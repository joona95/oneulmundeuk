#!/usr/bin/env python3
"""
오늘문득 M5-0: related retrieval 평가 (stdlib only, no ML framework).

  python3 eval.py --check                       # 데이터셋 검증 + 라벨 분포
  python3 eval.py --system oracle               # 순위 상한: 라벨순 Top5 (nDCG 1.0)
  python3 eval.py --system oracle_quiet         # 앱 기준 이상: 2만 보여주고, 없으면 아무것도 안 보여줌
  python3 eval.py --system recency              # 최신순 baseline
  python3 eval.py --system random --seed 7      # 무작위 baseline
  python3 eval.py --system lexical              # 글자 2-gram 겹침 참고선 (모델 없음)
  python3 eval.py --rankings runs/v1.json -v    # 외부 시스템 결과 (embed.py의 runs/*.json도 그대로 읽음)

rankings 파일 형식 (query id → 보여줄 순서대로의 candidate id, 최대 5개만 평가):
  {"q01": ["q01-d", "q01-a", ...], "q02": [], ...}
  - 앱처럼 "의미 있는 것만" 보여주는 시스템이면 5개보다 적게(0개 포함) 반환해도 된다.
  - 각 항목을 {"id": "...", "score": 0.83} 형태로 써도 된다 (score는 기록용, 평가는 순서만 사용).
"""
import argparse
import json
import random
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
K = 5  # RelatedRecordFinder.LIMIT
GAIN = {2: 3, 1: 1, 0: 0}  # nDCG gain: 2를 1보다 확실히 우대
POSITIVE_CASES = ["change", "worry_outcome", "resolve_action", "recurring", "reversal", "implicit_link"]
HARD_NEG_CASES = ["repeat_low_value", "lexical_trap"]
EMOTIONS = {"calm", "happy", "excited", "so_so", "tired", "anxious", "sad"}
CATEGORIES = {"커리어", "성장", "개발", "사이드 프로젝트", "일상", "관계", "취미"}
CASE_LABELS = {**{c: {2} for c in POSITIVE_CASES}, "ambiguous": {1}, "repeat_low_value": {0}, "lexical_trap": {0}, "unrelated": {0}}


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


# ── dataset check ────────────────────────────────────────────────────────────
def check(ds):
    errors, ids = [], set()
    for q in ds["queries"]:
        cs = q["candidates"]
        if not 8 <= len(cs) <= 12:
            errors.append(f"{q['id']}: 후보 {len(cs)}개 (8~12개여야 함)")
        for item in [q, *cs]:
            if item["id"] in ids:
                errors.append(f"중복 id {item['id']}")
            ids.add(item["id"])
            if item.get("emotion") not in EMOTIONS | {None}:
                errors.append(f"{item['id']}: emotion key '{item['emotion']}'")
            if item.get("category") not in CATEGORIES | {None}:
                errors.append(f"{item['id']}: category '{item['category']}'")
        for c in cs:
            if c["label"] not in (0, 1, 2):
                errors.append(f"{c['id']}: label {c['label']}")
            if c["case"] not in CASE_LABELS:
                errors.append(f"{c['id']}: case '{c['case']}'")
            elif c["label"] not in CASE_LABELS[c["case"]]:
                errors.append(f"{c['id']}: case {c['case']}와 label {c['label']} 불일치")
            if not c["date"] < q["date"]:
                errors.append(f"{c['id']}: 과거 기록이 query({q['date']})보다 늦음 ({c['date']})")
    return errors


def distribution(ds):
    qs = ds["queries"]
    cands = [c for q in qs for c in q["candidates"]]
    labels = Counter(c["label"] for c in cands)
    cases = Counter(c["case"] for c in cands)
    print(f"queries {len(qs)} · pairs {len(cands)} · 후보/query {min(len(q['candidates']) for q in qs)}~{max(len(q['candidates']) for q in qs)}")
    print("label  " + "  ".join(f"{l}: {labels[l]} ({labels[l] / len(cands):.0%})" for l in (2, 1, 0)))
    print("case   " + "  ".join(f"{c} {cases[c]}" for c in CASE_LABELS))
    print("label-2 없음 (아무것도 안 보여주는 게 정답에 가까움): " + ", ".join(q["id"] for q in qs if not any(c["label"] == 2 for c in q["candidates"])))
    print("label-2 개수/query: " + " ".join(f"{q['id']}={sum(c['label'] == 2 for c in q['candidates'])}" for q in qs))


# ── baselines (평가 코드 sanity 용; 실제 후보 시스템이 아님) ───────────────────────
def baseline(ds, name, seed):
    rng = random.Random(seed)
    out = {}
    for q in ds["queries"]:
        cs = q["candidates"]
        if name in ("oracle", "oracle_quiet"):
            ranked = sorted(cs, key=lambda c: (-c["label"], c["id"]))
            if name == "oracle_quiet":
                ranked = [c for c in ranked if c["label"] == 2]  # 이상적인 앱: 2만, 없으면 아무것도 안 보여줌
        elif name == "recency":
            ranked = sorted(cs, key=lambda c: c["date"], reverse=True)
        elif name == "lexical":
            # 단어 겹침 참고선: 공백 제거 후 글자 2-gram cosine. "embedding이 단순 글자 겹침보다 나은가"를 보기 위한 비교 기준.
            qv = _bigrams(q["text"])
            ranked = sorted(cs, key=lambda c: (-_cos(qv, _bigrams(c["text"])), c["id"]))
        elif name == "random":
            ranked = cs[:]
            rng.shuffle(ranked)
        else:
            sys.exit(f"unknown system {name}")
        out[q["id"]] = [c["id"] for c in ranked]
    return out


def _bigrams(t):
    t = t.replace(" ", "")
    return Counter(t[i:i + 2] for i in range(len(t) - 1))


def _cos(a, b):
    from math import sqrt
    n = sum(a[k] * b[k] for k in a)
    d = sqrt(sum(v * v for v in a.values())) * sqrt(sum(v * v for v in b.values()))
    return n / d if d else 0.0


def normalize(rankings):
    """{qid: [id | {"id", "score"}]} 또는 embed.py 결과 파일({"rankings": {...}})을 순서 목록으로."""
    if "rankings" in rankings:
        rankings = rankings["rankings"]
    return {qid: [x["id"] if isinstance(x, dict) else x for x in lst] for qid, lst in rankings.items()}


# ── metrics ──────────────────────────────────────────────────────────────────
def dcg(labels):
    from math import log2
    return sum(GAIN[l] / log2(i + 2) for i, l in enumerate(labels))


def compute(ds, rankings):
    """지표 계산만 (출력 없음). report.py도 이 함수를 쓴다."""
    rows = []
    case_total, case_shown = Counter(), Counter()
    for q in ds["queries"]:
        by_id = {c["id"]: c for c in q["candidates"]}
        shown = [by_id[i] for i in rankings.get(q["id"], []) if i in by_id][:K]
        labels = [c["label"] for c in shown]
        n2 = sum(c["label"] == 2 for c in q["candidates"])
        ideal = dcg(sorted((c["label"] for c in q["candidates"]), reverse=True)[:K])
        for c in q["candidates"]:
            case_total[c["case"]] += 1
        for c in shown:
            case_shown[c["case"]] += 1
        rows.append(dict(
            id=q["id"], n2=n2, shown=len(shown), labels=labels,
            recall2=(labels.count(2) / min(K, n2)) if n2 else None,
            share2=(labels.count(2) / len(shown)) if shown else None,
            zeros=labels.count(0),
            top1=labels[0] if labels else None,
            ndcg=(dcg(labels) / ideal) if (n2 and ideal) else None,
        ))

    def mean(xs):
        xs = [x for x in xs if x is not None]
        return sum(xs) / len(xs) if xs else float("nan")

    with2 = [r for r in rows if r["n2"]]
    without2 = [r for r in rows if not r["n2"]]
    shown_total = sum(r["shown"] for r in rows)
    summary = {
        "Good@5 (2 회수율, query 평균)": mean(r["recall2"] for r in with2),
        "nDCG@5 (gain 2→3, 1→1)": mean(r["ndcg"] for r in with2),
        "Worth% (보여준 것 중 2 비율)": mean(r["share2"] for r in rows),
        "Bad% (보여준 것 중 0 비율, 전체)": (sum(r["zeros"] for r in rows) / shown_total) if shown_total else float("nan"),
        "Top1=0 (1위에 0을 올린 query 비율)": mean([(r["top1"] == 0) if r["top1"] is not None else None for r in rows]),
        "Quiet miss (2 없는 query에서 보여준 개수 평균)": mean(r["shown"] for r in without2),
    }
    return dict(summary=summary, rows=rows, case_total=case_total, case_shown=case_shown)


def evaluate(ds, rankings, verbose=False):
    r = compute(ds, rankings)
    summary, rows, case_total, case_shown = r["summary"], r["rows"], r["case_total"], r["case_shown"]
    print(f"\n{'metric':44s} value")
    for k, v in summary.items():
        print(f"{k:44s} {v:.2f}")
    print("\n2 유형별 회수 (Top5에 들어온 수 / 전체)")
    for c in POSITIVE_CASES:
        print(f"  {c:16s} {case_shown[c]:2d}/{case_total[c]:2d}")
    print("hard negative 유입 (Top5에 들어온 수 / 전체)")
    for c in HARD_NEG_CASES + ["unrelated", "ambiguous"]:
        print(f"  {c:16s} {case_shown[c]:2d}/{case_total[c]:2d}")
    if verbose:
        print("\nquery  n2 shown labels           Good@5 nDCG")
        for r in rows:
            g = "  -  " if r["recall2"] is None else f"{r['recall2']:.2f} "
            n = "  -  " if r["ndcg"] is None else f"{r['ndcg']:.2f}"
            print(f"{r['id']}   {r['n2']}   {r['shown']}    {str(r['labels']):16s} {g}  {n}")
    return summary


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--system", choices=["oracle", "oracle_quiet", "recency", "random", "lexical"])
    ap.add_argument("--rankings")
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("-v", "--verbose", action="store_true")
    a = ap.parse_args()
    ds = load(a.dataset)
    errors = check(ds)
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    if a.check or not (a.system or a.rankings):
        distribution(ds)
        return
    rankings = baseline(ds, a.system, a.seed) if a.system else normalize(load(a.rankings))
    evaluate(ds, rankings, a.verbose)


if __name__ == "__main__":
    main()
