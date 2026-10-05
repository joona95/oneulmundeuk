#!/usr/bin/env python3
"""
오늘문득 M5-2a: local LLM(Ollama)으로 후보를 하나씩(pointwise) 판정한다. stdlib만 사용.

  python3 llm_judge.py --dry-run                 # 호출 계획만 출력 (모델 호출 없음)
  python3 llm_judge.py --smoke                   # 1건만 판정 + GPU/메모리 상태 출력 (runs/llm-smoke-<model>.jsonl)
  python3 llm_judge.py --max-calls 20            # 20건만 판정하고 멈춤 (이어서 실행 가능)
  python3 llm_judge.py                           # 남은 판정 전부 (중간에 멈춰도 다시 실행하면 이어서)

모델 입력: 현재 기록 text + 과거 기록 text + 일반 판단 기준(prompts/judge_v1.txt)뿐.
label / case / note / date / emotion / category / e5 순위 / cosine은 절대 넣지 않는다.
e5 run은 판정 "순서"를 정하는 데만 쓴다 (상위 후보부터 판정해 두면 중간에 멈춰도 쓸모 있음).

저장: runs/llm-<model>-<prompt>.jsonl (한 줄 = 호출 1회, append-only)
  - 첫 줄은 meta (model, options, prompt sha). 다른 설정으로 같은 파일에 이어 쓰려 하면 중단한다.
  - 같은 (candidate, attempt)의 마지막 기록이 유효. status=ok가 있으면 건너뛰고, error면 다시 시도한다.
  - API 실패 / 잘린 응답 / JSON 형식 오류는 label 0으로 바꾸지 않고 status=error로 남긴다.

8GB Mac 보호:
  - 첫 호출 직후와 --check-every 호출마다 Ollama /api/ps를 확인해 모델이 100% GPU가 아니면 중단 (--allow-cpu로 해제).
  - macOS에서는 첫 호출(모델 로드 포함) 직후를 기준으로, 이후 swap 사용량이 --max-swap-growth-mb 이상 늘면 중단 (매 호출 확인).
  - 연속 실패 --max-consecutive-errors 회면 중단 (서버 다운 등).
"""
import argparse
import datetime as dt
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_MODEL = "qwen3.5:4b-q4_K_M"
# reason을 label보다 먼저 쓰게 한다: 근거를 먼저 적고 판정하는 편이 분류가 안정적이다 (key 순서만의 차이).
SCHEMA = {
    "type": "object",
    "properties": {
        "reason": {"type": "string"},
        "label": {"type": "integer", "enum": [0, 1, 2]},
    },
    "required": ["reason", "label"],
}


# ── prompt ───────────────────────────────────────────────────────────────────
def load_prompt(path):
    raw = Path(path).read_text(encoding="utf-8")
    m = re.match(r"\s*### SYSTEM\n(.*?)\n### USER\n(.*)\Z", raw, re.S)
    if not m:
        sys.exit(f"{path}: '### SYSTEM' / '### USER' 구획을 찾을 수 없음")
    system, user = m.group(1).strip(), m.group(2).strip()
    assert "{current}" in user and "{past}" in user
    return system, user, hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]


def messages(system, user_tpl, current, past):
    user = user_tpl.replace("{current}", current).replace("{past}", past)
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


# ── ollama ───────────────────────────────────────────────────────────────────
def http_json(url, body=None, timeout=120):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def judge_once(a, system, user_tpl, current, past):
    """→ (record fields). 실패도 예외 대신 status=error로 돌려준다."""
    body = {
        "model": a.model,
        "messages": messages(system, user_tpl, current, past),
        "stream": False,
        "think": False,
        "format": SCHEMA,
        "keep_alive": a.keep_alive,
        "options": options(a),
    }
    t0 = time.time()
    rec = {"status": "error", "label": None, "reason": None, "error": None, "raw": None}
    try:
        resp = http_json(f"{a.host}/api/chat", body, timeout=a.timeout)
    except urllib.error.HTTPError as e:
        rec["error"] = f"http {e.code}: {e.read().decode('utf-8', 'replace')[:300]}"
        return rec | {"elapsed_ms": int((time.time() - t0) * 1000)}
    except Exception as e:  # URLError, timeout, ConnectionRefused …
        rec["error"] = f"{type(e).__name__}: {e}"
        return rec | {"elapsed_ms": int((time.time() - t0) * 1000)}
    msg = resp.get("message") or {}
    content = msg.get("content") or ""
    rec.update(
        raw=content[:600],
        elapsed_ms=int((time.time() - t0) * 1000),
        done_reason=resp.get("done_reason"),
        prompt_eval_count=resp.get("prompt_eval_count"),
        eval_count=resp.get("eval_count"),
        load_duration_ms=round((resp.get("load_duration") or 0) / 1e6),  # 1초 이상이면 이 호출에서 모델을 (다시) 로드함
        thinking_present=bool(msg.get("thinking")),
    )
    if resp.get("done_reason") == "length":
        rec["error"] = "truncated (num_predict 한도 도달)"
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


def options(a):
    return {"temperature": 0, "seed": a.seed, "num_ctx": a.num_ctx, "num_predict": a.num_predict}


def model_state(a):
    """Ollama /api/ps에서 이 모델의 메모리 배치. 없으면 None."""
    try:
        ps = http_json(f"{a.host}/api/ps", timeout=10)
    except Exception as e:
        return {"error": f"{type(e).__name__}: {e}"}
    loaded = ps.get("models", [])
    me = next((m for m in loaded if m.get("name") == a.model or m.get("model") == a.model), None)
    others = [m.get("name") for m in loaded if m is not me]
    if not me:
        return {"loaded": False, "others": others}
    size, vram = me.get("size", 0), me.get("size_vram", 0)
    return {
        "loaded": True,
        "size_gb": round(size / 1e9, 2),
        "size_vram_gb": round(vram / 1e9, 2),
        "gpu_pct": round(100 * vram / size) if size else None,
        "context_length": me.get("context_length"),
        "others": others,
    }


def swap_used_mb():
    if sys.platform != "darwin":
        return None
    try:
        out = subprocess.run(["sysctl", "-n", "vm.swapusage"], capture_output=True, text=True, timeout=5).stdout
        m = re.search(r"used = ([\d.]+)([MG])", out)
        if not m:
            return None
        v = float(m.group(1))
        return v * 1024 if m.group(2) == "G" else v
    except Exception:
        return None


def guard(a, swap0, where):
    """중단해야 하면 이유 문자열, 아니면 None."""
    st = model_state(a)
    sw = swap_used_mb()
    line = f"[{where}] ollama ps: {st}"
    if sw is not None and swap0 is not None:
        line += f" · swap used {sw:.0f}MB (기준 대비 {sw - swap0:+.0f}MB)"
    print(line, flush=True)
    if st.get("error"):
        return f"/api/ps 확인 실패: {st['error']}"
    if not st.get("loaded"):
        return "모델이 메모리에 없음 (/api/ps)"
    if st.get("others"):
        return f"다른 모델도 올라가 있음: {st['others']} — `ollama stop <model>` 후 다시 실행"
    if not a.allow_cpu and (st.get("gpu_pct") or 0) < 100:
        return f"100% GPU가 아님 (GPU {st.get('gpu_pct')}%, {st.get('size_vram_gb')}/{st.get('size_gb')}GB) — CPU로 넘친 상태"
    if st.get("context_length") and st["context_length"] > a.num_ctx:
        return f"context_length {st['context_length']} > {a.num_ctx}"
    if sw is not None and swap0 is not None and sw - swap0 > a.max_swap_growth_mb:
        return f"swap이 {sw - swap0:.0f}MB 늘어남 (한도 {a.max_swap_growth_mb}MB)"
    return None


# ── plan / storage ───────────────────────────────────────────────────────────
def plan(ds, e5):
    """(query, candidate, e5 rank) — query 순, 각 query 안에서는 e5 순위 순."""
    rk = {qid: [x["id"] for x in lst] for qid, lst in e5["rankings"].items()}
    out = []
    for q in ds["queries"]:
        by = {c["id"]: c for c in q["candidates"]}
        order = rk.get(q["id"])
        if order is None or set(order) != set(by):
            sys.exit(f"e5 run이 dataset과 맞지 않음 ({q['id']}). dataset {ds.get('version')}로 만든 run을 쓰세요.")
        out += [(q, by[cid], i + 1) for i, cid in enumerate(order)]
    return out


def read_log(path):
    meta, latest = None, {}
    if not path.exists():
        return meta, latest
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        r = json.loads(line)
        if r.get("type") == "meta":
            meta = meta or r
        elif r.get("type") == "judgment":
            latest[(r["cid"], r["attempt"])] = r
    return meta, latest


def append(path, rec):
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
        f.flush()
        os.fsync(f.fileno())


def slug(s):
    return re.sub(r"[^A-Za-z0-9._-]+", "-", s)


# ── main ─────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=DEFAULT_MODEL)
    ap.add_argument("--host", default=os.environ.get("OLLAMA_HOST_URL", "http://127.0.0.1:11434"))
    ap.add_argument("--prompt", default=HERE / "prompts" / "judge_v1.txt")
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--out", help="기본: runs/llm-<model>-<prompt>.jsonl (--smoke면 runs/llm-smoke-<model>.jsonl)")
    ap.add_argument("--attempts", type=int, default=2, help="같은 설정으로 반복할 횟수 (안정성 확인)")
    ap.add_argument("--max-calls", type=int, default=0, help="이번 실행에서 최대 호출 수 (0 = 남은 것 전부)")
    ap.add_argument("--num-ctx", type=int, default=2048)
    ap.add_argument("--num-predict", type=int, default=192)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--keep-alive", default="10m")
    ap.add_argument("--timeout", type=int, default=120)
    ap.add_argument("--check-every", type=int, default=20)
    ap.add_argument("--max-swap-growth-mb", type=int, default=256)
    ap.add_argument("--max-consecutive-errors", type=int, default=5)
    ap.add_argument("--allow-cpu", action="store_true", help="100%% GPU가 아니어도 계속 (8GB Mac에서는 비권장)")
    ap.add_argument("--no-retry-failed", action="store_true")
    ap.add_argument("--smoke", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    system, user_tpl, prompt_sha = load_prompt(a.prompt)
    ds = json.loads(Path(a.dataset).read_text(encoding="utf-8"))
    e5 = json.loads(Path(a.e5).read_text(encoding="utf-8"))
    pairs = plan(ds, e5)
    meta = {
        "type": "meta",
        "model": a.model,
        "options": options(a) | {"think": False, "format": "json-schema", "keep_alive": a.keep_alive},
        "prompt_file": Path(a.prompt).name,
        "prompt_sha": prompt_sha,
        "dataset_version": ds.get("version"),
        "e5_run": Path(a.e5).name,
        "inputs": "query text + candidate text only",
        "created": dt.datetime.now().isoformat(timespec="seconds"),
    }

    if a.smoke:
        out = Path(a.out) if a.out else HERE / "runs" / f"llm-smoke-{slug(a.model)}.jsonl"  # 모델별로 분리
        out.parent.mkdir(exist_ok=True)
        q, c, rank = pairs[0]
        print(f"smoke: {a.model} → {out}")
        print(f"smoke: {q['id']} × {c['id']} (e5 {rank}위)\n현재: {q['text']}\n과거: {c['text']}\n", flush=True)
        swap0 = swap_used_mb()
        rec = judge_once(a, system, user_tpl, q["text"], c["text"])
        append(out, meta | {"type": "smoke", "qid": q["id"], "cid": c["id"], **rec, "ts": dt.datetime.now().isoformat(timespec="seconds")})
        print(json.dumps(rec, ensure_ascii=False, indent=1))
        problem = guard(a, swap0, "smoke")
        if rec["status"] != "ok":
            print("\n✗ smoke 실패: 위 error를 확인하세요. 전체 실행을 시작하지 마세요.")
            sys.exit(2)
        if problem:
            print(f"\n✗ 메모리/GPU 문제: {problem}\n전체 실행을 시작하지 마세요.")
            sys.exit(3)
        print(f"\n✓ smoke OK ({rec['elapsed_ms']}ms). `ollama ps`로 한 번 더 확인한 뒤 진행하세요.")
        return

    out = Path(a.out) if a.out else HERE / "runs" / f"llm-{slug(a.model)}-{Path(a.prompt).stem}.jsonl"
    out.parent.mkdir(exist_ok=True)
    old_meta, latest = read_log(out)
    if old_meta:
        for k in ("model", "prompt_sha", "dataset_version", "e5_run"):
            if old_meta.get(k) != meta[k]:
                sys.exit(f"{out}는 다른 설정({k}: {old_meta.get(k)} ≠ {meta[k]})으로 만든 기록. --out으로 다른 파일을 지정하세요.")
        if old_meta.get("options") != meta["options"]:
            sys.exit(f"{out}의 options가 다름: {old_meta.get('options')} ≠ {meta['options']}")

    todo = []
    for attempt in range(1, a.attempts + 1):  # 1회차 전부 → 2회차 전부 (중간에 멈춰도 1회차가 먼저 완성)
        for q, c, rank in pairs:
            r = latest.get((c["id"], attempt))
            if r and (r["status"] == "ok" or a.no_retry_failed):
                continue
            todo.append((attempt, q, c, rank))
    total = len(pairs) * a.attempts
    done_ok = sum(r["status"] == "ok" for r in latest.values())
    errs = sum(r["status"] != "ok" for r in latest.values())
    n = min(len(todo), a.max_calls) if a.max_calls else len(todo)
    print(f"model {a.model} · prompt {meta['prompt_file']} ({prompt_sha}) · dataset {meta['dataset_version']}")
    print(f"pairs {len(pairs)} × attempts {a.attempts} = {total}회 · 완료 {done_ok} · 실패 기록 {errs} · 남음 {len(todo)} · 이번 실행 {n}회")
    print(f"예상 시간 (호출당 2~4초 가정): {n * 2 / 60:.0f}~{n * 4 / 60:.0f}분 · 저장: {out}")
    if a.dry_run or n == 0:
        return

    if not old_meta:
        append(out, meta)
    swap_start = swap_used_mb()
    swap0 = None  # warm 기준: 첫 호출(필요하면 모델 로드) 직후의 swap. 이후 증가만 guard 대상
    consecutive = 0
    for i, (attempt, q, c, rank) in enumerate(todo[:n], 1):
        rec = judge_once(a, system, user_tpl, q["text"], c["text"])
        sw = swap_used_mb()
        if i == 1:
            swap0 = sw
            if sw is not None and swap_start is not None:
                print(f"    첫 호출 load {rec.get('load_duration_ms', 0)}ms · swap {swap_start:.0f}→{sw:.0f}MB "
                      f"({sw - swap_start:+.0f}MB, 로드 비용) · 이후 기준 {sw:.0f}MB", flush=True)
        append(out, {"type": "judgment", "qid": q["id"], "cid": c["id"], "attempt": attempt, "e5_rank": rank,
                     **rec, "swap_mb": sw, "ts": dt.datetime.now().isoformat(timespec="seconds")})
        shown = f"label {rec['label']}" if rec["status"] == "ok" else f"ERROR {rec['error']}"
        drift = "" if sw is None or swap0 is None else f" swap {sw - swap0:+.0f}MB"
        print(f"{i:3d}/{n} a{attempt} {c['id']:6s} {rec.get('elapsed_ms', 0):5d}ms{drift} {shown}", flush=True)
        if i > 1 and (rec.get("load_duration_ms") or 0) > 1000:
            print(f"    ⚠️ 모델 재로드 감지 (load {rec['load_duration_ms']}ms)", flush=True)
        if i > 1 and sw is not None and swap0 is not None and sw - swap0 > a.max_swap_growth_mb:
            sys.exit(f"\n✗ 중단: warm 상태에서 swap이 {sw - swap0:.0f}MB 늘어남 (한도 {a.max_swap_growth_mb}MB)\n다시 실행하면 이어서 진행합니다.")
        consecutive = consecutive + 1 if rec["status"] != "ok" else 0
        if consecutive >= a.max_consecutive_errors:
            sys.exit(f"\n✗ 연속 {consecutive}회 실패 — 중단. Ollama 서버/모델 상태를 확인하세요. 다시 실행하면 이어서 진행합니다.")
        if i == 1 or i % a.check_every == 0:
            problem = guard(a, swap0, f"{i}/{n}")
            if problem:
                sys.exit(f"\n✗ 중단: {problem}\n다시 실행하면 이어서 진행합니다.")
    _, latest = read_log(out)
    print(f"\n완료. ok {sum(r['status'] == 'ok' for r in latest.values())}/{total} · "
          f"error {sum(r['status'] != 'ok' for r in latest.values())} → python3 llm_report.py")


if __name__ == "__main__":
    main()
