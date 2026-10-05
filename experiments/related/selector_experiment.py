#!/usr/bin/env python3
"""
오늘문득 M5-2b: e5 순위 + judge_v1 판정(이미 저장된 것)을 조합하는 discrete selector 비교. 새 LLM 호출 없음.

  python3 selector_experiment.py --out results-m5-2b-selector.md [--notes notes.md]

입력(읽기만 함): dataset.json (v1.1), runs/e5-small-ko.v1.1.json, runs/llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl (1회차)
가중합 / 연속 점수 없음. 정책은 모두 "e5 순서 + judge label 0/1/2"만 쓰는 규칙이다.
"""
import argparse
import json
from math import log2
from pathlib import Path

import eval as ev
from llm_report import esc, read_log

HERE = Path(__file__).resolve().parent
TOP = 5            # e5 후보 상위 구간 (현재 baseline Top5)
CAPS = (1, 2, 3, 5)
POS_CASES = ["resolve_action", "worry_outcome", "implicit_link", "recurring", "change", "reversal"]


# ── selector 정책 (순수 함수: e5 순서 리스트, {cid: label}, cap → 선택 리스트) ─────────────────
def e5_top5(order, lab, cap):
    """A. e5 Top5 그대로 (cap만 적용)."""
    return order[:TOP][:cap]


def judge2_only(order, lab, cap):
    """B. e5 전체 후보 중 judge label 2만, e5 순서."""
    return [c for c in order if lab[c] == 2][:cap]


def remove_0(order, lab, cap):
    """C. e5 Top5에서 judge label 0만 제거, 보충 없음. (F. top5_keep_2_1과 동일한 정책)"""
    return [c for c in order[:TOP] if lab[c] != 0][:cap]


def remove_0_refill_2(order, lab, cap):
    """D. C 뒤에, Top5 밖의 judge label 2를 e5 순서로 보충."""
    kept = [c for c in order[:TOP] if lab[c] != 0]
    refill = [c for c in order[TOP:] if lab[c] == 2]
    return (kept + refill)[:cap]


def label2_then_1(order, lab, cap):
    """E. e5 전체 중 label 2를 e5 순서로 먼저, 부족하면 label 1을 e5 순서로."""
    return ([c for c in order if lab[c] == 2] + [c for c in order if lab[c] == 1])[:cap]


POLICIES = [
    ("A e5_top5", e5_top5),
    ("B judge2_only", judge2_only),
    ("C remove_0 (= F top5_keep_2_1)", remove_0),
    ("D remove_0_refill_2", remove_0_refill_2),
    ("E label2_then_1", label2_then_1),
]


# ── 평가 ─────────────────────────────────────────────────────────────────────
def evaluate(ds, sel, cap):
    """eval.compute (Good@5 · nDCG@5 · Worth% · Bad% · Top1=0 · Quiet miss) + cap 기준 지표 + 개수."""
    r = ev.compute(ds, sel)
    s = dict(r["summary"])
    good_cap, ndcg_cap = [], []
    shown = good = bad = 0
    for q in ds["queries"]:
        lab = {c["id"]: c["label"] for c in q["candidates"]}
        ids = sel[q["id"]]
        labels = [lab[i] for i in ids]
        shown += len(ids)
        good += labels.count(2)
        bad += labels.count(0)
        n2 = sum(1 for v in lab.values() if v == 2)
        if n2:
            good_cap.append(labels.count(2) / min(cap, n2))
            ideal = ev.dcg(sorted(lab.values(), reverse=True)[:cap])
            ndcg_cap.append(ev.dcg(labels) / ideal if ideal else 0.0)
    nolab2 = [q for q in ds["queries"] if not any(c["label"] == 2 for c in q["candidates"])]
    return {
        "s": s, "rows": r["rows"], "case_shown": r["case_shown"], "case_total": r["case_total"],
        "good_cap": sum(good_cap) / len(good_cap), "ndcg_cap": sum(ndcg_cap) / len(ndcg_cap),
        "shown": shown, "good": good, "bad": bad,
        "f7": sum(not sel[q["id"]] for q in nolab2), "f7_total": len(nolab2),
    }


def pareto(points):
    """(good ↑, bad ↓) 기준으로 지배되지 않는 점들. points: [(key, good, bad)]"""
    front = []
    for k, g, b in points:
        if not any((g2 >= g and b2 <= b) and (g2 > g or b2 < b) for _, g2, b2 in points):
            front.append(k)
    return front


def load_inputs(dataset, e5_path, log_path):
    ds = ev.load(dataset)
    assert not ev.check(ds), ev.check(ds)
    e5 = json.loads(Path(e5_path).read_text(encoding="utf-8"))
    order = {qid: [x["id"] for x in lst] for qid, lst in e5["rankings"].items()}
    meta, latest = read_log(log_path)
    lab = {cid: r["label"] for (cid, at), r in latest.items() if at == 1 and r["status"] == "ok"}
    all_ids = [c["id"] for q in ds["queries"] for c in q["candidates"]]
    missing = [c for c in all_ids if c not in lab]
    if missing:
        raise SystemExit(f"judge_v1 1회차 판정이 없는 pair {len(missing)}개: {missing[:5]}…")
    for q in ds["queries"]:
        assert set(order[q["id"]]) == {c["id"] for c in q["candidates"]}, q["id"]
    return ds, order, lab, meta


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--judge", default=HERE / "runs" / "llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl")
    ap.add_argument("--notes", help="해석(사람이 쓴 글)을 넣을 markdown 파일")
    ap.add_argument("--out")
    a = ap.parse_args()
    ds, order, lab, meta = load_inputs(a.dataset, a.e5, a.judge)
    qs = ds["queries"]

    results = {}
    for name, fn in POLICIES:
        for cap in CAPS:
            sel = {q["id"]: fn(order[q["id"]], lab, cap) for q in qs}
            results[(name, cap)] = (sel, evaluate(ds, sel, cap))
    total2 = sum(c["label"] == 2 for q in qs for c in q["candidates"])
    total0 = sum(c["label"] == 0 for q in qs for c in q["candidates"])

    out = []
    p = out.append
    p("# M5-2b 결과 · selector 비교 (e5 순서 + judge_v1 판정)\n")
    p("> ⚠️ **이 실험 역시 dataset v1.1을 사용한 development experiment이며, 최종 정책을 이 데이터에 맞춰 선택하면 낙관적일 수 있다. "
      "선택된 정책은 별도 hold-out에서 검증해야 한다.**\n")
    p(f"- 입력: dataset {ds.get('version')} (frozen) · `{Path(a.e5).name}` · `{Path(a.judge).name}` 1회차 "
      f"(model `{meta['model']}`, prompt `{meta['prompt_file']}` {meta['prompt_sha']}). 새 LLM 호출 없음. judge_v2 미사용.")
    p("- 정책은 모두 e5 순서와 judge_v1 label(0/1/2)만 쓰는 discrete 규칙. 가중합·연속 점수 없음.")
    p("- C `remove_0`과 F `top5_keep_2_1`은 정의가 같다 (Top5 안에서 judge 0만 제거, 보충 없음) → 하나만 계산.")
    p(f"- 평가셋: 17 queries · 157 pairs · label 2 {total2}개 · label 0 {total0}개 · label 2 없는 query 4개 (q02, q15, q16, q17).\n")

    p("## 지표 읽는 법\n")
    p("- **Good@5**: eval.py 그대로 (Top5에 들어온 2 / min(5, query의 2 개수)). cap이 작으면 구조적으로 낮아진다 — cap 간 비교용.")
    p("- **Good@cap**: 분모를 min(cap, query의 2 개수)로 바꾼 것 — 같은 cap 안에서 비교용.")
    p("- **2 보여줌 / 0 보여줌**: 17 query 전체에서 실제로 보여준 label 2·label 0의 개수. precision 착시 없이 \"좋은 걸 얼마나 남겼나 / 나쁜 걸 얼마나 보여줬나\"를 본다.")
    p("- nDCG@5는 eval.py 그대로, Worth% · Bad% · Top1=0 · Quiet miss도 eval.py 그대로.\n")

    p("## 1. 전체 selector × cap\n")
    p("| 정책 | cap | 보여준 총 개수 | 2 보여줌 | 0 보여줌 | Good@5 | Good@cap | nDCG@5 | Worth% | Bad% | Top1=0 | Quiet miss | F7 0개 반환 |")
    p("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for (name, cap), (sel, r) in results.items():
        s = r["s"]
        p(f"| {name} | {cap} | {r['shown']} | {r['good']}/{total2} | {r['bad']} | {s['Good@5 (2 회수율, query 평균)']:.2f} | {r['good_cap']:.2f} | "
          f"{s['nDCG@5 (gain 2→3, 1→1)']:.2f} | {s['Worth% (보여준 것 중 2 비율)']:.2f} | {s['Bad% (보여준 것 중 0 비율, 전체)']:.2f} | "
          f"{s['Top1=0 (1위에 0을 올린 query 비율)']:.2f} | {s['Quiet miss (2 없는 query에서 보여준 개수 평균)']:.2f} | {r['f7']}/{r['f7_total']} |")

    p("\n## 2. 실패 유형별 (유입 = 보여준 수 / 전체, 회수 = 보여준 2 / 전체)\n")
    cols = ["repeat_low_value", "lexical_trap", *POS_CASES]
    p("| 정책 | cap | F1 repeat 유입 | F2 lexical 유입 | F3 resolve | F4 worry | F5 implicit | F6 recurring | change | reversal | F7 |")
    p("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for (name, cap), (sel, r) in results.items():
        p(f"| {name} | {cap} | " + " | ".join(f"{r['case_shown'][c]}/{r['case_total'][c]}" for c in cols) + f" | {r['f7']}/{r['f7_total']} |")

    pts = [(k, r["good"], r["bad"]) for k, (sel, r) in results.items()]
    front = pareto(pts)
    p("\n## 3. Pareto (2 보여줌 ↑, 0 보여줌 ↓)\n")
    p("\"좋은 연결을 더 많이 남기면서 나쁜 연결을 더 적게 보여주는\" 다른 조합이 없는 점들. 하나의 winner를 고르지 않는다.\n")
    p("| 정책 | cap | 2 보여줌 | 0 보여줌 | 보여준 총 개수 | Bad% | Top1=0 | F7 0개 반환 |")
    p("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for k in sorted(front, key=lambda k: -results[k][1]["good"]):
        r = results[k][1]
        p(f"| {k[0]} | {k[1]} | {r['good']}/{total2} | {r['bad']} | {r['shown']} | {r['s']['Bad% (보여준 것 중 0 비율, 전체)']:.2f} | "
          f"{r['s']['Top1=0 (1위에 0을 올린 query 비율)']:.2f} | {r['f7']}/{r['f7_total']} |")

    # 정답 0개 query
    nolab2 = [q for q in qs if not any(c["label"] == 2 for c in q["candidates"])]
    p("\n## 4. label 2가 없는 query별 반환 개수\n")
    hdr = [(n, c) for n, _ in POLICIES for c in (3, 5)]
    p("| query | 현재 기록 | " + " | ".join(f"{n.split()[0]}@{c}" for n, c in hdr) + " |")
    p("| --- | --- |" + " --- |" * len(hdr))
    for q in nolab2:
        p(f"| {q['id']} | {esc(q['text'])} | " + " | ".join(str(len(results[(n, c)][0][q['id']])) for n, c in hdr) + " |")
    p("\n(A=e5_top5, B=judge2_only, C=remove_0, D=remove_0_refill_2, E=label2_then_1)")

    # 집중 분석: D@3 vs A@5 vs B@5
    focus = [("A e5_top5", 5), ("B judge2_only", 5), ("D remove_0_refill_2", 3), ("D remove_0_refill_2", 5)]
    p("\n## 5. 집중 분석: e5 Top5 → judge 0 제거 → Top5 밖 label 2 보충 → cap 3\n")
    p("query별로 실제로 보여준 후보의 정답 label (e5 순서). `·`는 0개.\n")
    p("| query | " + " | ".join(f"{n.split()[0]}@{c}" for n, c in focus) + " |")
    p("| --- |" + " --- |" * len(focus))
    for q in qs:
        labd = {c["id"]: c["label"] for c in q["candidates"]}
        cells = []
        for k in focus:
            ids = results[k][0][q["id"]]
            cells.append(" ".join(f"{i.split('-')[1]}({labd[i]})" for i in ids) or "·")
        p(f"| {q['id']} | " + " | ".join(cells) + " |")

    if a.notes and Path(a.notes).exists():
        p("\n" + Path(a.notes).read_text(encoding="utf-8").strip())

    body = "\n".join(out) + "\n"
    if a.out:
        Path(a.out).write_text(body, encoding="utf-8")
        print(f"wrote {a.out}")
    else:
        print(body)


if __name__ == "__main__":
    main()
