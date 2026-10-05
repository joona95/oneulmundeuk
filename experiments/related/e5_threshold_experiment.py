#!/usr/bin/env python3
"""
오늘문득 M5-3: e5 similarity threshold 실험. 모델 호출 · embedding 재계산 없음. stdlib만 사용.

  python3 e5_threshold_experiment.py --out results-m5-3-e5-threshold.md --csv results-m5-3-e5-threshold-sweep.csv

입력(읽기만): dataset.json (v1.1), runs/e5-small-ko.v1.1.json (저장된 cosine score)
정책: score >= threshold인 후보만, 기존 e5 순서(score 내림차순) 그대로, 최대 N개. 넘는 후보가 없으면 0개.

사전 고정(결과를 보기 전에 정함):
  - grid: 0.01 간격, 저장된 score 범위를 덮는 [floor(min,0.01), ceil(max,0.01)] 전체 × N ∈ {1, 3, 5}
  - 비교는 정수(만분율)로 한다: score는 소수 4자리로 저장돼 있어 float 오차 없이 >= 비교.
  - MVP 후보 규칙: 설명 가능한 값만 쓰도록 threshold는 0.05 배수로 한정.
      A recall 우선  : e5 Top5가 보여주는 label 2 수의 90% 이상 유지
      B balanced     : 75% 이상 유지
      C precision 우선: 50% 이상 유지
    각 조건을 만족하는 (0.05 배수 threshold, N) 중 label 0 노출이 가장 적은 것.
    동률이면 보여준 총 개수가 적은 것 → threshold가 낮은 것 → N이 큰 것.
지표는 eval.compute(selector_experiment.evaluate) 정의 그대로. Good@5 / nDCG@5는 K=5 기준이라 N<5에서는 상한이 낮다 → Good@N도 함께 낸다.
"""
import argparse
import csv
import json
import math
from collections import Counter
from pathlib import Path

import eval as ev
import selector_experiment as S
from llm_report import esc

HERE = Path(__file__).resolve().parent
CAPS = (1, 3, 5)
STEP = 100  # 정수 단위: 1 = 0.0001, 100 = 0.01
NOLAB2 = ("q02", "q15", "q16", "q17")
NOISE_CASES = ["repeat_low_value", "lexical_trap", "unrelated"]
KEEP_CASES = ["resolve_action", "worry_outcome", "implicit_link", "recurring", "change", "reversal"]
RETENTION = (("A", "recall 우선", 0.90), ("B", "balanced", 0.75), ("C", "precision 우선", 0.50))
K_SUMMARY = {"Good@5": "Good@5 (2 회수율, query 평균)", "nDCG@5": "nDCG@5 (gain 2→3, 1→1)", "Worth%": "Worth% (보여준 것 중 2 비율)",
             "Bad%": "Bad% (보여준 것 중 0 비율, 전체)", "Top1=0": "Top1=0 (1위에 0을 올린 query 비율)",
             "Quiet miss": "Quiet miss (2 없는 query에서 보여준 개수 평균)"}


def to_int(x):
    return int(round(x * 10000))


def load(dataset, e5_path):
    ds = ev.load(dataset)
    assert not ev.check(ds), ev.check(ds)
    e5 = json.loads(Path(e5_path).read_text(encoding="utf-8"))
    ranked = {}
    for q in ds["queries"]:
        lst = e5["rankings"][q["id"]]
        assert {x["id"] for x in lst} == {c["id"] for c in q["candidates"]}, q["id"]
        sc = [to_int(x["score"]) for x in lst]
        assert sc == sorted(sc, reverse=True), f"{q['id']}: e5 순서가 score 내림차순이 아님"
        ranked[q["id"]] = [(x["id"], to_int(x["score"])) for x in lst]
    return ds, e5, ranked


def grid(ranked):
    scores = [s for lst in ranked.values() for _, s in lst]
    lo, hi = (min(scores) // STEP) * STEP, -(-max(scores) // STEP) * STEP
    return list(range(lo, hi + 1, STEP))


def select(ranked, t, cap):
    return {qid: [cid for cid, s in lst if s >= t][:cap] for qid, lst in ranked.items()}


def quantile(xs, p):
    """선형 보간 (numpy 기본과 같음)."""
    xs = sorted(xs)
    k = (len(xs) - 1) * p
    f = math.floor(k)
    return xs[f] + (xs[min(f + 1, len(xs) - 1)] - xs[f]) * (k - f)


def distribution(ds, ranked):
    lab = {c["id"]: c["label"] for q in ds["queries"] for c in q["candidates"]}
    by = {g: [s / 10000 for lst in ranked.values() for cid, s in lst if lab[cid] == g] for g in (2, 1, 0)}
    stats = {g: {"count": len(v), "min": min(v), "p10": quantile(v, .1), "p25": quantile(v, .25), "median": quantile(v, .5),
                 "p75": quantile(v, .75), "p90": quantile(v, .9), "max": max(v), "mean": sum(v) / len(v)} for g, v in by.items()}
    # 겹침: AUC = P(label 2 score > label 0 score) (동점 0.5), 겹치는 구간
    two, zero = by[2], by[0]
    auc = sum((a > b) + 0.5 * (a == b) for a in two for b in zero) / (len(two) * len(zero))
    ov_lo, ov_hi = max(min(two), min(zero)), min(max(two), max(zero))
    overlap = {"auc_2_vs_0": auc, "range": (ov_lo, ov_hi),
               "two_in_overlap": sum(ov_lo <= x <= ov_hi for x in two), "zero_in_overlap": sum(ov_lo <= x <= ov_hi for x in zero),
               "zero_above_median2": sum(x >= stats[2]["median"] for x in zero),
               "two_below_p75_0": sum(x < stats[0]["p75"] for x in two)}
    # 같은 query 안에서의 순위 분리 (threshold와 무관한 참고값)
    within = []
    for lst in ranked.values():
        t = [s for cid, s in lst if lab[cid] == 2]
        z = [s for cid, s in lst if lab[cid] == 0]
        within += [(a > b) + 0.5 * (a == b) for a in t for b in z]
    overlap["auc_within_query"] = sum(within) / len(within)
    case = {}
    for q in ds["queries"]:
        for c in q["candidates"]:
            s = dict(ranked[q["id"]])[c["id"]] / 10000
            case.setdefault(c["case"], []).append(s)
    return by, stats, overlap, case


def evaluate_all(ds, ranked):
    out = []
    for t in grid(ranked):
        for cap in CAPS:
            sel = select(ranked, t, cap)
            r = S.evaluate(ds, sel, cap)
            lab = {c["id"]: c["label"] for q in ds["queries"] for c in q["candidates"]}
            out.append({"t": t, "cap": cap, "sel": sel, "r": r,
                        "one": sum(lab[c] == 1 for v in sel.values() for c in v),
                        "zero_q": sum(not v for v in sel.values()),
                        "key": tuple(tuple(sel[q]) for q in sorted(sel))})
    return out


def pareto(rows, objs):
    """objs: [(함수, +1 최대화 / -1 최소화)]. 같은 선택 결과는 하나로 본다."""
    uniq = {}
    for r in rows:
        uniq.setdefault(r["key"], r)
    pts = list(uniq.values())
    vec = lambda r: tuple(s * f(r) for f, s in objs)
    front = []
    for r in pts:
        v = vec(r)
        if not any(all(a >= b for a, b in zip(vec(o), v)) and vec(o) != v for o in pts):
            front.append(r)
    return sorted(front, key=lambda r: (-r["r"]["good"], r["r"]["bad"]))


def candidates(rows, e5_good):
    simple = [r for r in rows if r["t"] % 500 == 0]
    out = []
    for code, name, keep in RETENTION:
        ok = [r for r in simple if r["r"]["good"] >= keep * e5_good]
        best = min(ok, key=lambda r: (r["r"]["bad"], r["r"]["shown"], r["t"], -r["cap"])) if ok else None
        out.append((code, name, keep, best))
    return out


def fmt_t(t):
    return f"{t / 10000:.2f}"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--out")
    ap.add_argument("--csv")
    a = ap.parse_args(argv)
    for p in (a.out, a.csv):
        if p and Path(p).exists():
            raise SystemExit(f"{p}가 이미 있음 — 덮어쓰지 않는다.")

    ds, e5, ranked = load(a.dataset, a.e5)
    qs = ds["queries"]
    by, stats, ov, case_scores = distribution(ds, ranked)
    rows = evaluate_all(ds, ranked)
    base = S.evaluate(ds, {qid: [c for c, _ in lst][:5] for qid, lst in ranked.items()}, 5)
    total2 = sum(c["label"] == 2 for q in qs for c in q["candidates"])
    case_total = Counter(c["case"] for q in qs for c in q["candidates"])
    front = pareto(rows, [(lambda r: r["r"]["good"], 1), (lambda r: r["r"]["bad"], -1)])
    front3 = pareto(rows, [(lambda r: r["r"]["good"], 1), (lambda r: r["r"]["bad"], -1), (lambda r: r["r"]["s"][K_SUMMARY["Quiet miss"]], -1)])
    cands = candidates(rows, base["good"])
    g = grid(ranked)

    out = []
    p = out.append
    p("# M5-3 결과 · e5 similarity threshold\n")
    p("> ⚠️ **dataset v1.1은 여러 실험에 이미 쓴 development benchmark다.** 아래 threshold는 이 157쌍에서 본 값이며 \"최적 threshold\"가 아니다. "
      "MVP 값은 임시값이고, 실제 사용자 기록에서 다시 확인해야 한다. 157쌍 · 17 query라 한 query의 차이가 지표를 크게 움직인다.\n")
    p(f"- e5 run: `{Path(a.e5).name}` · model `{e5.get('model')}` · 저장된 cosine score만 사용 (재계산·모델 호출 없음)")
    p(f"- 정책: score ≥ threshold인 후보만, e5 순서(score 내림차순) 그대로, 최대 N개. 넘는 후보가 없으면 0개.")
    p(f"- grid (사전 고정): {fmt_t(g[0])} ~ {fmt_t(g[-1])}, 0.01 간격 {len(g)}개 × N ∈ {{1, 3, 5}} = {len(rows)}개 조합 전부 평가")
    p("- 지표는 eval.compute 정의 그대로. Good@5 · nDCG@5는 K=5 기준(N<5면 상한이 낮음) → Good@N도 함께 표시. "
      "Worth% · Top1=0은 1개 이상 보여준 query 평균, Bad%는 보여준 전체 중 0 비율.\n")

    p("## 1. similarity 분포 (정답 label별)\n")
    p("| label | count | min | p10 | p25 | median | p75 | p90 | max | mean |")
    p("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for gl in (2, 1, 0):
        s = stats[gl]
        p(f"| **{gl}** | {s['count']} | " + " | ".join(f"{s[k]:.3f}" for k in ("min", "p10", "p25", "median", "p75", "p90", "max", "mean")) + " |")
    p("\n**label 2 vs label 0 겹침**\n")
    p(f"- AUC P(score₂ > score₀), 전체 쌍 기준: **{ov['auc_2_vs_0']:.2f}** (0.5 = 구분 불가, 1.0 = 완전 분리) · 같은 query 안에서만 비교하면 {ov['auc_within_query']:.2f}")
    p(f"- 두 분포가 겹치는 구간 [{ov['range'][0]:.3f}, {ov['range'][1]:.3f}]: label 2 {ov['two_in_overlap']}/{stats[2]['count']}개, "
      f"label 0 {ov['zero_in_overlap']}/{stats[0]['count']}개가 이 안에 있다")
    p(f"- label 0 중 label 2 median({stats[2]['median']:.3f}) 이상: {ov['zero_above_median2']}/{stats[0]['count']} · "
      f"label 2 중 label 0 p75({stats[0]['p75']:.3f}) 미만: {ov['two_below_p75_0']}/{stats[2]['count']}")
    p("\n0.05 구간별 개수\n")
    lo = math.floor(min(min(v) for v in by.values()) * 20) / 20
    p("| score 구간 | label 2 | label 1 | label 0 |")
    p("| --- | --- | --- | --- |")
    b = lo
    while b < 0.9:
        cnt = [sum(to_int(b) <= to_int(x) < to_int(b + 0.05) for x in by[gl]) for gl in (2, 1, 0)]
        p(f"| {b:.2f}–{b + 0.05:.2f} | {cnt[0]} | {cnt[1]} | {cnt[2]} |")
        b = round(b + 0.05, 2)
    p("\ncase별 score (median · min–max)\n")
    p("| case | 정답 | n | median | min–max |")
    p("| --- | --- | --- | --- | --- |")
    for c in KEEP_CASES + ["ambiguous"] + NOISE_CASES:
        v = case_scores[c]
        p(f"| {c} | {next(iter(ev.CASE_LABELS[c]))} | {len(v)} | {quantile(v, .5):.3f} | {min(v):.3f}–{max(v):.3f} |")

    def metric_row(name, r):
        s = r["r"]["s"] if "r" in r else r["s"]
        rr = r["r"] if "r" in r else r
        return (f"| {name} | {rr['shown']} | {rr['shown'] / len(qs):.2f} | {rr['good']}/{total2} | {r.get('one', '–')} | {rr['bad']} | "
                f"{s[K_SUMMARY['Good@5']]:.2f} | {rr['good_cap']:.2f} | {s[K_SUMMARY['nDCG@5']]:.2f} | {s[K_SUMMARY['Worth%']]:.2f} | "
                f"{s[K_SUMMARY['Bad%']]:.2f} | {s[K_SUMMARY['Top1=0']]:.2f} | {s[K_SUMMARY['Quiet miss']]:.2f} | "
                f"{r.get('zero_q', sum(not x for x in []))} | {rr['f7']}/{rr['f7_total']} |")
    HEAD = ("| 정책 | 보여준 총 | 평균 노출 | label 2 | label 1 | label 0 | Good@5 | Good@N | nDCG@5 | Worth% | Bad% | Top1=0 | Quiet miss | 0개 query | F7 0개 |\n"
            "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")

    p("\n## 2. threshold sweep (전체 grid)\n")
    base_row = {"r": base, "one": sum(1 for q in qs for c in q["candidates"] if c["label"] == 1 and c["id"] in [x for x, _ in ranked[q["id"]][:5]]),
                "zero_q": 0}
    p(HEAD)
    p(metric_row("**e5 Top5 (threshold 없음)**", base_row))
    for cap in CAPS:
        for r in rows:
            if r["cap"] == cap:
                p(metric_row(f"t≥{fmt_t(r['t'])} · N={cap}", r))

    p("\n## 3. F7 · label 2가 없는 query의 노출 개수 (0.05 배수 threshold)\n")
    p("| 정책 | " + " | ".join(NOLAB2) + " |")
    p("| --- |" + " --- |" * len(NOLAB2))
    p("| e5 Top5 | " + " | ".join("5" for _ in NOLAB2) + " |")
    for cap in CAPS:
        for r in rows:
            if r["cap"] == cap and r["t"] % 500 == 0:
                p(f"| t≥{fmt_t(r['t'])} · N={cap} | " + " | ".join("**0 ✓**" if not r["sel"][q] else str(len(r["sel"][q])) for q in NOLAB2) + " |")

    p("\n## 4. case별 노출 (보여준 수 / 전체, 0.05 배수 threshold)\n")
    cs = NOISE_CASES + KEEP_CASES
    p("| 정책 | " + " | ".join(f"{c} {'↓' if c in NOISE_CASES else '↑'}" for c in cs) + " |")
    p("| --- |" + " --- |" * len(cs))
    p("| e5 Top5 | " + " | ".join(f"{base['case_shown'][c]}/{case_total[c]}" for c in cs) + " |")
    for cap in CAPS:
        for r in rows:
            if r["cap"] == cap and r["t"] % 500 == 0:
                p(f"| t≥{fmt_t(r['t'])} · N={cap} | " + " | ".join(f"{r['r']['case_shown'][c]}/{case_total[c]}" for c in cs) + " |")

    p("\n## 5. Pareto frontier\n")
    p("같은 선택 결과를 내는 조합은 하나로 묶었다 (가장 낮은 threshold · 작은 N이 대표).\n")
    p("**(a) label 2 노출 ↑ · label 0 노출 ↓**\n")
    p(HEAD)
    for r in front:
        p(metric_row(f"t≥{fmt_t(r['t'])} · N={r['cap']}", r))
    p("\n**(b) label 2 노출 ↑ · label 0 노출 ↓ · Quiet miss ↓** (F7 query에서 조용한 정도까지 포함)\n")
    p(f"- {len(front3)}개 조합. (a)에 없는 것: " + (", ".join(f"t≥{fmt_t(r['t'])}·N={r['cap']}" for r in front3 if r["key"] not in {x['key'] for x in front}) or "없음"))

    p("\n## 6. MVP 후보 정책 (사전 고정 규칙 · 0.05 배수 threshold만)\n")
    p(f"e5 Top5의 label 2 노출 {base['good']}개를 기준으로, 유지율 조건을 만족하는 조합 중 label 0 노출이 가장 적은 것.\n")
    p(HEAD)
    p(metric_row("e5 Top5", base_row))
    for code, name, keep, r in cands:
        p(metric_row(f"**{code}** {name} (≥{keep:.0%}) · t≥{fmt_t(r['t'])} · N={r['cap']}", r) if r else f"| {code} | 조건을 만족하는 조합 없음 |")
    p("\n후보별 case · F7\n")
    p("| 후보 | " + " | ".join(cs) + " | " + " | ".join(NOLAB2) + " |")
    p("| --- |" + " --- |" * (len(cs) + len(NOLAB2)))
    for code, name, keep, r in cands:
        if r:
            p(f"| {code} t≥{fmt_t(r['t'])}·N={r['cap']} | " + " | ".join(f"{r['r']['case_shown'][c]}/{case_total[c]}" for c in cs) + " | "
              + " | ".join(str(len(r["sel"][q])) for q in NOLAB2) + " |")

    body = "\n".join(out) + "\n"
    if a.out:
        Path(a.out).write_text(body, encoding="utf-8")
        print(f"wrote {a.out}")
    else:
        print(body)
    if a.csv:
        with open(a.csv, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["threshold", "N", "shown", "label2", "label1", "label0", "zero_queries", *K_SUMMARY, "Good@N",
                        *[f"F7_{q}" for q in NOLAB2], *[f"case_{c}" for c in cs]])
            for r in rows:
                s = r["r"]["s"]
                w.writerow([fmt_t(r["t"]), r["cap"], r["r"]["shown"], r["r"]["good"], r["one"], r["r"]["bad"], r["zero_q"],
                            *[f"{s[v]:.4f}" for v in K_SUMMARY.values()], f"{r['r']['good_cap']:.4f}",
                            *[len(r["sel"][q]) for q in NOLAB2], *[r["r"]["case_shown"][c] for c in cs]])
        print(f"wrote {a.csv}")
    return {"stats": stats, "overlap": ov, "rows": rows, "front": front, "cands": cands, "base": base}


if __name__ == "__main__":
    main()
