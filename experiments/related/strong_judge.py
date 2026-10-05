#!/usr/bin/env python3
"""
오늘문득 M5-2d: 강한 API 모델로 judge_v1을 그대로 pointwise 판정한다 (upper-bound 실험). stdlib만 사용.

  python3 strong_judge.py --dry-run          # 계획 + 157개 payload 검증 + 토큰/비용 추정 (네트워크 없음)
  python3 strong_judge.py --print-request    # 첫 판정의 실제 요청 payload 출력 (네트워크 없음)
  python3 strong_judge.py --max-calls 3      # 3건만 실제 호출 (OPENAI_API_KEY 필요, 이어서 실행 가능)
  python3 strong_judge.py                    # 남은 판정 전부

독립변수는 모델 하나다. prompt 로딩 · 메시지 구성 · schema · 판정 순서 · 저장 형식은 llm_judge.py에서 import해서
그대로 쓴다 (llm_judge.py는 수정하지 않음). 출력 검증 규칙도 llm_judge.judge_once와 같다 (test_strong_judge.py가 비교).

고정 조건: prompts/judge_v1.txt (sha 553ab6176c0293f9) · dataset v1.1 157쌍 · e5-small-ko.v1.1 순서 · 1회(attempt 1)
          · temperature 0 · reasoning.effort none · 출력 최대 192 토큰 · strict JSON schema {reason, label 0/1/2}.
API로는 맞출 수 없는 것: tokenizer, seed (Responses API에 없음 → 보내지 않음), num_ctx (해당 없음).

저장: runs/llm-openai-<model>-judge_v1.jsonl (llm_judge.py와 같은 meta/judgment 형식 → llm_report.py, compare_judges.py로 읽음)
  - API 실패 / 잘린 응답 / 거부 / JSON 형식 오류는 label 0으로 바꾸지 않고 status=error로 남긴다. 다시 실행하면 그것만 다시 시도.
  - 429/5xx는 판정이 아니라 전송 실패라서 같은 호출 안에서 최대 --http-retries번 다시 보낸다 (http_retries로 기록).
API 키는 환경변수 OPENAI_API_KEY에서만 읽고, 화면이나 파일에 남기지 않는다.
"""
import argparse
import datetime as dt
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

import llm_judge as J

HERE = Path(__file__).resolve().parent
PROVIDER = "openai"
DEFAULT_MODEL = "gpt-5.6-sol"
DEFAULT_BASE_URL = "https://api.openai.com/v1"
EXPECTED_PROMPT_SHA = "553ab6176c0293f9"   # judge_v1
ATTEMPT = 1
TEMPERATURE = 0
REASONING_EFFORT = "none"
MAX_OUTPUT_TOKENS = 192                    # judge_v1 num_predict
PRICE_PER_MTOK = {"gpt-5.6-sol": {"input": 4.0, "output": 20.0}}  # USD, OpenAI 공식 가격 (사용자 확인, 2026-10)
QWEN_LOG = HERE / "runs" / "llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl"  # 토큰 추정용 (읽기만)
TRUNCATED = "truncated (num_predict 한도 도달)"  # llm_judge와 같은 문구 (리포트에서 같은 유형으로 묶이게)


# ── request ──────────────────────────────────────────────────────────────────
def strict_schema():
    """llm_judge.SCHEMA 그대로 + strict 모드가 요구하는 additionalProperties: false. key 순서(reason → label) 유지."""
    s = json.loads(json.dumps(J.SCHEMA))
    s["additionalProperties"] = False
    return s


def request_body(model, system, user_tpl, current, past):
    return {
        "model": model,
        "input": J.messages(system, user_tpl, current, past),  # judge_v1과 같은 system/user 문자열
        "reasoning": {"effort": REASONING_EFFORT},
        "temperature": TEMPERATURE,
        "max_output_tokens": MAX_OUTPUT_TOKENS,
        "text": {"format": {"type": "json_schema", "name": "judgment", "schema": strict_schema(), "strict": True}},
        "store": False,
    }


def verify_payload(body, model, system, user_tpl, q, c):
    """요청이 고정 조건을 지키는지. 문제 목록을 돌려준다 (빈 목록 = 통과)."""
    p = []
    if body.get("model") != model:
        p.append("model")
    if body.get("temperature") != 0:
        p.append("temperature")
    if body.get("reasoning") != {"effort": "none"}:
        p.append("reasoning")
    if body.get("max_output_tokens") != 192:
        p.append("max_output_tokens")
    if "seed" in body:
        p.append("seed (Responses API에 없음)")
    fmt = body.get("text", {}).get("format", {})
    sch = fmt.get("schema", {})
    if not (fmt.get("type") == "json_schema" and fmt.get("strict") is True and sch.get("additionalProperties") is False
            and list(sch.get("properties", {})) == ["reason", "label"] and sch["properties"]["label"].get("enum") == [0, 1, 2]
            and sch.get("required") == ["reason", "label"]):
        p.append("schema")
    if body.get("input") != J.messages(system, user_tpl, q["text"], c["text"]):
        p.append("messages ≠ judge_v1")
    user = body["input"][1]["content"]
    if q["text"] not in user or c["text"] not in user:
        p.append("text 누락")
    leaks = [str(c.get(k)) for k in ("id", "case", "note", "date") if c.get(k)] + [str(q.get(k)) for k in ("id", "date") if q.get(k)]
    for s in leaks:
        if s and s in user:
            p.append(f"메타 정보 누출: {s[:20]}")
    return p


# ── call ─────────────────────────────────────────────────────────────────────
def http_post(url, body, key, timeout):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def validate(content, truncated):
    """llm_judge.judge_once의 출력 검증과 같은 규칙. → status/label/reason/error/raw."""
    rec = {"status": "error", "label": None, "reason": None, "error": None, "raw": content[:600]}
    if truncated:
        rec["error"] = TRUNCATED
        return rec
    try:
        obj = json.loads(content)
    except json.JSONDecodeError as e:
        rec["error"] = f"malformed JSON: {e}"
        return rec
    label, reason = obj.get("label") if isinstance(obj, dict) else None, obj.get("reason") if isinstance(obj, dict) else None
    if not (isinstance(label, int) and not isinstance(label, bool) and label in (0, 1, 2)):
        rec["error"] = f"invalid label: {label!r}"
        return rec
    if not (isinstance(reason, str) and reason.strip()):
        rec["error"] = "empty reason"
        return rec
    rec.update(status="ok", label=label, reason=reason.strip())
    return rec


def parse_response(resp):
    """Responses API 응답 → (content, truncated, other_error, extra)."""
    texts, refusal = [], None
    for item in resp.get("output") or []:
        if item.get("type") != "message":
            continue
        for part in item.get("content") or []:
            if part.get("type") == "output_text":
                texts.append(part.get("text") or "")
            elif part.get("type") == "refusal":
                refusal = part.get("refusal") or "refusal"
    status = resp.get("status")
    why = (resp.get("incomplete_details") or {}).get("reason")
    usage = resp.get("usage") or {}
    extra = {
        "done_reason": "stop" if status == "completed" else f"{status}:{why}" if why else status,
        "prompt_eval_count": usage.get("input_tokens"),   # 기존 리포트와 같은 이름 (= API input tokens)
        "eval_count": usage.get("output_tokens"),         # (= API output tokens, reasoning 포함)
        "reasoning_tokens": (usage.get("output_tokens_details") or {}).get("reasoning_tokens"),
        "cached_tokens": (usage.get("input_tokens_details") or {}).get("cached_tokens"),
        "response_model": resp.get("model"),
        "response_id": resp.get("id"),
    }
    truncated = status == "incomplete" and why == "max_output_tokens"
    other = None
    if refusal is not None:
        other = f"refusal: {refusal[:200]}"
    elif status != "completed" and not truncated:
        other = f"incomplete: {why or status}"
    return "".join(texts), truncated, other, extra


def cost_usd(model, inp, out):
    pr = PRICE_PER_MTOK[model]
    return (inp or 0) * pr["input"] / 1e6 + (out or 0) * pr["output"] / 1e6


def judge_once(a, key, system, user_tpl, current, past):
    body = request_body(a.model, system, user_tpl, current, past)
    t0 = time.time()
    retries = 0
    while True:
        try:
            resp = http_post(f"{a.base_url}/responses", body, key, a.timeout)
            break
        except urllib.error.HTTPError as e:
            msg = e.read().decode("utf-8", "replace")[:300]
            if e.code in (429, 500, 502, 503, 504) and retries < a.http_retries:
                retries += 1
                ra = e.headers.get("retry-after") if e.headers else None
                time.sleep(float(ra) if ra and ra.replace(".", "", 1).isdigit() else a.backoff * 2 ** (retries - 1))
                continue
            return {"status": "error", "label": None, "reason": None, "error": f"http {e.code}: {msg}", "raw": None,
                    "http_code": e.code, "http_retries": retries, "elapsed_ms": int((time.time() - t0) * 1000)}
        except Exception as e:  # URLError, timeout …
            return {"status": "error", "label": None, "reason": None, "error": f"{type(e).__name__}: {e}", "raw": None,
                    "http_retries": retries, "elapsed_ms": int((time.time() - t0) * 1000)}
    content, truncated, other, extra = parse_response(resp)
    rec = validate(content, truncated)
    if other:  # 거부 / max_output_tokens 외의 incomplete → 판정 실패
        rec.update(status="error", label=None, reason=None, error=other)
    rec.update(extra, http_retries=retries, elapsed_ms=int((time.time() - t0) * 1000),
               cost_usd=round(cost_usd(a.model, extra["prompt_eval_count"], extra["eval_count"]), 6))
    return rec


# ── estimate ─────────────────────────────────────────────────────────────────
def estimate(pairs, model, qwen_log=QWEN_LOG):
    """Qwen judge_v1 실측 토큰을 proxy로 쓴다 (tokenizer가 달라 ±50% 범위로 본다)."""
    _, latest = J.read_log(qwen_log) if Path(qwen_log).exists() else (None, {})
    inp = out = n = 0
    for _, c, _ in pairs:
        r = latest.get((c["id"], 1))
        if r and r.get("prompt_eval_count") and r.get("eval_count"):
            inp += r["prompt_eval_count"]
            out += r["eval_count"]
            n += 1
    lines = [f"토큰 추정 (Qwen judge_v1 실측 {n}/{len(pairs)}쌍 기준): input {inp:,} · output {out:,}"]
    for f in (0.7, 1.0, 1.5):
        lines.append(f"  ×{f}: input {inp * f:,.0f} · output {out * f:,.0f} → ${cost_usd(model, inp * f, out * f):.3f}")
    worst = cost_usd(model, inp * 1.5, MAX_OUTPUT_TOKENS * len(pairs))
    lines.append(f"  상한 (input ×1.5, 모든 호출이 출력 {MAX_OUTPUT_TOKENS} 토큰을 다 씀): ${worst:.3f}")
    pr = PRICE_PER_MTOK[model]
    lines.append(f"  가격: input ${pr['input']}/1M · output ${pr['output']}/1M · 비용 = Σinput×{pr['input']}/1e6 + Σoutput×{pr['output']}/1e6")
    return {"input": inp, "output": out, "n": n, "worst": worst}, lines


# ── main ─────────────────────────────────────────────────────────────────────
def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=DEFAULT_MODEL, choices=sorted(PRICE_PER_MTOK))
    ap.add_argument("--base-url", default=DEFAULT_BASE_URL)
    ap.add_argument("--prompt", default=HERE / "prompts" / "judge_v1.txt")
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--qwen-log", default=QWEN_LOG, help="토큰 추정용 (읽기만)")
    ap.add_argument("--out", help="기본: runs/llm-openai-<model>-judge_v1.jsonl")
    ap.add_argument("--max-calls", type=int, default=0, help="이번 실행에서 최대 호출 수 (0 = 남은 것 전부)")
    ap.add_argument("--max-cost-usd", type=float, default=2.0, help="파일에 기록된 실제 비용 합계가 이 값을 넘으면 중단")
    ap.add_argument("--timeout", type=int, default=60)
    ap.add_argument("--http-retries", type=int, default=3)
    ap.add_argument("--backoff", type=float, default=2.0)
    ap.add_argument("--max-consecutive-errors", type=int, default=5)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--print-request", action="store_true")
    a = ap.parse_args(argv)
    a.base_url = a.base_url.rstrip("/")

    system, user_tpl, prompt_sha = J.load_prompt(a.prompt)
    if prompt_sha != EXPECTED_PROMPT_SHA:
        sys.exit(f"prompt sha {prompt_sha} ≠ judge_v1 {EXPECTED_PROMPT_SHA}. 이 실험은 judge_v1만 쓴다.")
    ds = json.loads(Path(a.dataset).read_text(encoding="utf-8"))
    e5 = json.loads(Path(a.e5).read_text(encoding="utf-8"))
    pairs = J.plan(ds, e5)

    problems = {c["id"]: pr for q, c, _ in pairs if (pr := verify_payload(request_body(a.model, system, user_tpl, q["text"], c["text"]),
                                                                     a.model, system, user_tpl, q, c))}
    if problems:
        sys.exit(f"payload 검증 실패: {problems}")

    if a.print_request:
        q, c, rank = pairs[0]
        print(f"POST {a.base_url}/responses   ({q['id']} × {c['id']}, e5 {rank}위)")
        print("Authorization: Bearer $OPENAI_API_KEY (값은 출력하지 않음)")
        print(json.dumps(request_body(a.model, system, user_tpl, q["text"], c["text"]), ensure_ascii=False, indent=1))
        print(f"\nprompt sha {prompt_sha} (= judge_v1) · payload 검증 {len(pairs)}/{len(pairs)} 통과")
        return 0

    meta = {
        "type": "meta",
        "provider": PROVIDER,
        "model": a.model,
        "endpoint": f"{a.base_url}/responses",
        "options": {"temperature": TEMPERATURE, "reasoning_effort": REASONING_EFFORT, "max_output_tokens": MAX_OUTPUT_TOKENS,
                    "format": "json-schema-strict", "store": False, "seed": None},
        "prompt_file": Path(a.prompt).name,
        "prompt_sha": prompt_sha,
        "dataset_version": ds.get("version"),
        "e5_run": Path(a.e5).name,
        "inputs": "query text + candidate text only",
        "price_usd_per_mtok": PRICE_PER_MTOK[a.model],
        "created": dt.datetime.now().isoformat(timespec="seconds"),
    }
    out = Path(a.out) if a.out else HERE / "runs" / f"llm-{PROVIDER}-{J.slug(a.model)}-{Path(a.prompt).stem}.jsonl"
    old_meta, latest = J.read_log(out)
    if old_meta:
        for k in ("provider", "model", "prompt_sha", "dataset_version", "e5_run", "options"):
            if old_meta.get(k) != meta[k]:
                sys.exit(f"{out}는 다른 설정({k}: {old_meta.get(k)} ≠ {meta[k]})으로 만든 기록. --out으로 다른 파일을 지정하세요.")

    todo = [(q, c, rank) for q, c, rank in pairs if not ((r := latest.get((c["id"], ATTEMPT))) and r["status"] == "ok")]
    spent = sum(r.get("cost_usd") or 0 for r in latest.values())
    n = min(len(todo), a.max_calls) if a.max_calls else len(todo)
    est, est_lines = estimate(pairs, a.model, a.qwen_log)
    print(f"provider {PROVIDER} · model {a.model} · prompt {meta['prompt_file']} ({prompt_sha} = judge_v1) · dataset {meta['dataset_version']} · e5 {meta['e5_run']}")
    print(f"options {json.dumps(meta['options'], ensure_ascii=False)}")
    print(f"payload 검증 {len(pairs)}/{len(pairs)} 통과 (temperature 0 · reasoning none · max 192 · strict schema · judge_v1 메시지 동일 · 메타 정보 없음)")
    print(f"pairs {len(pairs)} × 1회 · 완료 {len(pairs) - len(todo)} · 남음 {len(todo)} · 이번 실행 {n}회 · 지금까지 실제 비용 ${spent:.4f}")
    print("\n".join(est_lines))
    print(f"저장: {out}")
    if a.dry_run or n == 0:
        if n == 0 and not a.dry_run:
            print("남은 호출 없음")
        return 0

    key = os.environ.get("OPENAI_API_KEY")
    if not key:
        sys.exit("OPENAI_API_KEY 환경변수가 없음. 실제 호출은 키가 있을 때만 한다 (키를 인자나 파일로 넘기지 않는다).")
    if spent >= a.max_cost_usd:
        sys.exit(f"이미 ${spent:.4f} 사용 (한도 ${a.max_cost_usd}). --max-cost-usd를 확인하세요.")

    out.parent.mkdir(exist_ok=True)
    if not old_meta:
        J.append(out, meta)
    consecutive = 0
    for i, (q, c, rank) in enumerate(todo[:n], 1):
        rec = judge_once(a, key, system, user_tpl, q["text"], c["text"])
        J.append(out, {"type": "judgment", "qid": q["id"], "cid": c["id"], "attempt": ATTEMPT, "e5_rank": rank,
                       **rec, "ts": dt.datetime.now().isoformat(timespec="seconds")})
        spent += rec.get("cost_usd") or 0
        shown = f"label {rec['label']}" if rec["status"] == "ok" else f"ERROR {rec['error']}"
        print(f"{i:3d}/{n} {c['id']:6s} {rec.get('elapsed_ms', 0):5d}ms in {rec.get('prompt_eval_count')} out {rec.get('eval_count')} "
              f"${spent:.4f} {shown}", flush=True)
        if i == 1 and rec["status"] != "ok" and str(rec.get("http_code", "")).startswith("4") and rec.get("http_code") != 429:
            sys.exit(f"\n✗ 첫 호출이 {rec['http_code']}로 거부됨 — 키/모델/파라미터를 확인하세요. 다시 실행하면 이어서 진행합니다.")
        if i == 1 and rec["status"] == "ok" and rec.get("reasoning_tokens"):
            sys.exit(f"\n✗ reasoning_tokens {rec['reasoning_tokens']} > 0 — reasoning none이 적용되지 않음. 중단.")
        consecutive = consecutive + 1 if rec["status"] != "ok" else 0
        if consecutive >= a.max_consecutive_errors:
            sys.exit(f"\n✗ 연속 {consecutive}회 실패 — 중단. 다시 실행하면 이어서 진행합니다.")
        if spent >= a.max_cost_usd:
            sys.exit(f"\n✗ 비용 한도 도달 (${spent:.4f} ≥ ${a.max_cost_usd}) — 중단.")
    _, latest = J.read_log(out)
    ok = sum(r["status"] == "ok" for r in latest.values())
    print(f"\n완료. ok {ok}/{len(pairs)} · error {len(latest) - ok} · 실제 비용 ${spent:.4f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
