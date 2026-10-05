#!/usr/bin/env python3
"""
오늘문득 M5-2c: listwise 결과 vs 기존 baseline 비교 리포트. 모델을 호출하지 않는다.

  python3 listwise_report.py --out results-m5-2c-listwise.md

baseline은 저장된 결과로 다시 계산한다: e5 Top5/Top3, judge_v1(judge2_only) cap5/cap3, M5-2b E(label2_then_1) cap5/cap3.
listwise는 최대 3개라서 같은 cap 3 baseline과 함께 본다.
"""
import argparse
import json
from collections import Counter
from pathlib import Path

import eval as ev
import listwise_select as L
import selector_experiment as S
from llm_report import esc

HERE = Path(__file__).resolve().parent
NOLAB2 = ("q02", "q15", "q16", "q17")
CASES = ["repeat_low_value", "lexical_trap", "resolve_action", "worry_outcome", "implicit_link", "recurring", "change", "reversal"]


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--log", default=HERE / "runs" / "listwise-qwen3.5-2b-q4_K_M-listwise_v1.jsonl")
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--judge", default=HERE / "runs" / "llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl")
    ap.add_argument("--out")
    a = ap.parse_args(argv)

    ds, order, jlab, _ = S.load_inputs(a.dataset, a.e5, a.judge)
    qs = ds["queries"]
    meta, latest = L.read_log(Path(a.log))
    lw = {q["id"]: (latest[q["id"]]["selected_ids"] if latest.get(q["id"], {}).get("status") == "ok" else []) for q in qs}
    failed = [qid for qid in (q["id"] for q in qs) if latest.get(qid, {}).get("status") != "ok"]

    systems = []
    for name, fn, cap in [("e5 Top5", S.e5_top5, 5), ("e5 Top3", S.e5_top5, 3),
                          ("judge_v1 all@5", S.judge2_only, 5), ("judge_v1 @3", S.judge2_only, 3),
                          ("2b E@5", S.label2_then_1, 5), ("2b E@3", S.label2_then_1, 3)]:
        systems.append((name, cap, {q["id"]: fn(order[q["id"]], jlab, cap) for q in qs}))
    systems.append(("listwise_v1", 3, lw))
    res = {n: S.evaluate(ds, sel, cap) for n, cap, sel in systems}
    sel = {n: s for n, _, s in systems}
    names = [n for n, _, _ in systems]
    total2 = sum(c["label"] == 2 for q in qs for c in q["candidates"])

    out = []
    p = out.append
    p("# M5-2c 결과 · listwise selection\n")
    p("> ⚠️ **dataset v1.1은 여러 실험에 이미 쓴 development benchmark다. 이 결과는 구조 선택용이며, 좋아 보여도 최종 일반화 성능으로 "
      "해석하지 않는다. 채택하면 별도 hold-out으로 확인한다.**\n")
    p(f"- listwise: `{Path(a.log).name}` · model `{meta['model']}` · prompt `{meta['prompt_file']}` ({meta['prompt_sha']}) · "
      f"options `{json.dumps(meta['options'], ensure_ascii=False)}` · e5 Top{meta['top_n']} · 후보 순서 shuffle(`{meta['shuffle_salt']}`)")
    p(f"- 완료 {len(qs) - len(failed)}/{len(qs)} query" + (f" · **실패 {failed} (빈 선택으로 계산됨)**" if failed else ""))
    p("- baseline은 저장된 e5 / judge_v1 결과에서 다시 계산 (새 호출 없음). listwise는 최대 3개 → cap 3 baseline과 같이 본다.\n")

    p("## 1. 전체 지표\n")
    p("| metric | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- |" + " --- |" * len(names))
    rows = [("보여준 총 개수", lambda r: str(r["shown"])), ("label 2 보여줌", lambda r: f"{r['good']}/{total2}"),
            ("label 0 보여줌", lambda r: str(r["bad"])), ("Good@5", lambda r: f"{r['s']['Good@5 (2 회수율, query 평균)']:.2f}"),
            ("Good@cap", lambda r: f"{r['good_cap']:.2f}"), ("Worth%", lambda r: f"{r['s']['Worth% (보여준 것 중 2 비율)']:.2f}"),
            ("Bad%", lambda r: f"{r['s']['Bad% (보여준 것 중 0 비율, 전체)']:.2f}"),
            ("Top1=0", lambda r: f"{r['s']['Top1=0 (1위에 0을 올린 query 비율)']:.2f}"),
            ("Quiet miss", lambda r: f"{r['s']['Quiet miss (2 없는 query에서 보여준 개수 평균)']:.2f}"),
            ("F7 0개 반환", lambda r: f"{r['f7']}/{r['f7_total']}")]
    for label, f in rows:
        p(f"| {label} | " + " | ".join(f(res[n]) for n in names) + " |")

    p("\n## 2. case별 (유입·회수 = 보여준 수 / 전체)\n")
    p("| case | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- |" + " --- |" * len(names))
    for c in CASES:
        p(f"| {c} | " + " | ".join(f"{res[n]['case_shown'][c]}/{res[n]['case_total'][c]}" for n in names) + " |")

    p("\n## 3. F7 · label 2가 없는 query\n")
    p("| query | 현재 기록 | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- | --- |" + " --- |" * len(names))
    qmap = {q["id"]: q for q in qs}
    for qid in NOLAB2:
        p(f"| {qid} | {esc(qmap[qid]['text'])} | " + " | ".join("**[] ✓**" if not sel[n][qid] else str(len(sel[n][qid])) for n in names) + " |")

    p("\n## 4. query별 listwise 선택 (정답 label · e5 순위 · prompt 글자)\n")
    p("| query | 선택 | 정답 2 개수 |")
    p("| --- | --- | --- |")
    for q in qs:
        lab = {c["id"]: c["label"] for c in q["candidates"]}
        r = latest.get(q["id"], {})
        if r.get("status") != "ok":
            p(f"| {q['id']} | **실패** {esc(r.get('error') or '기록 없음')} | {sum(v == 2 for v in lab.values())} |")
            continue
        inv = {cid: Lt for Lt, cid in r["letters"].items()}
        cells = " ".join(f"{cid.split('-')[1]}[{lab[cid]}] e5#{r['e5_top'].index(cid) + 1} {inv[cid]}" for cid in r["selected_ids"]) or "[]"
        p(f"| {q['id']} | {cells} | {sum(v == 2 for v in lab.values())} |")

    p("\n## 5. 지시 이행 진단 (점수에 쓰지 않음)\n")
    recs = [latest[q["id"]] for q in qs if q["id"] in latest]
    ok = [r for r in recs if r["status"] == "ok"]
    p(f"- 유효한 출력: {len(ok)}/{len(qs)}")
    p("- 선택 개수 분포: " + ", ".join(f"{k}개 {v}" for k, v in sorted(Counter(len(r['selected_ids']) for r in ok).items())))
    errs = Counter(r["error"].split(":")[0] for r in recs if r["status"] != "ok")
    if errs:
        p("- 실패 유형: " + ", ".join(f"{k} {v}" for k, v in errs.items()))
    pec = [r.get("prompt_eval_count") for r in recs if r.get("prompt_eval_count")]
    if pec:
        p(f"- prompt_eval_count: 최소 {min(pec)} · 최대 {max(pec)} (num_ctx {meta['options']['num_ctx']})")
    pos = Counter(r["e5_top"].index(cid) + 1 for r in ok for cid in r["selected_ids"])
    p("- 선택된 후보의 e5 순위 분포: " + ", ".join(f"#{k} {v}" for k, v in sorted(pos.items())))
    ppos = Counter(list(r["letters"]).index(Lt) + 1 for r in ok for Lt in r["selected_letters"])
    p("- 선택된 후보의 prompt 위치(A=1) 분포: " + ", ".join(f"{k} {v}" for k, v in sorted(ppos.items())))

    body = "\n".join(out) + "\n"
    if a.out:
        Path(a.out).write_text(body, encoding="utf-8")
        print(f"wrote {a.out}")
    else:
        print(body)


if __name__ == "__main__":
    main()
