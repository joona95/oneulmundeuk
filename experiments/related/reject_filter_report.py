#!/usr/bin/env python3
"""
오늘문득 M5-2e: reject filter 결과 vs e5 Top5 / judge_v1 비교 리포트. 모델을 호출하지 않는다.

  python3 reject_filter_report.py --out results-m5-2e-reject-filter.md

선택 규칙
  e5 Top5          : e5 순서 상위 5개
  judge_v1         : e5 순서에서 judge_v1 label 2만, 최대 5개 (M5-2b judge2_only@5와 같음)
  reject_filter_v1 : e5 Top10 중 keep=true만 (e5 순서 유지), 최대 5개. 판정 실패(error)는 keep으로 본다.
지표는 eval.compute 그대로 (selector_experiment.evaluate).
"""
import argparse
import json
from collections import Counter
from pathlib import Path

import reject_filter as R
import selector_experiment as S
from llm_report import esc

HERE = Path(__file__).resolve().parent
NOLAB2 = ("q02", "q15", "q16", "q17")
CASES = ["repeat_low_value", "lexical_trap", "resolve_action", "worry_outcome", "implicit_link", "recurring", "change", "reversal"]
CASE_GOAL = {"repeat_low_value": "유입↓", "lexical_trap": "유입↓"}


def filter_select(order, keep, top_n=R.TOP_N, cap=5):
    """e5 Top N 중 keep(또는 실패)인 후보, e5 순서 유지, 최대 cap개."""
    return [c for c in order[:top_n] if keep.get(c, True) is not False][:cap]


def load_filter(path, ds, order):
    meta, latest = R.read_log(path)
    if meta is None:
        raise SystemExit(f"{path}: 기록 없음")
    keep, status = {}, {}
    for q in ds["queries"]:
        for cid in order[q["id"]][:R.TOP_N]:
            r = latest.get((cid, R.ATTEMPT))
            status[cid] = "missing" if r is None else r["status"]
            keep[cid] = r["keep"] if r and r["status"] == "ok" else None
    return meta, keep, status


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--log", default=HERE / "runs" / "reject-qwen3.5-2b-q4_K_M-reject_filter_v1.jsonl")
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--judge", default=HERE / "runs" / "llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl")
    ap.add_argument("--out")
    a = ap.parse_args(argv)

    ds, order, jlab, _ = S.load_inputs(a.dataset, a.e5, a.judge)
    qs = ds["queries"]
    meta, keep, status = load_filter(Path(a.log), ds, order)
    cmap = {c["id"]: (q, c) for q in qs for c in q["candidates"]}
    sel = {
        "e5 Top5": {q["id"]: S.e5_top5(order[q["id"]], jlab, 5) for q in qs},
        "judge_v1": {q["id"]: S.judge2_only(order[q["id"]], jlab, 5) for q in qs},
        "reject_filter_v1": {q["id"]: filter_select(order[q["id"]], keep) for q in qs},
    }
    names = list(sel)
    res = {n: S.evaluate(ds, sel[n], 5) for n in names}
    total2 = sum(c["label"] == 2 for q in qs for c in q["candidates"])
    judged = [cid for cid in keep]
    n_err = sum(status[c] != "ok" for c in judged)

    out = []
    p = out.append
    p("# M5-2e 결과 · Qwen 2B conservative reject filter\n")
    p("> ⚠️ **dataset v1.1은 여러 실험에 이미 쓴 development benchmark다.** 이 결과는 구조 확인용이며 최종 일반화 성능으로 해석하지 않는다. "
      "prompt는 결과를 보기 전에 고정했고, 1회만 실행했다.\n")
    p(f"- filter: `{Path(a.log).name}` · model `{meta['model']}` · prompt `{meta['prompt_file']}` ({meta['prompt_sha']}) · "
      f"options `{json.dumps(meta['options'], ensure_ascii=False)}` · e5 Top{meta['top_n']}")
    p(f"- 판정 {len(judged)}쌍 · ok {len(judged) - n_err} · 실패/미완료 {n_err}" + (" (**실패는 keep으로 계산**)" if n_err else ""))
    p("- e5 Top5 / judge_v1은 저장된 결과로 다시 계산 (새 호출 없음).\n")

    p("## 1. 전체 지표 (최대 5개 노출)\n")
    p("| metric | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- |" + " --- |" * len(names))
    rows = [("Good@5", "Good@5 (2 회수율, query 평균)"), ("nDCG@5", "nDCG@5 (gain 2→3, 1→1)"), ("Worth%", "Worth% (보여준 것 중 2 비율)"),
            ("Bad%", "Bad% (보여준 것 중 0 비율, 전체)"), ("Top1=0", "Top1=0 (1위에 0을 올린 query 비율)"),
            ("Quiet miss", "Quiet miss (2 없는 query에서 보여준 개수 평균)")]
    for label, k in rows:
        p(f"| {label} | " + " | ".join(f"{res[n]['s'][k]:.2f}" for n in names) + " |")
    p("| 보여준 총 개수 | " + " | ".join(str(res[n]["shown"]) for n in names) + " |")
    p(f"| label 2 보여줌 (/{total2}) | " + " | ".join(str(res[n]["good"]) for n in names) + " |")
    p("| label 0 보여줌 | " + " | ".join(str(res[n]["bad"]) for n in names) + " |")
    p("| F7 0개 반환 | " + " | ".join(f"{res[n]['f7']}/{res[n]['f7_total']}" for n in names) + " |")

    p("\n## 2. case별 (보여준 수 / 전체)\n")
    p("| case | 목표 | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- | --- |" + " --- |" * len(names))
    for c in CASES:
        p(f"| {c} | {CASE_GOAL.get(c, '회수↑')} | " + " | ".join(f"{res[n]['case_shown'][c]}/{res[n]['case_total'][c]}" for n in names) + " |")

    p("\n## 3. F7 · label 2가 없는 query\n")
    p("| query | 현재 기록 | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- | --- |" + " --- |" * len(names))
    qmap = {q["id"]: q for q in qs}
    for qid in NOLAB2:
        lab = {c["id"]: c["label"] for c in qmap[qid]["candidates"]}
        p(f"| {qid} | {esc(qmap[qid]['text'])} | " + " | ".join(
            "**0개 ✓**" if not sel[n][qid] else f"{len(sel[n][qid])}개 [" + ",".join(str(lab[i]) for i in sel[n][qid]) + "]" for n in names) + " |")

    p("\n## 4. filter 동작 · 정답 label별 keep / reject\n")
    for title, ids in (("e5 Top10 전체", judged), ("그중 e5 Top5", [c for q in qs for c in order[q["id"]][:5]])):
        cnt = Counter((cmap[c][1]["label"], "keep" if keep[c] is True else "reject" if keep[c] is False else "error") for c in ids)
        p(f"\n{title} ({len(ids)}쌍)\n")
        p("| 정답 label | 전체 | keep | reject | 실패 | reject 비율 |")
        p("| --- | --- | --- | --- | --- | --- |")
        for g in (2, 1, 0):
            tot = sum(cnt[(g, k)] for k in ("keep", "reject", "error"))
            p(f"| **{g}** | {tot} | {cnt[(g, 'keep')]} | {cnt[(g, 'reject')]} | {cnt[(g, 'error')]} | "
              f"{cnt[(g, 'reject')] / tot:.2f} |" if tot else f"| **{g}** | 0 | – | – | – | – |")
    p("\ncase별 reject (e5 Top10)\n")
    p("| case | 정답 | 전체 | reject |")
    p("| --- | --- | --- | --- |")
    for c in CASES + ["ambiguous", "unrelated"]:
        ids = [i for i in judged if cmap[i][1].get("case") == c]
        if ids:
            p(f"| {c} | {cmap[ids[0]][1]['label']} | {len(ids)} | {sum(keep[i] is False for i in ids)} |")

    p("\n### reject된 label 2 (놓친 좋은 기록)\n")
    lost = [c for c in judged if cmap[c][1]["label"] == 2 and keep[c] is False]
    if not lost:
        p("- 없음")
    else:
        p("| pair | case | e5 순위 | 현재 기록 | 과거 기록 |")
        p("| --- | --- | --- | --- | --- |")
        for c in lost:
            q, cand = cmap[c]
            p(f"| {c} | {cand.get('case')} | {order[q['id']].index(c) + 1} | {esc(q['text'])} | {esc(cand['text'])} |")

    p("\n## 5. 출력 진단 (점수에 쓰지 않음)\n")
    _, latest = R.read_log(Path(a.log))
    recs = list(latest.values())
    errs = Counter((r["error"] or "").split(":")[0] for r in recs if r["status"] != "ok")
    p(f"- keep 비율: {sum(keep[c] is True for c in judged)}/{len(judged)}")
    if errs:
        p("- 실패 유형: " + ", ".join(f"{k} {v}" for k, v in errs.items()))
    extra = sum(bool(r.get("extra_keys")) for r in recs if r["status"] == "ok")
    p(f"- keep 외 key를 함께 출력한 응답: {extra}")
    pec = [r["prompt_eval_count"] for r in recs if r.get("prompt_eval_count")]
    if pec:
        p(f"- prompt_eval_count: 최소 {min(pec)} · 최대 {max(pec)} (num_ctx {meta['options']['num_ctx']})")
    reload_ = sum((r.get("load_duration_ms") or 0) > 1000 for r in recs[1:])
    p(f"- 첫 호출 이후 모델 재로드: {reload_}회")

    body = "\n".join(out) + "\n"
    if a.out:
        if Path(a.out).exists() and Path(a.out).read_text(encoding="utf-8") != body:
            raise SystemExit(f"{a.out}가 이미 있음 — 덮어쓰지 않는다.")
        Path(a.out).write_text(body, encoding="utf-8")
        print(f"wrote {a.out}")
    else:
        print(body)


if __name__ == "__main__":
    main()
