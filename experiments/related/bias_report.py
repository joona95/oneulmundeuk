#!/usr/bin/env python3
"""
오늘문득 M5-0.1: 날짜 편향 확인 + e5-small 재평가 → results-m5-0.1.md

  python3 bias_report.py --run runs/e5-small-ko.json --out results-m5-0.1.md

"14일 이상만 후보로 허용"은 dataset bias를 드러내기 위한 비교용 baseline이다. production 전략이 아니다.
"""
import argparse
import datetime as dt
import json
from collections import defaultdict
from pathlib import Path

import eval as ev

HERE = Path(__file__).resolve().parent
GATE_DAYS = 14


def gap(q, c):
    return (dt.date.fromisoformat(q["date"]) - dt.date.fromisoformat(c["date"])).days


def dist(xs):
    xs = sorted(xs)
    if not xs:
        return "-"
    pct = lambda p: xs[min(len(xs) - 1, int(p * (len(xs) - 1) + 0.5))]
    return f"n={len(xs)} · min {xs[0]} · 25% {pct(.25)} · 중앙 {pct(.5)} · 75% {pct(.75)} · max {xs[-1]}"


def buckets(xs):
    edges = [(0, 7), (8, 14), (15, 30), (31, 90), (91, 180), (181, 10 ** 6)]
    names = ["≤7일", "8–14일", "15–30일", "31–90일", "91–180일", ">180일"]
    return [sum(lo <= x <= hi for x in xs) for lo, hi in edges], names


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True, help="dataset 1.1 전체로 계산한 e5-small run")
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--out")
    a = ap.parse_args()
    ds = ev.load(a.dataset)
    assert not ev.check(ds), ev.check(ds)
    run = json.loads(Path(a.run).read_text(encoding="utf-8"))
    rank_lists = ev.normalize(run)
    missing = [c["id"] for q in ds["queries"] for c in q["candidates"] if c["id"] not in rank_lists.get(q["id"], [])]
    if missing:
        raise SystemExit(f"run이 dataset {ds.get('version')}보다 오래됨 ({len(missing)}개 후보 없음). embed.py를 다시 실행하세요.")

    out = []
    p = out.append
    cands = [(q, c) for q in ds["queries"] for c in q["candidates"]]
    new = [(q, c) for q, c in cands if c.get("added_in") == "0.1"]

    p(f"# M5-0.1 결과 · 날짜 편향 보정 후 재집계 (dataset {ds.get('version')})\n")
    p("평가셋 보정 단계다. 성능 개선이 아니다. 기존 15 queries / 131 pairs는 그대로 두고 반례만 추가했다 (`added_in: \"0.1\"`).\n")

    # 1
    lab = defaultdict(int)
    for _, c in cands:
        lab[c["label"]] += 1
    p("## 1. 전체 분포\n")
    p(f"- queries {len(ds['queries'])} (기존 15 + 신규 2) · pairs {len(cands)} (기존 131 + 신규 {len(new)}) · query당 후보 "
      f"{min(len(q['candidates']) for q in ds['queries'])}~{max(len(q['candidates']) for q in ds['queries'])}개")
    p("- label " + " · ".join(f"{l}: {lab[l]} ({lab[l] / len(cands):.0%})" for l in (2, 1, 0)))
    nolab2 = [q["id"] for q in ds["queries"] if not any(c["label"] == 2 for c in q["candidates"])]
    p(f"- label 2가 없는 query (정답 = 0개): {', '.join(nolab2)}")
    p("- 신규 항목: " + ", ".join(f"{c['id']}({c['label']}·{c['case']}·{gap(q, c)}일)" for q, c in new) + "\n")

    # 2, 3
    p("## 2. label별 날짜 간격 (현재 기록 − 과거 기록, 일)\n")
    p("| 그룹 | 분포 | " + " | ".join(buckets([])[1]) + " |")
    p("| --- | --- |" + " --- |" * 6)
    groups = [("label 2", lambda c: c["label"] == 2), ("label 1", lambda c: c["label"] == 1), ("label 0", lambda c: c["label"] == 0),
              ("— repeat_low_value", lambda c: c["case"] == "repeat_low_value"), ("— lexical_trap", lambda c: c["case"] == "lexical_trap"),
              ("— unrelated", lambda c: c["case"] == "unrelated")]
    for name, pred in groups:
        xs = [gap(q, c) for q, c in cands if pred(c)]
        b, _ = buckets(xs)
        p(f"| {name} | {dist(xs)} | " + " | ".join(map(str, b)) + " |")
    p("\n## 3. repeat_low_value 날짜 간격 (v1 → v1.1)\n")
    v1 = [gap(q, c) for q, c in cands if c["case"] == "repeat_low_value" and c.get("added_in") != "0.1"]
    v11 = [gap(q, c) for q, c in cands if c["case"] == "repeat_low_value"]
    p(f"| | 분포 | {GATE_DAYS}일 미만 | {GATE_DAYS}일 이상 |")
    p("| --- | --- | --- | --- |")
    for name, xs in (("v1", v1), ("v1.1", v11)):
        p(f"| {name} | {dist(xs)} | {sum(x < GATE_DAYS for x in xs)} | {sum(x >= GATE_DAYS for x in xs)} |")
    l2 = [gap(q, c) for q, c in cands if c["label"] == 2]
    l2v1 = [gap(q, c) for q, c in cands if c["label"] == 2 and c.get("added_in") != "0.1"]
    p(f"\nlabel 2 중 {GATE_DAYS}일 미만 (gate가 지우는 좋은 연결): v1 {sum(x < GATE_DAYS for x in l2v1)}/{len(l2v1)} → v1.1 {sum(x < GATE_DAYS for x in l2)}/{len(l2)}\n")

    # 4, 5
    def gated(ranks):
        out_ = {}
        for q in ds["queries"]:
            by = {c["id"]: c for c in q["candidates"]}
            out_[q["id"]] = [i for i in ranks[q["id"]] if gap(q, by[i]) >= GATE_DAYS]
        return out_

    def recall_at(ranks, n):
        hit = tot = 0
        for q in ds["queries"]:
            top = set(ranks[q["id"]][:n])
            for c in q["candidates"]:
                if c["label"] == 2:
                    tot += 1
                    hit += c["id"] in top
        return hit / tot

    systems = [("e5-small-ko", rank_lists), (f"e5-small-ko + {GATE_DAYS}일 gate", gated(rank_lists))]
    res = {n: ev.compute(ds, r) for n, r in systems}
    p(f"## 4–5. e5-small-ko 재평가 · {GATE_DAYS}일 gate 비교\n")
    p(f"> **`{GATE_DAYS}일 gate`는 production 전략이 아니라 dataset bias 확인용 baseline이다.** "
      "e5 순위에서 14일 미만 기록을 지운 뒤 Top5. gate가 좋아 보일수록 평가셋이 날짜로 풀린다는 뜻이다.\n")
    p(f"run: `{run['model']}` @ `{run['revision'][:12]}` · text only · cosine\n")
    p("| metric | " + " | ".join(f"`{n}`" for n, _ in systems) + " |")
    p("| --- |" + " --- |" * len(systems))
    for k in res[systems[0][0]]["summary"]:
        p(f"| {k} | " + " | ".join(f"{res[n]['summary'][k]:.2f}" for n, _ in systems) + " |")
    for case in ("repeat_low_value", "lexical_trap"):
        p(f"| {case} 유입 (Top5/전체) | " + " | ".join(f"{res[n]['case_shown'][case]}/{res[n]['case_total'][case]}" for n, _ in systems) + " |")
    for k in (5, 7, 10):
        p(f"| label 2 candidate recall@{k} | " + " | ".join(f"{recall_at(r, k):.2f}" for _, r in systems) + " |")

    # 신규 항목이 어떻게 처리됐나
    p("\n### 신규 항목의 e5 순위\n")
    p("| id | label | case | 간격 | e5 순위 | gate 후 | 과거 기록 |")
    p("| --- | --- | --- | --- | --- | --- | --- |")
    g = gated(rank_lists)
    for q, c in new:
        r0 = rank_lists[q["id"]].index(c["id"]) + 1
        rg = g[q["id"]].index(c["id"]) + 1 if c["id"] in g[q["id"]] else "제거"
        p(f"| {c['id']} | {c['label']} | {c['case']} | {gap(q, c)}일 | {r0} | {rg} | {c['text']} |")
    body = "\n".join(out) + "\n"
    if a.out:
        Path(a.out).write_text(body, encoding="utf-8")
        print(f"wrote {a.out}")
    else:
        print(body)


if __name__ == "__main__":
    main()
