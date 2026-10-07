// JNI wrapper: one judge_v1 chat completion on Qwen3.5-2B, applied the way llama-server b10456 applied the PoC request
// (experiments/android-qwen-poc/poc_judge.py → POST /v1/chat/completions with
//   temperature 0 · seed 7 · max_tokens 192 · response_format json_schema · chat_template_kwargs.enable_thinking=false ·
//   cache_prompt; server flags -c 2048 -n 192 --temp 0 --seed 7 -np 1 --jinja).
// Every step uses the same llama.cpp `common` functions the server uses (tools/server/server-common.cpp
// oaicompat_chat_params_parse, server-schema.cpp, server-context.cpp process_token, server-task.cpp final parse):
//   common_chat_templates_apply (GGUF jinja template, json_schema → grammar) → common_sampler (greedy + grammar)
//   → stop strings / n_predict / EOS → common_chat_parse → message content.
// llama.cpp itself is not modified. Kotlin: app.oneulmundeuk.related.qwen.LlamaCppQwenEngine.

#include <jni.h>
#include <android/log.h>
#include <sched.h>

#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#include "chat.h"
#include "common.h"
#include "ggml-cpu.h"
#include "llama.h"
#include "sampling.h"

namespace {

constexpr const char * TAG = "QwenJudge";

void log_callback(ggml_log_level level, const char * text, void * /*user*/) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
    }
}

std::once_flag g_backend_once;

struct sampler_deleter {
    void operator()(common_sampler * s) const { if (s) common_sampler_free(s); }
};

// model + context + chat templates of one engine instance; freed together (also when a call failed).
struct session {
    common_params params;  // server defaults + the PoC flags
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    // persistent CPU threadpool attached to ctx, as llama-server does (common_threadpools::init, common/common.cpp).
    // Without it ggml creates and frees a threadpool for every graph compute (ggml-cpu.c ggml_graph_compute).
    ggml_threadpool * threadpool = nullptr;
    common_chat_templates_ptr tmpls;
    std::vector<llama_token> cached;  // tokens in the KV cache (prompt + generated) — llama-server cache_prompt
    std::mutex lock;

    ~session() {
        tmpls.reset();
        if (ctx) llama_free(ctx);                         // the context stops using the threadpool first
        if (threadpool) ggml_threadpool_free(threadpool);
        if (model) llama_model_free(model);
    }
};

// CPUs this process may run on (cpuset / affinity). An app process is often allowed fewer cores than the device has
// (e.g. 0-5 of 8 for an instrumentation / non-top-app process); 0 = unknown.
int allowed_cpu_count() {
    cpu_set_t set;
    CPU_ZERO(&set);
    if (sched_getaffinity(0, sizeof(set), &set) != 0) return 0;
    return CPU_COUNT(&set);
}

std::string bytes_to_string(JNIEnv * env, jbyteArray bytes) {
    if (bytes == nullptr) throw std::invalid_argument("null input");
    const jsize n = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(bytes, 0, n, reinterpret_cast<jbyte *>(out.data()));
    return out;
}

jbyteArray string_to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(s.size()));
    if (out == nullptr) throw std::bad_alloc();
    if (!s.empty()) env->SetByteArrayRegion(out, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return out;
}

void throw_java(JNIEnv * env, const std::string & message) {
    if (env->ExceptionCheck()) return;  // keep the pending Java exception
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
}

void decode_tokens(llama_context * ctx, std::vector<llama_token> & tokens, size_t from, int32_t n_batch) {
    for (size_t i = from; i < tokens.size(); i += static_cast<size_t>(n_batch)) {
        const int32_t n = static_cast<int32_t>(std::min(tokens.size() - i, static_cast<size_t>(n_batch)));
        if (llama_decode(ctx, llama_batch_get_one(tokens.data() + i, n)) != 0) {
            throw std::runtime_error("llama_decode failed (prompt)");
        }
    }
}

// server-common.cpp: grammar triggers / preserved tokens → sampling params (server-schema.cpp handlers).
void apply_chat_params(const llama_vocab * vocab, const common_chat_params & cp, const common_params & base,
                       common_params_sampling & sp) {
    if (!cp.grammar.empty()) {
        sp.grammar = {COMMON_GRAMMAR_TYPE_TOOL_CALLS, cp.grammar};  // server: grammar_type "tool_calls" for template grammars
    }
    sp.grammar_lazy = cp.grammar_lazy;
    for (const auto & t : cp.preserved_tokens) {
        auto ids = common_tokenize(vocab, t, false, true);
        if (ids.size() == 1) sp.preserved_tokens.insert(ids[0]);
    }
    for (const auto & trigger : cp.grammar_triggers) {
        if (trigger.type == COMMON_GRAMMAR_TRIGGER_TYPE_WORD) {
            auto ids = common_tokenize(vocab, trigger.value, false, true);
            if (ids.size() == 1) {
                if (sp.preserved_tokens.find(ids[0]) == sp.preserved_tokens.end()) {
                    throw std::runtime_error("grammar trigger word should be a preserved token: " + trigger.value);
                }
                common_grammar_trigger t;
                t.type = COMMON_GRAMMAR_TRIGGER_TYPE_TOKEN;
                t.value = trigger.value;
                t.token = ids[0];
                sp.grammar_triggers.push_back(std::move(t));
            } else {
                sp.grammar_triggers.push_back({COMMON_GRAMMAR_TRIGGER_TYPE_WORD, trigger.value});
            }
        } else {
            sp.grammar_triggers.push_back(trigger);
        }
    }
    if (sp.grammar_lazy && sp.grammar_triggers.empty()) throw std::runtime_error("no triggers set for lazy grammar");
    sp.generation_prompt = cp.generation_prompt;
    // reasoning budget fields are passed only when the template has thinking end tags (server-common.cpp)
    if (!cp.thinking_end_tags.empty()) {
        sp.reasoning_budget_tokens = base.sampling.reasoning_budget_tokens;
        sp.reasoning_budget_start = common_tokenize(vocab, cp.thinking_start_tag, false, true);
        sp.reasoning_budget_end.clear();
        for (const auto & tag : cp.thinking_end_tags) {
            if (!tag.empty()) sp.reasoning_budget_end.push_back(common_tokenize(vocab, tag, false, true));
        }
        if (!sp.reasoning_budget_end.empty()) {
            llama_tokens forced = sp.reasoning_budget_end.front();
            const std::string & message = base.sampling.reasoning_budget_message;
            if (!message.empty()) {
                llama_tokens m = common_tokenize(vocab, message, false, true);
                forced.insert(forced.begin(), m.begin(), m.end());
            }
            sp.reasoning_budget_forced = std::move(forced);
        }
    }
}

// server-context.cpp find_stopping_strings (full stop): earliest stop word near the end of the text.
size_t find_stop(const std::string & text, size_t last_piece, const std::vector<std::string> & stops) {
    size_t stop_pos = std::string::npos;
    for (const auto & word : stops) {
        const size_t tmp = word.size() + last_piece;
        const size_t from = text.size() > tmp ? text.size() - tmp : 0;
        const size_t pos = text.find(word, from);
        if (pos != std::string::npos && (stop_pos == std::string::npos || pos < stop_pos)) stop_pos = pos;
    }
    return stop_pos;
}

bool utf8_complete(const std::string & s) {
    // trailing incomplete multi-byte sequence? (server: validate_utf8)
    size_t i = s.size();
    int back = 0;
    while (i > 0 && back < 4) {
        const unsigned char c = static_cast<unsigned char>(s[--i]);
        ++back;
        if ((c & 0xC0) != 0x80) {
            int need = (c & 0x80) == 0 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 1;
            return back >= need;
        }
    }
    return true;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_oneulmundeuk_related_qwen_QwenNative_nativeLoad(JNIEnv * env, jclass, jbyteArray model_path, jint n_ctx,
                                                          jint n_predict, jint seed) {
    try {
        std::call_once(g_backend_once, [] {
            llama_log_set(log_callback, nullptr);
            llama_backend_init();
        });
        auto s = std::make_unique<session>();
        common_params & p = s->params;
        p.model.path = bytes_to_string(env, model_path);
        p.n_ctx = n_ctx;              // -c 2048
        p.n_predict = n_predict;      // -n 192 (request max_tokens 192)
        p.sampling.temp = 0.0f;       // --temp 0 (greedy)
        p.sampling.seed = static_cast<uint32_t>(seed);  // --seed 7
        p.use_jinja = true;           // --jinja
        // threads: the server default (common_cpu_get_num_math = physical cores), but never more than the CPUs this
        // process is allowed — ggml workers busy-wait at every op barrier, so threads > allowed cores stall each other.
        int n_threads = common_cpu_get_num_math();
        const int allowed = allowed_cpu_count();
        if (allowed > 0 && allowed < n_threads) n_threads = allowed;
        p.cpuparams.n_threads = n_threads;
        p.cpuparams_batch.n_threads = n_threads;
        postprocess_cpu_params(p.cpuparams, nullptr);
        postprocess_cpu_params(p.cpuparams_batch, &p.cpuparams);
        __android_log_print(ANDROID_LOG_INFO, TAG, "threads %d (math cores %d, allowed cpus %d)",
                            p.cpuparams.n_threads, common_cpu_get_num_math(), allowed);

        s->model = llama_model_load_from_file(p.model.path.c_str(), common_model_params_to_llama(p));
        if (s->model == nullptr) throw std::runtime_error("llama_model_load_from_file failed");
        s->ctx = llama_init_from_model(s->model, common_context_params_to_llama(p));
        if (s->ctx == nullptr) throw std::runtime_error("llama_init_from_model failed");

        // same as common_threadpools::init: one pool from the cpu params (batch params are identical → no batch pool)
        ggml_threadpool_params tpp = ggml_threadpool_params_from_cpu_params(p.cpuparams);
        ggml_threadpool_params tpp_batch = ggml_threadpool_params_from_cpu_params(p.cpuparams_batch);
        if (!ggml_threadpool_params_match(&tpp, &tpp_batch)) throw std::runtime_error("threadpool params differ");
        s->threadpool = ggml_threadpool_new(&tpp);
        if (s->threadpool == nullptr) throw std::runtime_error("ggml_threadpool_new failed");
        llama_attach_threadpool(s->ctx, s->threadpool, nullptr);
        s->tmpls = common_chat_templates_init(s->model, p.chat_template);  // GGUF built-in template (no override)
        return reinterpret_cast<jlong>(s.release());
    } catch (const std::exception & e) {
        throw_java(env, std::string("qwen load: ") + e.what());
    } catch (...) {
        throw_java(env, "qwen load: unknown native error");
    }
    return 0;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_oneulmundeuk_related_qwen_QwenNative_nativeComplete(JNIEnv * env, jclass, jlong handle, jbyteArray system_bytes,
                                                              jbyteArray user_bytes, jbyteArray schema_bytes) {
    auto * s = reinterpret_cast<session *>(handle);
    if (s == nullptr) {
        throw_java(env, "qwen: closed");
        return nullptr;
    }
    try {
        std::lock_guard<std::mutex> guard(s->lock);
        const llama_vocab * vocab = llama_model_get_vocab(s->model);
        const common_params & base = s->params;

        // ── oaicompat_chat_params_parse ──
        common_chat_msg system;
        system.role = "system";
        system.content = bytes_to_string(env, system_bytes);
        common_chat_msg user;
        user.role = "user";
        user.content = bytes_to_string(env, user_bytes);

        common_chat_templates_inputs inputs;
        inputs.messages = {system, user};
        inputs.json_schema = bytes_to_string(env, schema_bytes);
        inputs.use_jinja = base.use_jinja;
        inputs.parallel_tool_calls = common_chat_templates_get_caps(s->tmpls.get())["supports_parallel_tool_calls"];
        inputs.add_generation_prompt = true;
        inputs.reasoning_format = base.reasoning_format;
        inputs.chat_template_kwargs = base.default_template_kwargs;
        inputs.chat_template_kwargs["enable_thinking"] = "false";  // request chat_template_kwargs (JSON dump of false)
        inputs.enable_thinking = false;                            // parsed from that kwarg
        inputs.force_pure_content = base.force_pure_content_parser;
        const common_chat_params cp = common_chat_templates_apply(s->tmpls.get(), inputs);

        // ── task params (server-schema.cpp) ──
        common_params_sampling sp = base.sampling;
        apply_chat_params(vocab, cp, base, sp);

        common_chat_parser_params parser(cp);
        parser.reasoning_format = base.reasoning_format;
        parser.reasoning_in_content = false;  // not streaming
        if (!cp.parser.empty()) parser.parser.load(cp.parser);

        // ── prompt + prefix cache (cache_prompt) ──
        std::vector<llama_token> prompt = common_tokenize(vocab, cp.prompt, true, true);
        if (prompt.empty()) throw std::runtime_error("empty prompt");
        const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(s->ctx));
        if (static_cast<int32_t>(prompt.size()) >= n_ctx) {
            // llama-server rejects a prompt that does not fit: this pair cannot be judged (not a runtime error)
            return [&] {
                jobjectArray out = env->NewObjectArray(2, env->FindClass("[B"), nullptr);
                env->SetObjectArrayElement(out, 0, string_to_bytes(env, ""));
                env->SetObjectArrayElement(out, 1, string_to_bytes(env, "context"));
                return out;
            }();
        }
        size_t n_past = 0;
        while (n_past < s->cached.size() && n_past < prompt.size() && s->cached[n_past] == prompt[n_past]) ++n_past;
        if (n_past == prompt.size()) --n_past;  // always evaluate at least the last prompt token (fresh logits)
        if (!llama_memory_seq_rm(llama_get_memory(s->ctx), 0, static_cast<llama_pos>(n_past), -1)) {
            llama_memory_clear(llama_get_memory(s->ctx), true);
            n_past = 0;
        }
        s->cached.assign(prompt.begin(), prompt.begin() + static_cast<std::ptrdiff_t>(n_past));
        decode_tokens(s->ctx, prompt, n_past, base.n_batch);
        s->cached = prompt;

        // ── generation (server-context.cpp process_token) ──
        std::unique_ptr<common_sampler, sampler_deleter> smpl(common_sampler_init(s->model, sp));
        if (!smpl) throw std::runtime_error("common_sampler_init failed");
        std::string generated;
        std::string finish = "stop";
        int32_t n_gen = 0;
        while (true) {
            const llama_token id = common_sampler_sample(smpl.get(), s->ctx, -1);
            common_sampler_accept(smpl.get(), id, true);
            ++n_gen;
            const bool special = base.special || sp.preserved_tokens.find(id) != sp.preserved_tokens.end();
            const std::string piece = common_token_to_piece(s->ctx, id, special);
            generated += piece;
            bool has_next = true;
            if (utf8_complete(generated)) {
                const size_t stop_pos = find_stop(generated, piece.size(), cp.additional_stops);
                if (stop_pos != std::string::npos) {
                    generated.erase(stop_pos);
                    has_next = false;
                }
            }
            if (has_next && static_cast<int32_t>(s->cached.size()) + 1 >= n_ctx) {
                finish = "length";  // out of context (server: truncated, STOP_TYPE_LIMIT)
                has_next = false;
            }
            if (has_next && base.n_predict >= 0 && n_gen >= base.n_predict) {
                finish = "length";  // n_predict reached without an end
                has_next = false;
            }
            if (llama_vocab_is_eog(vocab, id)) {
                finish = "stop";    // EOS wins (server: checked last)
                has_next = false;
            }
            if (!has_next) break;
            llama_token next = id;
            if (llama_decode(s->ctx, llama_batch_get_one(&next, 1)) != 0) throw std::runtime_error("llama_decode failed (generation)");
            s->cached.push_back(id);
        }

        // ── final message (server-task.cpp: common_chat_parse, not partial) ──
        const common_chat_msg msg = common_chat_parse(generated, false, parser);

        jobjectArray out = env->NewObjectArray(2, env->FindClass("[B"), nullptr);
        if (out == nullptr) throw std::bad_alloc();
        env->SetObjectArrayElement(out, 0, string_to_bytes(env, msg.content));
        env->SetObjectArrayElement(out, 1, string_to_bytes(env, finish));
        return out;
    } catch (const std::exception & e) {
        s->cached.clear();  // KV state unknown after a failure: next call re-evaluates the whole prompt
        llama_memory_clear(llama_get_memory(s->ctx), true);
        throw_java(env, std::string("qwen complete: ") + e.what());
    } catch (...) {
        s->cached.clear();
        llama_memory_clear(llama_get_memory(s->ctx), true);
        throw_java(env, "qwen complete: unknown native error");
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_app_oneulmundeuk_related_qwen_QwenNative_nativeFree(JNIEnv *, jclass, jlong handle) {
    delete reinterpret_cast<session *>(handle);
}
