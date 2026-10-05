#!/usr/bin/env python3
"""
오늘문득 M5-1: embedding run 두 개를 같은 기준으로 비교해 results.md의 "자동 생성" 부분을 만든다.

  python3 report.py --small runs/e5-small-ko.json --big runs/kure.json > results.generated.md

모델 간 cosine 절댓값은 비교하지 않는다. 비교는 각 모델 "내부" 순위와 label 분리 정도로만 한다.
점수는 query별 표에 참고용으로만 싣는다.
"""
import argparse
import json
import random
from collections import defaultdict
from pathlib import Path

import eval as ev

HERE = Path(__file__).resolve().parent
TOP = ev.K


def load_run(path):
    run = json.loads(Path(path).read_text(encoding="utf-8"))
    name = Path(path).stem
    return name, run


def ranks_of(run):
    """{qid: {cid: (rank 1-based, score)}}"""
    out = {}
    for qid, lst in run["rankings"].items():
        out[qid] = {x["id"]: (i + 1, x["score"]) for i, x in enumerate(lst)}
    return out


def pairwise(ds, rk, hi, lo):
    """같은 query 안에서 hi 집합 점수 > lo 집합 점수인 쌍의 비율 (동점 0.5). 모델 내부 비교라 절댓값과 무관."""
    win = n = 0
    for q in ds["queries"]:
        r = rk[q["id"]]
        H = [c for c in q["candidates"] if hi(c)]
        L = [c for c in q["candidates"] if lo(c)]
        for h in H:
            for l in L:
                sh, sl = r[h["id"]][1], r[l["id"]][1]
                win += 1 if sh > sl else 0.5 if sh == sl else 0
                n += 1
    return win / n if n else float("nan")


def separable(ds, rk):
    """label 2가 전부 label 0보다 위에 있는 query 수 / label 2가 있는 query 수"""
    ok = tot = 0
    for q in ds["queries"]:
        r = rk[q["id"]]
        s2 = [r[c["id"]][1] for c in q["candidates"] if c["label"] == 2]
        s0 = [r[c["id"]][1] for c in q["candidates"] if c["label"] == 0]
        if not s2:
            continue
        tot += 1
        ok += min(s2) > max(s0)
    return ok, tot


def mean_rank(ds, rk, pred):
    xs = [rk[q["id"]][c["id"]][0] for q in ds["queries"] for c in q["candidates"] if pred(c)]
    return sum(xs) / len(xs) if xs else float("nan")


def in_top(ds, rk, pred):
    hit = tot = 0
    for q in ds["queries"]:
        for c in q["candidates"]:
            if pred(c):
                tot += 1
                hit += rk[q["id"]][c["id"]][0] <= TOP
    return hit, tot


def esc(t):
    return t.replace("|", "\\|")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--small", required=True)
    ap.add_argument("--big", required=True)
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--out", help="results.md 경로. 주면 머리말(방법) + 해석 자리 + 생성 결과를 함께 쓴다")
    a = ap.parse_args()

    ds = ev.load(a.dataset)
    assert not ev.check(ds)
    qs = {q["id"]: q for q in ds["queries"]}
    cand = {c["id"]: (q, c) for q in ds["queries"] for c in q["candidates"]}
    (sn, sr), (bn, br) = load_run(a.small), load_run(a.big)
    runs = [(sn, sr), (bn, br)]
    rks = {n: ranks_of(r) for n, r in runs}
    res = {n: ev.compute(ds, ev.normalize(r)) for n, r in runs}

    # 참고 기준선 (eval.py 내장)
    refs = {}
    acc = defaultdict(float)
    for seed in range(20):
        s = ev.compute(ds, ev.baseline(ds, "random", seed))["summary"]
        for k, v in s.items():
            acc[k] += v / 20
    refs["random (20 seeds)"] = acc
    refs["lexical (글자 2-gram)"] = ev.compute(ds, ev.baseline(ds, "lexical", 0))["summary"]
    refs["recency"] = ev.compute(ds, ev.baseline(ds, "recency", 0))["summary"]

    out = []
    p = out.append
    p("<!-- 아래는 report.py가 생성한 부분. 재생성: python3 report.py --small runs/e5-small-ko.json --big runs/kure.json -->\n")
    p("## 실행 정보\n")
    p("| run | model | revision | dim | prefix | encode |")
    p("| --- | --- | --- | --- | --- | --- |")
    for n, r in runs:
        p(f"| `{n}` | {r['model']} | `{r['revision'][:12]}` | {r['dim']} | `{r['prefix'] or '(없음)'}` | {r['encode_seconds']}s ({r['texts']} texts, {r['env']['machine']}) |")

    # 1. 전체 지표
    p("\n## 1. 전체 지표\n")
    p("Top5 기준. 이번 단계의 주 지표는 **Bad% · Top1=0 · hard negative 유입**이고, Good@5는 무작위(≈0.59)보다 나은지만 본다. "
      "Quiet miss는 threshold가 없어 모두 5다.\n")
    keys = list(res[sn]["summary"].keys())
    cols = [sn, bn, *refs]
    p("| metric | " + " | ".join(f"`{c}`" if c in rks else c for c in cols) + " |")
    p("| --- |" + " --- |" * len(cols))
    for k in keys:
        vals = [res[c]["summary"][k] if c in res else refs[c][k] for c in cols]
        p(f"| {k} | " + " | ".join(f"{v:.2f}" for v in vals) + " |")

    # 2. label 분리
    p("\n## 2. label 분리 (모델 내부 순위 기준)\n")
    p("같은 query 안에서 위 집합이 아래 집합보다 높은 점수를 받은 쌍의 비율 (0.5 = 무작위, 1.0 = 완벽 분리). "
      "모델마다 cosine 분포가 달라서 점수 절댓값 대신 이것을 비교한다.\n")
    L = lambda l: (lambda c: c["label"] == l)
    C = lambda case: (lambda c: c["case"] == case)
    rows = [
        ("P(2 > 0)", L(2), L(0)), ("P(2 > 1)", L(2), L(1)), ("P(1 > 0)", L(1), L(0)),
        ("P(2 > repeat_low_value)", L(2), C("repeat_low_value")),
        ("P(2 > lexical_trap)", L(2), C("lexical_trap")),
        ("P(2 > unrelated)", L(2), C("unrelated")),
    ]
    p(f"| 쌍 | `{sn}` | `{bn}` |")
    p("| --- | --- | --- |")
    for name, hi, lo in rows:
        p(f"| {name} | {pairwise(ds, rks[sn], hi, lo):.2f} | {pairwise(ds, rks[bn], hi, lo):.2f} |")
    for n in (sn, bn):
        ok, tot = separable(ds, rks[n])
        rks[n]["_sep"] = (ok, tot)
    p(f"| 완전 분리 query (모든 2 > 모든 0) | {rks[sn]['_sep'][0]}/{rks[sn]['_sep'][1]} | {rks[bn]['_sep'][0]}/{rks[bn]['_sep'][1]} |")
    p("\n평균 순위 (후보 8~10개 중, 낮을수록 위)\n")
    p(f"| 그룹 | `{sn}` | `{bn}` |")
    p("| --- | --- | --- |")
    for name, pred in [("label 2", L(2)), ("label 1", L(1)), ("label 0", L(0))]:
        p(f"| {name} | {mean_rank(ds, rks[sn], pred):.1f} | {mean_rank(ds, rks[bn], pred):.1f} |")

    # 3. case별
    p("\n## 3. case별 결과\n")
    p("Top5 진입 수 / 전체, 평균 순위. 2 유형은 높을수록, 0 유형은 낮을수록 좋다.\n")
    p(f"| case | label | `{sn}` Top5 | `{sn}` 평균 순위 | `{bn}` Top5 | `{bn}` 평균 순위 |")
    p("| --- | --- | --- | --- | --- | --- |")
    focus = ["repeat_low_value", "lexical_trap", "reversal", "implicit_link"]
    others = [c for c in ev.CASE_LABELS if c not in focus]
    for case in focus + others:
        lab = next(iter(ev.CASE_LABELS[case]))
        cells = []
        for n in (sn, bn):
            h, t = in_top(ds, rks[n], C(case))
            cells += [f"{h}/{t}", f"{mean_rank(ds, rks[n], C(case)):.1f}"]
        bold = "**" if case in focus else ""
        p(f"| {bold}{case}{bold} | {lab} | " + " | ".join(cells) + " |")

    def fp_list(n):
        xs = []
        for q in ds["queries"]:
            for c in q["candidates"]:
                rank, score = rks[n][q["id"]][c["id"]]
                if c["label"] == 0 and rank <= TOP:
                    xs.append((rank, q, c, score))
        return sorted(xs, key=lambda x: (x[0], x[2]["id"]))

    def fn_list(n):
        xs = []
        for q in ds["queries"]:
            for c in q["candidates"]:
                rank, score = rks[n][q["id"]][c["id"]]
                if c["label"] == 2 and rank > TOP:
                    xs.append((rank, q, c, score))
        return sorted(xs, key=lambda x: (-x[0], x[2]["id"]))

    # 4. false positive
    p("\n## 4. 대표 false positive — label 0인데 Top5\n")
    p("순위가 높은 것(=자신 있게 올린 것)부터. 1~2위 0이 가장 거슬리는 실패다.\n")
    for n in (sn, bn):
        xs = fp_list(n)
        p(f"\n### `{n}` — {len(xs)}건 (1위 {sum(x[0] == 1 for x in xs)} · 2위 {sum(x[0] == 2 for x in xs)})\n")
        p("| 순위 | case | 현재 기록 | 과거 기록 (label 0) |")
        p("| --- | --- | --- | --- |")
        for rank, q, c, score in xs[:12]:
            p(f"| {rank} | {c['case']} | {esc(q['text'])} | {esc(c['text'])} |")

    # 5. false negative
    p("\n## 5. 대표 false negative — label 2인데 Top5 밖\n")
    for n in (sn, bn):
        xs = fn_list(n)
        p(f"\n### `{n}` — {len(xs)}건\n")
        p("| 순위 | case | 현재 기록 | 과거 기록 (label 2) |")
        p("| --- | --- | --- | --- |")
        for rank, q, c, score in xs[:12]:
            p(f"| {rank} | {c['case']} | {esc(q['text'])} | {esc(c['text'])} |")

    # 6. 공통 실패
    p("\n## 6. 두 모델이 공통으로 실패한 사례\n")
    fp_s = {x[2]["id"]: x for x in fp_list(sn)}
    fp_b = {x[2]["id"]: x for x in fp_list(bn)}
    fn_s = {x[2]["id"]: x for x in fn_list(sn)}
    fn_b = {x[2]["id"]: x for x in fn_list(bn)}
    both_fp = sorted(set(fp_s) & set(fp_b))
    both_fn = sorted(set(fn_s) & set(fn_b))
    p(f"**둘 다 Top5에 올린 label 0: {len(both_fp)}건** (`{sn}` 전체 {len(fp_s)} · `{bn}` 전체 {len(fp_b)})\n")
    p(f"| case | `{sn}` 순위 | `{bn}` 순위 | 현재 기록 | 과거 기록 |")
    p("| --- | --- | --- | --- | --- |")
    for cid in sorted(both_fp, key=lambda i: (fp_s[i][0] + fp_b[i][0], i)):
        q, c = cand[cid]
        p(f"| {c['case']} | {fp_s[cid][0]} | {fp_b[cid][0]} | {esc(q['text'])} | {esc(c['text'])} |")
    p(f"\n**둘 다 Top5 밖으로 민 label 2: {len(both_fn)}건** (`{sn}` 전체 {len(fn_s)} · `{bn}` 전체 {len(fn_b)})\n")
    p(f"| case | `{sn}` 순위 | `{bn}` 순위 | 현재 기록 | 과거 기록 |")
    p("| --- | --- | --- | --- | --- |")
    for cid in sorted(both_fn, key=lambda i: (-(fn_s[i][0] + fn_b[i][0]), i)):
        q, c = cand[cid]
        p(f"| {c['case']} | {fn_s[cid][0]} | {fn_b[cid][0]} | {esc(q['text'])} | {esc(c['text'])} |")

    # 7. 큰 모델이 해결한 것 / 나빠진 것
    p(f"\n## 7. `{bn}`가 `{sn}` 대비 해결한 사례 (그리고 반대)\n")

    def table(title, ids, src_a, src_b):
        p(f"\n**{title}: {len(ids)}건**\n")
        if not ids:
            return
        p(f"| case | label | `{sn}` 순위 | `{bn}` 순위 | 현재 기록 | 과거 기록 |")
        p("| --- | --- | --- | --- | --- | --- |")
        for cid in sorted(ids):
            q, c = cand[cid]
            p(f"| {c['case']} | {c['label']} | {rks[sn][q['id']][cid][0]} | {rks[bn][q['id']][cid][0]} | {esc(q['text'])} | {esc(c['text'])} |")

    table(f"label 0: `{sn}` Top5 → `{bn}` Top5 밖 (해결)", set(fp_s) - set(fp_b), fp_s, fp_b)
    table(f"label 2: `{sn}` Top5 밖 → `{bn}` Top5 (해결)", set(fn_s) - set(fn_b), fn_s, fn_b)
    table(f"label 0: `{sn}` Top5 밖 → `{bn}` Top5 (악화)", set(fp_b) - set(fp_s), fp_b, fp_s)
    table(f"label 2: `{sn}` Top5 → `{bn}` Top5 밖 (악화)", set(fn_b) - set(fn_s), fn_b, fn_s)

    # 8. query별
    p("\n## 8. query별 ranking / score\n")
    p(f"각 모델 내부 순위와 cosine. **모델 간 점수 절댓값은 비교하지 않는다.** Top5 경계는 순위 5.\n")
    for q in ds["queries"]:
        p(f"\n### {q['id']} · {esc(q['theme'])}\n")
        p(f"> {esc(q['text'])}\n")
        p(f"| `{sn}` | `{bn}` | label | case | 과거 기록 |")
        p("| --- | --- | --- | --- | --- |")
        cs = sorted(q["candidates"], key=lambda c: rks[sn][q["id"]][c["id"]][0])
        for c in cs:
            ra, sa = rks[sn][q["id"]][c["id"]]
            rb, sb = rks[bn][q["id"]][c["id"]]
            mark = lambda r: f"**{r}**" if r <= TOP else f"{r}"
            p(f"| {mark(ra)} ({sa:.3f}) | {mark(rb)} ({sb:.3f}) | {c['label']} | {c['case']} | {esc(c['text'])} |")
    body = "\n".join(out)
    if not a.out:
        print(body)
        return
    head = f"""# M5-1 결과 · text-only embedding cosine baseline

질문: **본문 embedding + cosine similarity만으로 M5-0 평가셋을 어디까지 풀 수 있는가?**

- 평가셋: `dataset.json` (synthetic, 15 queries · 131 pairs). 실제 사용자 기록은 쓰지 않았다.
- 모델: `{sn}` = 작은 모바일 후보, `{bn}` = 큰 한국어 dense retrieval 모델(상한 비교용).
- 입력은 `text`만. category · emotion · date · heuristic · reranker · threshold 없음.
  질문/문서를 구분하지 않고 양쪽을 같은 방식으로 encode, L2 정규화 후 내적(cosine) 내림차순.
- 모델 간 cosine **절댓값은 비교하지 않는다**. 각 모델 내부 순위와 label 분리 정도(§2)로 비교한다.
- 참고선: random(20 seeds 평균), lexical(글자 2-gram 겹침, 모델 없음), recency.

## 해석

_(실행 결과를 보고 작성)_

"""
    Path(a.out).write_text(head + body + "\n", encoding="utf-8")
    print(f"wrote {a.out}")


if __name__ == "__main__":
    main()
