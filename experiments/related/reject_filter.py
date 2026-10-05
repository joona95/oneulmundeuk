#!/usr/bin/env python3
"""
오늘문득 M5-2e: Qwen 2B를 conservative reject filter로 쓴다. e5 Top10 후보를 하나씩 keep=true/false로만 판정. stdlib만 사용.

  python3 reject_filter.py --dry-run          # 호출 계획 · 토큰 안전성 확인 (모델 호출 없음)
  python3 reject_filter.py --smoke            # 1건만 (runs/reject-smoke-<model>.jsonl, 본 실행 기록과 분리)
  python3 reject_filter.py                    # 본 실행 1회 (중간에 멈추면 다시 실행해 이어서)

- 모델 입력: prompts/reject_filter_v1.txt + 현재 기록 text + 과거 기록 text뿐. label/case/note/date/e5 순위/cosine 없음.
- 옵션은 judge_v1과 동일: temperature 0 · seed 7 · num_ctx 2048 · num_predict 192 · think false · keep_alive 10m · JSON schema.
  schema만 {"keep": boolean} (reason 없음).
- 1회만 실행한다 (attempt 1). 실패(API 오류 · 잘림 · 형식 오류)는 status=error로 남기고 다시 시도하지 않는다
  (--retry-failed를 명시할 때만 그 pair를 다시 호출). 평가에서는 실패를 keep으로 본다 (filter는 실패 시 유지).
- 8GB Mac 보호는 llm_judge.py와 같다: 100% GPU · 다른 모델 없음 · warm swap +256MB · 재로드 감지 · 연속 실패 중단.

저장: runs/reject-<model>-reject_filter_v1.jsonl (append-only, 첫 줄 meta)
"""
import argparse
import datetime as dt
import json
import os
import sys
import time
import urllib.error
from pathlib import Path

import llm_judge as J

HERE = Path(__file__).resolve().parent
DEFAULT_MODEL = "qwen3.5:2b-q4_K_M"
TOP_N = 10
ATTEMPT = 1
EXPECTED_PROMPT_SHA = "de90ab9be00c412a"  # prompts/reject_filter_v1.txt
SCHEMA = {"type": "object", "properties": {"keep": {"type": "boolean"}}, "required": ["keep"]}
TOK_PER_CHAR, TOK_OVERHEAD = 0.7, 60  # 보수적 상한 (한국어 Qwen tokenizer ≤ 0.7 tok/char)


def plan(ds, e5, top_n=TOP_N):
    """(query, candidate, e5 rank) — query 순, 각 query 안에서 e5 순위 순, 상위 top_n개."""
    return [(q, c, r) for q, c, r in J.plan(ds, e5) if r <= top_n]


def validate(content, truncated):
    rec = {"status": "error", "keep": None, "error": None, "raw": content[:300]}
    if truncated:
        rec["error"] = "truncated (num_predict 한도 도달)"
        return rec
    try:
        obj = json.loads(content)
    except json.JSONDecodeError as e:
        rec["error"] = f"malformed JSON: {e}"
        return rec
    keep = obj.get("keep") if isinstance(obj, dict) else None
    if not isinstance(keep, bool):
        rec["error"] = f"invalid keep: {keep!r}"
        return rec
    rec.update(status="ok", keep=keep, extra_keys=sorted(set(obj) - {"keep"}) or None)
    return rec


def filter_once(a, system, user_tpl, current, past):
    body = {"model": a.model, "messages": J.messages(system, user_tpl, current, past), "stream": False, "think": False,
            "format": SCHEMA, "keep_alive": a.keep_alive, "options": J.options(a)}
    t0 = time.time()
    try:
        resp = J.http_json(f"{a.host}/api/chat", body, timeout=a.timeout)
    except urllib.error.HTTPError as e:
        return {"status": "error", "keep": None, "error": f"http {e.code}: {e.read().decode('utf-8', 'replace')[:300]}",
                "raw": None, "elapsed_ms": int((time.time() - t0) * 1000)}
    except Exception as e:
        return {"status": "error", "keep": None, "error": f"{type(e).__name__}: {e}", "raw": None,
                "elapsed_ms": int((time.time() - t0) * 1000)}
    msg = resp.get("message") or {}
    rec = validate(msg.get("content") or "", resp.get("done_reason") == "length")
    rec.update(elapsed_ms=int((time.time() - t0) * 1000), done_reason=resp.get("done_reason"),
               prompt_eval_count=resp.get("prompt_eval_count"), eval_count=resp.get("eval_count"),
               load_duration_ms=round((resp.get("load_duration") or 0) / 1e6), thinking_present=bool(msg.get("thinking")))
    if rec["status"] == "ok" and (rec["prompt_eval_count"] or 0) >= a.num_ctx - a.num_predict:
        rec.update(status="error", keep=None, error=f"prompt 잘림 의심 (prompt_eval_count {rec['prompt_eval_count']})")
    return rec


def read_log(path):
    meta, latest = None, {}
    if Path(path).exists():
        for line in Path(path).read_text(encoding="utf-8").splitlines():
            if line.strip():
                r = json.loads(line)
                if r.get("type") == "meta":
                    meta = meta or r
                elif r.get("type") == "filter":
                    latest[(r["cid"], r["attempt"])] = r
    return meta, latest


def tok_upper(system, user_tpl, q, c):
    return int(sum(len(m["content"]) for m in J.messages(system, user_tpl, q["text"], c["text"])) * TOK_PER_CHAR) + TOK_OVERHEAD


def build_parser():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=DEFAULT_MODEL)
    ap.add_argument("--host", default=os.environ.get("OLLAMA_HOST_URL", "http://127.0.0.1:11434"))
    ap.add_argument("--prompt", default=HERE / "prompts" / "reject_filter_v1.txt")
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--out")
    ap.add_argument("--num-ctx", type=int, default=2048)
    ap.add_argument("--num-predict", type=int, default=192)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--keep-alive", default="10m")
    ap.add_argument("--timeout", type=int, default=120)
    ap.add_argument("--check-every", type=int, default=20)
    ap.add_argument("--max-swap-growth-mb", type=int, default=256)
    ap.add_argument("--max-consecutive-errors", type=int, default=5)
    ap.add_argument("--allow-cpu", action="store_true", help="100%% GPU가 아니어도 계속 (8GB Mac에서는 비권장)")
    ap.add_argument("--retry-failed", action="store_true", help="status=error인 pair만 다시 호출 (기본: 다시 호출하지 않음)")
    ap.add_argument("--smoke", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    return ap


def main(argv=None):
    a = build_parser().parse_args(argv)
    system, user_tpl, sha = J.load_prompt(a.prompt)
    if sha != EXPECTED_PROMPT_SHA:
        sys.exit(f"prompt sha {sha} ≠ reject_filter_v1 {EXPECTED_PROMPT_SHA}. 이 실험은 prompt를 바꾸지 않는다.")
    ds = json.loads(Path(a.dataset).read_text(encoding="utf-8"))
    e5 = json.loads(Path(a.e5).read_text(encoding="utf-8"))
    pairs = plan(ds, e5)
    meta = {"type": "meta", "model": a.model,
            "options": J.options(a) | {"think": False, "format": "json-schema", "keep_alive": a.keep_alive},
            "schema": SCHEMA, "prompt_file": Path(a.prompt).name, "prompt_sha": sha, "dataset_version": ds.get("version"),
            "e5_run": Path(a.e5).name, "top_n": TOP_N, "inputs": "query text + candidate text only",
            "created": dt.datetime.now().isoformat(timespec="seconds")}
    upper = max(tok_upper(system, user_tpl, q, c) for q, c, _ in pairs)
    if upper + a.num_predict >= a.num_ctx:
        sys.exit(f"토큰 상한 {upper} + {a.num_predict} ≥ num_ctx {a.num_ctx}")

    if a.smoke:
        out = Path(a.out) if a.out else HERE / "runs" / f"reject-smoke-{J.slug(a.model)}.jsonl"
        q, c, rank = pairs[0]
        print(f"smoke: {q['id']} × {c['id']} (e5 {rank}위) → {out}", flush=True)
        swap0 = J.swap_used_mb()
        rec = filter_once(a, system, user_tpl, q["text"], c["text"])
        J.append(out, meta | {"type": "smoke", "qid": q["id"], "cid": c["id"], **rec})
        print(json.dumps(rec, ensure_ascii=False, indent=1))
        problem = J.guard(a, swap0, "smoke")
        if rec["status"] != "ok" or problem:
            sys.exit(f"✗ smoke 실패: {rec.get('error') or problem}")
        print("✓ smoke OK")
        return 0

    out = Path(a.out) if a.out else HERE / "runs" / f"reject-{J.slug(a.model)}-{Path(a.prompt).stem}.jsonl"
    old, latest = read_log(out)
    if old:
        for k in ("model", "options", "schema", "prompt_sha", "dataset_version", "e5_run", "top_n"):
            if old.get(k) != meta[k]:
                sys.exit(f"{out}는 다른 설정({k})으로 만든 기록. 덮어쓰지 않는다.")
    todo = [(q, c, r) for q, c, r in pairs
            if not ((x := latest.get((c["id"], ATTEMPT))) and (x["status"] == "ok" or not a.retry_failed))]
    errs = sum(r["status"] != "ok" for r in latest.values())
    print(f"model {a.model} · prompt {meta['prompt_file']} ({sha}) · dataset {meta['dataset_version']} · e5 Top{TOP_N}")
    print(f"options {json.dumps(meta['options'], ensure_ascii=False)} · schema {json.dumps(SCHEMA)}")
    print(f"queries {len(ds['queries'])} · 호출 대상 {len(pairs)}쌍 × 1회 · 기록됨 {len(latest)} (실패 {errs}) · 이번 실행 {len(todo)}회")
    print(f"토큰 상한 추정 최대 {upper} + num_predict {a.num_predict} < num_ctx {a.num_ctx} · 예상 {len(todo) * 1.5 / 60:.0f}~{len(todo) * 3 / 60:.0f}분")
    print(f"저장: {out}")
    if a.dry_run:
        return 0
    if not todo:
        print("남은 호출 없음")
        return 0

    if not old:
        J.append(out, meta)
    swap_start, swap0, consecutive = J.swap_used_mb(), None, 0
    for i, (q, c, rank) in enumerate(todo, 1):
        rec = filter_once(a, system, user_tpl, q["text"], c["text"])
        sw = J.swap_used_mb()
        if i == 1:
            swap0 = sw
            if sw is not None and swap_start is not None:
                print(f"    첫 호출 load {rec.get('load_duration_ms', 0)}ms · swap {swap_start:.0f}→{sw:.0f}MB · 이후 기준 {sw:.0f}MB", flush=True)
        J.append(out, {"type": "filter", "qid": q["id"], "cid": c["id"], "attempt": ATTEMPT, "e5_rank": rank, **rec,
                       "swap_mb": sw, "ts": dt.datetime.now().isoformat(timespec="seconds")})
        shown = f"keep={rec['keep']}" if rec["status"] == "ok" else f"ERROR {rec['error']}"
        drift = "" if sw is None or swap0 is None else f" swap {sw - swap0:+.0f}MB"
        print(f"{i:3d}/{len(todo)} {c['id']:6s} e5#{rank:<2d} {rec.get('elapsed_ms', 0):5d}ms{drift} {shown}", flush=True)
        if i > 1 and (rec.get("load_duration_ms") or 0) > 1000:
            print(f"    ⚠️ 모델 재로드 감지 (load {rec['load_duration_ms']}ms)", flush=True)
        if i > 1 and sw is not None and swap0 is not None and sw - swap0 > a.max_swap_growth_mb:
            sys.exit(f"\n✗ 중단: warm 상태에서 swap이 {sw - swap0:.0f}MB 늘어남. 다시 실행하면 이어서 진행합니다.")
        if rec.get("error", "") and rec["error"].startswith("prompt 잘림"):
            sys.exit(f"\n✗ 중단: {rec['error']}")
        consecutive = consecutive + 1 if rec["status"] != "ok" else 0
        if consecutive >= a.max_consecutive_errors:
            sys.exit(f"\n✗ 연속 {consecutive}회 실패 — 중단. Ollama 상태를 확인하세요. 다시 실행하면 남은 pair만 진행합니다.")
        if i == 1 or i % a.check_every == 0:
            problem = J.guard(a, swap0, f"{i}/{len(todo)}")
            if problem:
                sys.exit(f"\n✗ 중단: {problem}\n다시 실행하면 이어서 진행합니다.")
    _, latest = read_log(out)
    ok = [r for r in latest.values() if r["status"] == "ok"]
    print(f"\n완료. ok {len(ok)}/{len(pairs)} · keep {sum(r['keep'] for r in ok)} · reject {sum(not r['keep'] for r in ok)} "
          f"· error {len(latest) - len(ok)} → python3 reject_filter_report.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
