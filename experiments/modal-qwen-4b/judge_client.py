#!/usr/bin/env python3
"""
M5-4a 측정 클라이언트 (stdlib만). Modal에 띄운 vLLM OpenAI 호환 서버로 judge_v1 판정을 보내고 시간을 잰다.
modal_judge.py의 local entrypoint가 사용한다. 서버 쪽 코드와 분리해 fake server로 테스트한다.

고정 조건 (Mac/Android judge_v1과 같게): judge_v1.txt (sha 553ab6176c0293f9) system/user 그대로 · temperature 0 · seed 7 ·
max_tokens 192 · JSON schema {reason, label 0/1/2} · enable_thinking=false · 출력 검증 규칙 동일 (strong_judge.validate).
"""
import concurrent.futures as cf
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
RELATED = HERE.parent / "related"
sys.path.insert(0, str(RELATED))
import llm_judge as J  # noqa: E402  (읽기만: prompt · 메시지 · schema · 판정 순서)
import strong_judge as SJ  # noqa: E402  (judge_v1과 같은 출력 검증 규칙)

EXPECTED_PROMPT_SHA = "553ab6176c0293f9"
SERVED_NAME = "judge"
SEED, MAX_TOKENS = 7, 192


def load_prompt():
    system, user, sha = J.load_prompt(RELATED / "prompts" / "judge_v1.txt")
    if sha != EXPECTED_PROMPT_SHA:
        raise SystemExit(f"judge_v1 sha {sha} ≠ {EXPECTED_PROMPT_SHA}")
    return system, user, sha


def load_plan():
    ds = json.loads((RELATED / "dataset.json").read_text(encoding="utf-8"))
    e5 = json.loads((RELATED / "runs" / "e5-small-ko.v1.1.json").read_text(encoding="utf-8"))
    return ds, J.plan(ds, e5)  # Mac/Android judge_v1과 같은 순서 (query 순 · e5 순위 순)


def request_body(system, user_tpl, current, past):
    return {
        "model": SERVED_NAME,
        "messages": J.messages(system, user_tpl, current, past),
        "temperature": 0,
        "seed": SEED,
        "max_tokens": MAX_TOKENS,
        "response_format": {"type": "json_schema", "json_schema": {"name": "judgment", "schema": J.SCHEMA}},
        "chat_template_kwargs": {"enable_thinking": False},
        "stream": True,
        "stream_options": {"include_usage": True},
    }


def judge(base, system, user_tpl, current, past, timeout=300):
    """streaming으로 보내서 TTFT(≈ 대기 + prompt 처리 + 네트워크)와 생성 시간을 나눠 잰다."""
    body = json.dumps(request_body(system, user_tpl, current, past), ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{base}/v1/chat/completions", data=body, headers={"Content-Type": "application/json"})
    t0 = time.time()
    ttft = None
    content, reasoning, finish, usage = [], [], None, {}
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            for raw in r:
                line = raw.decode("utf-8").strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                ev = json.loads(data)
                usage = ev.get("usage") or usage
                for ch in ev.get("choices") or []:
                    d = ch.get("delta") or {}
                    piece = d.get("content")
                    think = d.get("reasoning_content") or d.get("reasoning")
                    if (piece or think) and ttft is None:
                        ttft = time.time() - t0
                    if piece:
                        content.append(piece)
                    if think:
                        reasoning.append(think)
                    finish = ch.get("finish_reason") or finish
    except urllib.error.HTTPError as e:
        return {"status": "error", "label": None, "reason": None, "raw": None, "wall_ms": int((time.time() - t0) * 1000),
                "error": f"http {e.code}: {e.read().decode('utf-8', 'replace')[:300]}"}
    except Exception as e:
        return {"status": "error", "label": None, "reason": None, "raw": None, "wall_ms": int((time.time() - t0) * 1000),
                "error": f"{type(e).__name__}: {e}"}
    wall = time.time() - t0
    rec = SJ.validate("".join(content), finish == "length")
    rec.update(wall_ms=int(wall * 1000), ttft_ms=None if ttft is None else int(ttft * 1000),
               gen_ms=None if ttft is None else int((wall - ttft) * 1000), finish_reason=finish,
               thinking_present=bool(reasoning), prompt_tokens=usage.get("prompt_tokens"),
               completion_tokens=usage.get("completion_tokens"),
               cached_tokens=(usage.get("prompt_tokens_details") or {}).get("cached_tokens"))
    return rec


def wait_ready(base, timeout_s, poll=2.0, clock=time.time, sleep=time.sleep):
    """/health 200까지 걸린 초. timeout이면 None."""
    t0 = clock()
    while clock() - t0 < timeout_s:
        try:
            with urllib.request.urlopen(f"{base}/health", timeout=30) as r:
                if r.status == 200:
                    return clock() - t0
        except Exception:
            pass
        sleep(poll)
    return None


def burst(base, system, user_tpl, pairs, workers):
    """pairs를 동시에 보낸다 (저장 1회 = Top30 판정을 병렬로 하는 경우의 wall time)."""
    t0 = time.time()
    with cf.ThreadPoolExecutor(max_workers=workers) as ex:
        recs = list(ex.map(lambda p: judge(base, system, user_tpl, p[1], p[2]), pairs))
    return time.time() - t0, recs


def metrics_snapshot(base):
    """vLLM /metrics의 서버 쪽 누적 시간 (없으면 {})."""
    want = ("vllm:e2e_request_latency_seconds_sum", "vllm:e2e_request_latency_seconds_count",
            "vllm:request_prefill_time_seconds_sum", "vllm:request_decode_time_seconds_sum",
            "vllm:request_queue_time_seconds_sum", "vllm:time_to_first_token_seconds_sum")
    try:
        with urllib.request.urlopen(f"{base}/metrics", timeout=30) as r:
            text = r.read().decode("utf-8")
    except Exception:
        return {}
    out = {}
    for line in text.splitlines():
        if line.startswith("#"):
            continue
        name = line.split("{")[0].split(" ")[0]
        if name in want:
            try:
                out[name] = out.get(name, 0.0) + float(line.rsplit(" ", 1)[1])
            except ValueError:
                pass
    return out


def diff(after, before):
    return {k: round(v - before.get(k, 0.0), 4) for k, v in after.items()}
