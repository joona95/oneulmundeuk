#!/usr/bin/env python3
"""
오늘문득 M5-2c: listwise selection. query당 1회, e5 Top10 후보를 한 prompt에 넣고 0~3개를 고르게 한다.

  python3 listwise_select.py --dry-run      # query별 후보 수 · 추정 토큰 · 섞인 순서 (호출 없음)
  python3 listwise_select.py                # 17회 호출 (중간에 멈춰도 다시 실행하면 이어서)

- 모델 입력: 현재 기록 text + 후보 text뿐. 후보 순서는 query id로 고정한 무작위 순서 (e5 순위가 위치로 드러나지 않게).
- 출력: {"selected": ["B", ...]} 최대 3개, reason 없음. 형식 위반(4개 이상 · 중복 · 없는 글자 · JSON 아님 · 잘림)은
  고치거나 잘라서 살리지 않고 status=error로 기록한다.
- 모델 옵션은 judge_v1과 동일 (llm_judge.options: temperature 0 · seed 7 · num_ctx 2048 · num_predict 192, think false, keep_alive 10m).
- GPU / warm swap / 모델 재로드 guard는 llm_judge.py와 같은 함수를 쓴다 (import만, 수정 없음).
- 토큰: 호출 전 보수적 상한으로 확인, 호출 후 prompt_eval_count로 잘림 여부 확인. 의심되면 즉시 중단.
"""
import argparse
import datetime as dt
import hashlib
import json
import random
import re
import sys
import time
import urllib.error
from pathlib import Path

import llm_judge as J

HERE = Path(__file__).resolve().parent
TOP_N = 10
MAX_SELECT = 3
LETTERS = "ABCDEFGHIJKL"
SHUFFLE_SALT = "listwise_v1"
# 토큰 추정: judge_v1 157건의 prompt_eval_count 회귀 (≈ 0.50 토큰/글자 + 틀) 기준의 보수적 상·하한
TOK_PER_CHAR_UPPER, TOK_OVERHEAD_UPPER = 0.7, 60
TOK_PER_CHAR_LOWER = 0.35
PREFLIGHT_LIMIT = 1500


# ── prompt / shuffle ─────────────────────────────────────────────────────────
def load_prompt(path):
    raw = Path(path).read_text(encoding="utf-8")
    m = re.match(r"\s*### SYSTEM\n(.*?)\n### USER\n(.*)\Z", raw, re.S)
    if not m:
        sys.exit(f"{path}: '### SYSTEM' / '### USER' 구획을 찾을 수 없음")
    system, user = m.group(1).strip(), m.group(2).strip()
    assert "{current}" in user and "{candidates}" in user
    return system, user, hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]


def shuffled(qid, ids):
    """query id로 고정된 순서. 같은 qid → 항상 같은 순서, 입력 순서(e5 순위)와 무관."""
    seed = int(hashlib.sha256(f"{SHUFFLE_SALT}:{qid}".encode("utf-8")).hexdigest()[:16], 16)
    out = sorted(ids)  # e5 순서를 지우고 시작
    random.Random(seed).shuffle(out)
    return out


def letter_map(order):
    """{"A": cid, ...} (prompt에 나오는 순서)"""
    return {LETTERS[i]: cid for i, cid in enumerate(order)}


def render(system, user_tpl, current, letters, texts):
    block = "\n".join(f"{L}: {texts[cid]}" for L, cid in letters.items())
    user = user_tpl.replace("{current}", current).replace("{candidates}", block)
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


def schema(letters):
    return {
        "type": "object",
        "properties": {"selected": {"type": "array", "items": {"type": "string", "enum": list(letters)}, "maxItems": MAX_SELECT}},
        "required": ["selected"],
    }


def token_bounds(messages):
    chars = sum(len(m["content"]) for m in messages)
    return int(TOK_PER_CHAR_LOWER * chars), int(TOK_PER_CHAR_UPPER * chars + TOK_OVERHEAD_UPPER), chars


# ── output validation ────────────────────────────────────────────────────────
def validate(content, letters):
    """→ (selected_letters, None) 또는 (None, error). 절대 고치거나 잘라내지 않는다."""
    try:
        obj = json.loads(content)
    except (json.JSONDecodeError, TypeError) as e:
        return None, f"malformed JSON: {e}"
    if not isinstance(obj, dict) or "selected" not in obj:
        return None, "missing 'selected'"
    sel = obj["selected"]
    if not isinstance(sel, list):
        return None, f"'selected' is not a list: {sel!r}"
    if not all(isinstance(x, str) for x in sel):
        return None, f"non-string item: {sel!r}"
    if len(sel) > MAX_SELECT:
        return None, f"too many: {len(sel)} > {MAX_SELECT}"
    if len(set(sel)) != len(sel):
        return None, f"duplicate: {sel}"
    bad = [x for x in sel if x not in letters]
    if bad:
        return None, f"unknown candidate: {bad}"
    return sel, None


def call(a, messages, letters):
    body = {"model": a.model, "messages": messages, "stream": False, "think": False,
            "format": schema(letters), "keep_alive": a.keep_alive, "options": J.options(a)}
    t0 = time.time()
    rec = {"status": "error", "selected_letters": None, "error": None, "raw": None}
    try:
        resp = J.http_json(f"{a.host}/api/chat", body, timeout=a.timeout)
    except urllib.error.HTTPError as e:
        return rec | {"error": f"http {e.code}: {e.read().decode('utf-8', 'replace')[:300]}", "elapsed_ms": int((time.time() - t0) * 1000)}
    except Exception as e:
        return rec | {"error": f"{type(e).__name__}: {e}", "elapsed_ms": int((time.time() - t0) * 1000)}
    msg = resp.get("message") or {}
    content = msg.get("content") or ""
    rec.update(raw=content[:600], elapsed_ms=int((time.time() - t0) * 1000), done_reason=resp.get("done_reason"),
               prompt_eval_count=resp.get("prompt_eval_count"), eval_count=resp.get("eval_count"),
               load_duration_ms=round((resp.get("load_duration") or 0) / 1e6), thinking_present=bool(msg.get("thinking")))
    if resp.get("done_reason") == "length":
        rec["error"] = "truncated output (num_predict 한도)"
        return rec
    sel, err = validate(content, letters)
    if err:
        rec["error"] = err
        return rec
    rec.update(status="ok", selected_letters=sel)
    return rec


# ── plan ─────────────────────────────────────────────────────────────────────
def plan(ds, e5, system, user_tpl):
    order = {qid: [x["id"] for x in lst] for qid, lst in e5["rankings"].items()}
    items = []
    for q in ds["queries"]:
        texts = {c["id"]: c["text"] for c in q["candidates"]}
        if set(order.get(q["id"], [])) != set(texts):
            sys.exit(f"e5 run이 dataset과 맞지 않음 ({q['id']})")
        top = order[q["id"]][:TOP_N]
        letters = letter_map(shuffled(q["id"], top))
        msgs = render(system, user_tpl, q["text"], letters, texts)
        lo, hi, chars = token_bounds(msgs)
        items.append({"q": q, "e5_top": top, "letters": letters, "messages": msgs, "tok_lower": lo, "tok_upper": hi, "chars": chars})
    return items


def read_log(path):
    meta, latest = None, {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                r = json.loads(line)
                if r.get("type") == "meta":
                    meta = meta or r
                elif r.get("type") == "listwise":
                    latest[r["qid"]] = r
    return meta, latest


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default="qwen3.5:2b-q4_K_M")
    ap.add_argument("--host", default="http://127.0.0.1:11434")
    ap.add_argument("--prompt", default=HERE / "prompts" / "listwise_v1.txt")
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--out", help="기본: runs/listwise-<model>-<prompt>.jsonl")
    ap.add_argument("--num-ctx", type=int, default=2048)
    ap.add_argument("--num-predict", type=int, default=192)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--keep-alive", default="10m")
    ap.add_argument("--timeout", type=int, default=180)
    ap.add_argument("--max-swap-growth-mb", type=int, default=256)
    ap.add_argument("--max-consecutive-errors", type=int, default=3)
    ap.add_argument("--allow-cpu", action="store_true", help="100%% GPU가 아니어도 계속 (8GB Mac에서는 비권장)")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args(argv)

    system, user_tpl, sha = load_prompt(a.prompt)
    ds = json.loads(Path(a.dataset).read_text(encoding="utf-8"))
    e5 = json.loads(Path(a.e5).read_text(encoding="utf-8"))
    items = plan(ds, e5, system, user_tpl)
    out = Path(a.out) if a.out else HERE / "runs" / f"listwise-{J.slug(a.model)}-{Path(a.prompt).stem}.jsonl"
    meta = {"type": "meta", "model": a.model,
            "options": J.options(a) | {"think": False, "format": "json-schema", "keep_alive": a.keep_alive},
            "prompt_file": Path(a.prompt).name, "prompt_sha": sha, "dataset_version": ds.get("version"),
            "e5_run": Path(a.e5).name, "top_n": TOP_N, "max_select": MAX_SELECT, "shuffle_salt": SHUFFLE_SALT,
            "inputs": "current text + candidate texts only (shuffled, lettered)",
            "created": dt.datetime.now().isoformat(timespec="seconds")}

    old_meta, latest = read_log(out)
    if old_meta:
        for k in ("model", "options", "prompt_sha", "dataset_version", "e5_run", "top_n", "shuffle_salt"):
            if old_meta.get(k) != meta[k]:
                sys.exit(f"{out}는 다른 설정({k})으로 만든 기록. --out으로 다른 파일을 지정하세요.")
    todo = [it for it in items if latest.get(it["q"]["id"], {}).get("status") != "ok"]
    max_hi = max(it["tok_upper"] for it in items)

    print(f"model {a.model} · prompt {meta['prompt_file']} ({sha}) · dataset {meta['dataset_version']} · e5 {meta['e5_run']}")
    print(f"options {json.dumps(meta['options'], ensure_ascii=False)}")
    if a.dry_run:
        print(f"\n{'query':6s} {'후보':>4s} {'chars':>6s} {'추정 토큰(하한~상한)':>20s}  섞인 순서 (글자=candidate id, 괄호=e5 순위)")
        for it in items:
            rank = {cid: i + 1 for i, cid in enumerate(it["e5_top"])}
            mp = " ".join(f"{L}={cid.split('-')[1]}({rank[cid]})" for L, cid in it["letters"].items())
            print(f"{it['q']['id']:6s} {len(it['letters']):4d} {it['chars']:6d} {it['tok_lower']:9d} ~ {it['tok_upper']:5d}      {mp}")
        print(f"\n최대 추정 토큰(상한) {max_hi} + 출력 {a.num_predict} = {max_hi + a.num_predict} / num_ctx {a.num_ctx} "
              f"(여유 {a.num_ctx - max_hi - a.num_predict}) · preflight 한도 {PREFLIGHT_LIMIT}")
        print(f"호출 예정 {len(todo)}회 (전체 {len(items)} · 완료 {len(items) - len(todo)}) · 저장 {out}")
        return 0
    if max_hi > PREFLIGHT_LIMIT:
        sys.exit(f"✗ preflight: 추정 상한 {max_hi} > {PREFLIGHT_LIMIT}. 호출하지 않고 중단.")
    if not todo:
        print("남은 호출 없음")
        return 0

    out.parent.mkdir(exist_ok=True)
    if not old_meta:
        J.append(out, meta)
    swap_start, swap0, consecutive = J.swap_used_mb(), None, 0
    for i, it in enumerate(todo, 1):
        q = it["q"]
        rec = call(a, it["messages"], it["letters"])
        sw = J.swap_used_mb()
        if i == 1:
            swap0 = sw
        pec = rec.get("prompt_eval_count")
        trunc = None
        if pec is not None and (pec >= a.num_ctx - a.num_predict or pec < it["tok_lower"]):
            trunc = f"prompt 잘림 의심: prompt_eval_count {pec} (추정 {it['tok_lower']}~{it['tok_upper']}, num_ctx {a.num_ctx})"
            rec.update(status="error", error=trunc, selected_letters=None)
        sel_ids = [it["letters"][L] for L in rec["selected_letters"]] if rec["status"] == "ok" else None
        J.append(out, {"type": "listwise", "qid": q["id"], "attempt": 1, "letters": it["letters"],
                       "prompt_order": list(it["letters"].values()), "e5_top": it["e5_top"],
                       "tok_estimate": [it["tok_lower"], it["tok_upper"]], **rec, "selected_ids": sel_ids,
                       "swap_mb": sw, "ts": dt.datetime.now().isoformat(timespec="seconds")})
        shown = f"selected {rec['selected_letters']} → {sel_ids}" if rec["status"] == "ok" else f"ERROR {rec['error']}"
        drift = "" if sw is None or swap0 is None else f" swap {sw - swap0:+.0f}MB"
        print(f"{i:2d}/{len(todo)} {q['id']} {rec.get('elapsed_ms', 0):5d}ms tok {pec}{drift} {shown}", flush=True)
        if trunc:
            sys.exit(f"\n✗ 중단: {trunc}")
        if i > 1 and (rec.get("load_duration_ms") or 0) > 1000:
            print(f"    ⚠️ 모델 재로드 감지 (load {rec['load_duration_ms']}ms)", flush=True)
        if i > 1 and sw is not None and swap0 is not None and sw - swap0 > a.max_swap_growth_mb:
            sys.exit(f"\n✗ 중단: warm 상태에서 swap이 {sw - swap0:.0f}MB 늘어남 (한도 {a.max_swap_growth_mb}MB)")
        problem = J.guard(a, swap0, f"{i}/{len(todo)}") if i == 1 else None
        if problem:
            sys.exit(f"\n✗ 중단: {problem}")
        consecutive = consecutive + 1 if rec["status"] != "ok" else 0
        if consecutive >= a.max_consecutive_errors:
            sys.exit(f"\n✗ 연속 {consecutive}회 실패 — 중단")
    _, latest = read_log(out)
    print(f"\n완료. ok {sum(r['status'] == 'ok' for r in latest.values())}/{len(items)} · "
          f"error {sum(r['status'] != 'ok' for r in latest.values())}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
