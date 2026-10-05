#!/usr/bin/env python3
"""
Android(llama.cpp) judge_v1 157쌍 결과를 Mac/Ollama judge_v1과 같은 방식으로 평가한다. 모델을 호출하지 않는다.

  python3 poc_judge.py session --tasks all        # 기기에서 157쌍 (먼저)
  python3 android_eval.py                         # 가장 최근 all session → 리포트

선택 규칙과 지표는 기존과 같다: e5 순서에서 judge label 2만, 최대 5개 (selector_experiment.judge2_only@5 = compare_judges "all")
→ eval.compute (Good@5 · nDCG@5 · Worth% · Bad% · Top1=0 · Quiet miss). 판정 실패는 label 없음(2가 아님)으로 계산한다.
출력: runs/llm-android-qwen3.5-2b-q4_K_M-judge_v1.jsonl (llm_judge 형식) · results-android-judge_v1.md · runs/android-judge_v1-pairs.csv
"""
import argparse
import csv
import json
import statistics
from collections import Counter
from pathlib import Path

import poc_judge as P
import selector_experiment as S  # experiments/related (poc_judge가 sys.path에 추가)
from llm_report import F_TYPES

HERE = Path(__file__).resolve().parent
CASES = ["repeat_low_value", "lexical_trap", "resolve_action", "worry_outcome", "implicit_link", "recurring", "change", "reversal"]
NOLAB2 = ("q02", "q15", "q16", "q17")
CONVERTED = HERE / "runs" / "llm-android-qwen3.5-2b-q4_K_M-judge_v1.jsonl"


def latest_all_session(runs):
    for f in sorted(Path(runs).glob("session-*.jsonl"), reverse=True):
        if f.name.endswith("-server.log"):
            continue
        meta = json.loads(f.read_text(encoding="utf-8").splitlines()[0])
        if "all" in meta.get("tasks", []):
            return f
    raise SystemExit("--tasks all session이 없음. 먼저 `python3 poc_judge.py session --tasks all`")


def load_session(path, plan):
    lines = [json.loads(l) for l in Path(path).read_text(encoding="utf-8").splitlines() if l.strip()]
    meta, load = lines[0], next(r for r in lines if r["type"] == "load")
    if meta["prompt_sha"] != P.EXPECTED_PROMPT_SHA:
        raise SystemExit(f"prompt sha {meta['prompt_sha']} ≠ {P.EXPECTED_PROMPT_SHA}")
    expected_args = P.server_args(argparse.Namespace(port=meta["server_args"][meta["server_args"].index("--port") + 1], threads=0, server_arg=None))
    if meta["server_args"] != expected_args:
        raise SystemExit(f"llama-server 설정이 PoC 기본값과 다름: {meta['server_args']}")
    recs = [r for r in lines if r["type"] == "judgment" and r["task"] == "all"]
    if [r["cid"] for r in recs] != [c["id"] for _, c, _ in plan]:
        raise SystemExit(f"all task가 157쌍을 순서대로 다 담고 있지 않음 ({len(recs)}개) — 중단된 session?")
    crash = [r for r in lines if r["type"] == "crash"]
    return meta, load, recs, crash


def write_converted(path, meta, recs, plan):
    rank = {c["id"]: (q["id"], r) for q, c, r in plan}
    out = [json.dumps({"type": "meta", "model": "qwen3.5:2b-q4_K_M (Ollama blob) on Android llama.cpp", "options": {"server_args": meta["server_args"],
                       "request": {k: v for k, v in meta["request_example"].items() if k != "messages"}}, "prompt_file": "judge_v1.txt",
                       "prompt_sha": meta["prompt_sha"], "dataset_version": "1.1", "e5_run": "e5-small-ko.v1.1.json",
                       "inputs": "query text + candidate text only", "device": meta.get("device")}, ensure_ascii=False)]
    for r in recs:
        qid, rk = rank[r["cid"]]
        out.append(json.dumps({"type": "judgment", "qid": qid, "cid": r["cid"], "attempt": 1, "e5_rank": rk, "status": r["status"],
                               "label": r["label"], "reason": r["reason"], "error": r["error"], "raw": r.get("raw"),
                               "elapsed_ms": r["wall_ms"], "prompt_eval_count": r.get("prompt_n"), "eval_count": r.get("predicted_n")},
                              ensure_ascii=False))
    body = "\n".join(out) + "\n"
    if path.exists() and path.read_text(encoding="utf-8") != body:
        raise SystemExit(f"{path}가 이미 있고 내용이 다름 — 덮어쓰지 않는다.")
    path.write_text(body, encoding="utf-8")


def kappa(pairs):
    n = len(pairs)
    po = sum(a == b for a, b in pairs) / n
    ca, cb = Counter(a for a, _ in pairs), Counter(b for _, b in pairs)
    pe = sum(ca[k] * cb[k] for k in set(ca) | set(cb)) / (n * n)
    return (po - pe) / (1 - pe) if pe < 1 else 1.0


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--session")
    ap.add_argument("--out", default=HERE / "results-android-judge_v1.md")
    ap.add_argument("--csv", default=HERE / "runs" / "android-judge_v1-pairs.csv")
    ap.add_argument("--converted", default=CONVERTED)
    a = ap.parse_args(argv)

    ds, plan = P.load_pairs()
    session = Path(a.session) if a.session else latest_all_session(HERE / "runs")
    meta, load, recs, crash = load_session(session, plan)
    write_converted(Path(a.converted), meta, recs, plan)

    qs = ds["queries"]
    order = {}
    for q, c, _ in plan:  # e5 순서
        order.setdefault(q["id"], []).append(c["id"])
    truth = {c["id"]: c for q in qs for c in q["candidates"]}
    mac = P.mac_labels()
    android = {r["cid"]: (r["label"] if r["status"] == "ok" else None) for r in recs}
    assert len(mac) == 157, "Mac judge_v1 1회차 157개가 필요"
    sel = {"e5 Top5": {q["id"]: order[q["id"]][:5] for q in qs},
           "Mac judge_v1": {q["id"]: [c for c in order[q["id"]] if mac.get(c) == 2][:5] for q in qs},
           "Android judge_v1": {q["id"]: [c for c in order[q["id"]] if android.get(c) == 2][:5] for q in qs}}
    names = list(sel)
    res = {n: S.evaluate(ds, sel[n], 5) for n in names}
    total2 = sum(c["label"] == 2 for c in truth.values())

    wall = [r["wall_ms"] for r in recs]
    pm = [r["prompt_ms"] for r in recs if r.get("prompt_ms") is not None]
    gm = [r["predicted_ms"] for r in recs if r.get("predicted_ms") is not None]
    pn = [r["prompt_n"] for r in recs if r.get("prompt_n") is not None]
    gn = [r["predicted_n"] for r in recs if r.get("predicted_n") is not None]
    hwm = max([load["mem"].get("VmHWM", 0)] + [(r.get("mem") or {}).get("VmHWM", 0) for r in recs])
    errs = [r for r in recs if r["status"] != "ok"]
    thinking = sum(bool(r.get("thinking_present")) for r in recs)

    out = []
    p = out.append
    p("# Android judge_v1 · frozen M5-0.1 benchmark 157쌍 (Mac/Ollama vs Android/llama.cpp)\n")
    p("> ⚠️ dataset v1.1은 여러 실험에 이미 쓴 development benchmark다. 아래는 runtime(Mac/Ollama → Android/llama.cpp)이 바뀌었을 때 "
      "제품 지표가 유지되는지 보는 비교이며, 일반화 성능 추정이 아니다. 모델 파일 · judge_v1 prompt · 선택 규칙은 Mac 실행과 같다.\n")
    dev = meta.get("device") or {}
    p(f"- 기기: {dev.get('model')} · SoC {dev.get('soc')} · Android {dev.get('android')} · " + " ".join((dev.get('mem') or '').split()))
    p(f"- runtime: {(dev.get('build_info') or '').splitlines()[0] if dev.get('build_info') else '?'} · " +
      " · ".join((dev.get('build_info') or '').splitlines()[2:3]) + " · CPU only")
    p(f"- 모델 파일: `{(dev.get('model_file') or '').split()[-1] if dev.get('model_file') else '?'}` ({(dev.get('model_file') or '?').split()[4] if dev.get('model_file') else '?'} bytes, Ollama blob 그대로)")
    p(f"- llama-server: `{' '.join(meta['server_args'])}` · 요청: temperature 0 · seed 7 · max_tokens 192 · json_schema · enable_thinking=false")
    p(f"- prompt `judge_v1.txt` sha {meta['prompt_sha']} · session `{session.name}`\n")

    p("## 1. 실행 · 성능 (157쌍 연속, 모델 reload 없음)\n")
    p("| 항목 | 값 |")
    p("| --- | --- |")
    p(f"| 판정 완료 | ok {len(recs) - len(errs)}/157 · 실패 {len(errs)}" + (f" ({Counter(r['error'].split(':')[0] for r in errs)})" if errs else "") + " |")
    p(f"| crash / OOM | {'있음' if crash else '없음'} |")
    p(f"| model load | {load['load_ms'] / 1000:.1f}s |")
    p(f"| 전체 실행 시간 (load 제외 / 포함) | {sum(wall) / 1000:.1f}s / {(sum(wall) + load['load_ms']) / 1000:.1f}s |")
    p(f"| 1쌍 latency | median {statistics.median(wall) / 1000:.2f}s · mean {statistics.mean(wall) / 1000:.2f}s · p90 {sorted(wall)[int(len(wall) * .9)] / 1000:.2f}s · max {max(wall) / 1000:.2f}s |")
    if pm and gm:
        p(f"| prompt processing | 평균 {statistics.mean(pm):.0f}ms/쌍 · 평균 {statistics.mean(pn):.0f} tok 처리 (prefix cache 제외분) · {sum(pn) / sum(pm) * 1000:.1f} tok/s |")
        p(f"| generation | 평균 {statistics.mean(gm):.0f}ms/쌍 · 평균 {statistics.mean(gn):.0f} tok · {sum(gn) / sum(gm) * 1000:.1f} tok/s |")
    p(f"| peak RSS (VmHWM) | {hwm:.0f} MB |")
    p(f"| thinking 출력 | {thinking}건 |")

    p("\n## 2. 제품 지표 (label 2만, e5 순서, 최대 5개)\n")
    p("| metric | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- |" + " --- |" * len(names))
    keys = [("Good@5", "Good@5 (2 회수율, query 평균)"), ("nDCG@5", "nDCG@5 (gain 2→3, 1→1)"), ("Worth%", "Worth% (보여준 것 중 2 비율)"),
            ("Bad%", "Bad% (보여준 것 중 0 비율, 전체)"), ("Top1=0", "Top1=0 (1위에 0을 올린 query 비율)"),
            ("Quiet miss", "Quiet miss (2 없는 query에서 보여준 개수 평균)")]
    for label, k in keys:
        p(f"| {label} | " + " | ".join(f"{res[n]['s'][k]:.2f}" for n in names) + " |")
    p("| 보여준 총 개수 | " + " | ".join(str(res[n]["shown"]) for n in names) + " |")
    p(f"| label 2 보여줌 (/{total2}) | " + " | ".join(str(res[n]["good"]) for n in names) + " |")
    p("| label 0 보여줌 | " + " | ".join(str(res[n]["bad"]) for n in names) + " |")
    p("| F7 0개 반환 | " + " | ".join(f"{res[n]['f7']}/{res[n]['f7_total']}" for n in names) + " |")

    p("\n## 3. failure case별 (보여준 수 / 전체)\n")
    goal = {c: g for _, _, c, g in F_TYPES}
    p("| case | 목표 | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- | --- |" + " --- |" * len(names))
    for c in CASES:
        p(f"| {c} | {goal.get(c, '회수 유지')} | " + " | ".join(f"{res[n]['case_shown'][c]}/{res[n]['case_total'][c]}" for n in names) + " |")
    p("\nF7 · label 2가 없는 query의 노출 개수\n")
    p("| query | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- |" + " --- |" * len(names))
    for qid in NOLAB2:
        p(f"| {qid} | " + " | ".join("**0 ✓**" if not sel[n][qid] else f"{len(sel[n][qid])} [" + ",".join(str(truth[c]['label']) for c in sel[n][qid]) + "]" for n in names) + " |")

    p("\n## 4. confusion matrix (정답 × 판정, 157쌍)\n")
    for name, lab in (("Mac judge_v1", mac), ("Android judge_v1", android)):
        cm = Counter((c["label"], lab.get(cid)) for cid, c in truth.items())
        p(f"`{name}`\n")
        p("| 정답 \\ 판정 | 2 | 1 | 0 | 실패 |")
        p("| --- | --- | --- | --- | --- |")
        for g in (2, 1, 0):
            p(f"| **{g}** | " + " | ".join(str(cm[(g, x)]) for x in (2, 1, 0, None)) + " |")
        p("")

    p("## 5. Mac vs Android label 일치 (157쌍)\n")
    both = [(mac[c], android[c]) for c in truth if android.get(c) is not None]
    agree = sum(m == x for m, x in both)
    p(f"- 일치 {agree}/{len(truth)} ({agree / len(truth):.0%}) · Cohen's κ {kappa(both):.2f}" + (f" · Android 실패 {len(truth) - len(both)}쌍 제외" if len(both) < len(truth) else ""))
    two = [c for c in truth if mac[c] == 2 or android.get(c) == 2]
    p(f"- label 2 판정 일치 (둘 중 하나라도 2인 {len(two)}쌍 중 둘 다 2): {sum(mac[c] == 2 and android.get(c) == 2 for c in two)}")
    cm = Counter((mac[c], android.get(c)) for c in truth)
    p("\n| Mac \\ Android | 2 | 1 | 0 | 실패 |")
    p("| --- | --- | --- | --- | --- |")
    for g in (2, 1, 0):
        p(f"| **{g}** | " + " | ".join(str(cm[(g, x)]) for x in (2, 1, 0, None)) + " |")
    p("\n정답 label · case별 일치\n")
    p("| 구분 | 쌍 | 일치 |")
    p("| --- | --- | --- |")
    for g in (2, 1, 0):
        ids = [c for c in truth if truth[c]["label"] == g]
        p(f"| 정답 {g} | {len(ids)} | {sum(mac[c] == android.get(c) for c in ids)} |")
    for case in CASES + ["ambiguous", "unrelated"]:
        ids = [c for c in truth if truth[c]["case"] == case]
        p(f"| {case} | {len(ids)} | {sum(mac[c] == android.get(c) for c in ids)} |")

    p(f"\n## 6. Mac judge_v1 vs Android judge_v1\n")
    p("| | Mac/Ollama | Android/llama.cpp | 변화 |")
    p("| --- | --- | --- | --- |")
    m, x = res["Mac judge_v1"], res["Android judge_v1"]
    for label, k in keys:
        p(f"| {label} | {m['s'][k]:.2f} | {x['s'][k]:.2f} | {x['s'][k] - m['s'][k]:+.2f} |")
    for label, k in (("보여준 총 개수", "shown"), ("label 2 보여줌", "good"), ("label 0 보여줌", "bad"), ("F7 0개 반환", "f7")):
        p(f"| {label} | {m[k]} | {x[k]} | {x[k] - m[k]:+d} |")
    for c in CASES:
        p(f"| {c} | {m['case_shown'][c]}/{m['case_total'][c]} | {x['case_shown'][c]}/{x['case_total'][c]} | {x['case_shown'][c] - m['case_shown'][c]:+d} |")
    p(f"| label 일치 | – | {agree}/157 | – |")
    p(f"| 1쌍 latency (median) | Mac 측정은 별도 | {statistics.median(wall) / 1000:.2f}s | – |")
    p(f"| peak RSS | – | {hwm:.0f} MB | – |")
    p("\n(e5 Top5: Good@5 {:.2f} · Bad% {:.2f} · label 2 {} · label 0 {} · F7 {}/4)".format(
        res["e5 Top5"]["s"][keys[0][1]], res["e5 Top5"]["s"][keys[3][1]], res["e5 Top5"]["good"], res["e5 Top5"]["bad"], res["e5 Top5"]["f7"]))

    body = "\n".join(out) + "\n"
    if Path(a.out).exists() and Path(a.out).read_text(encoding="utf-8") != body:
        raise SystemExit(f"{a.out}가 이미 있고 내용이 다름 — 덮어쓰지 않는다.")
    Path(a.out).write_text(body, encoding="utf-8")
    with open(a.csv, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["pair_id", "case", "truth", "mac_label", "android_label", "status", "wall_ms", "prompt_n", "prompt_ms", "predicted_n", "predicted_ms", "vm_hwm_mb"])
        for r in recs:
            c = truth[r["cid"]]
            w.writerow([r["cid"], c["case"], c["label"], mac.get(r["cid"]), r["label"], r["status"], r["wall_ms"], r.get("prompt_n"),
                        r.get("prompt_ms"), r.get("predicted_n"), r.get("predicted_ms"), (r.get("mem") or {}).get("VmHWM")])
    print(body)
    print(f"wrote {a.out} · {a.csv} · {a.converted}")
    return 0


if __name__ == "__main__":
    main()
