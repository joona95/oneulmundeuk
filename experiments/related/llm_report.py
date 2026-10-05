#!/usr/bin/env python3
"""
오늘문득 M5-2a: 저장된 LLM 판정(runs/llm-*.jsonl)으로 결과표를 만든다. 모델을 다시 호출하지 않는다.

  python3 llm_report.py --out results-m5-2a.md
  python3 llm_report.py --log runs/llm-qwen3.5-4b-q4_K_M-judge_v1.jsonl --out results-m5-2a.md

선택 규칙: e5 순서의 Top N 후보 중 LLM이 label 2로 판정한 것만, e5 순서대로 최대 5개. 2가 없으면 0개.
판정 실패(status=error)는 2로 치지 않되, 결과 맨 위에 실패 수와 목록을 표시한다.
"""
import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

import eval as ev

HERE = Path(__file__).resolve().parent
F_TYPES = [
    ("F1", "같은 상태·사건 반복", "repeat_low_value", "유입↓"),
    ("F2", "주제·단어만 공유", "lexical_trap", "유입↓"),
    ("F3", "다짐 → 행동", "resolve_action", "회수↑"),
    ("F4", "걱정 → 결과", "worry_outcome", "회수↑"),
    ("F5", "단어 없는 연결", "implicit_link", "회수↑"),
    ("F6", "반복 패턴", "recurring", "회수↑"),
]


def read_log(path):
    meta, latest = None, {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        r = json.loads(line)
        if r.get("type") == "meta":
            meta = meta or r
        elif r.get("type") == "judgment":
            latest[(r["cid"], r["attempt"])] = r
    return meta, latest


def esc(t):
    return (t or "").replace("|", "\\|").replace("\n", " ")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--log", help="기본: runs/llm-*.jsonl 중 최신")
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--out")
    a = ap.parse_args()

    log = Path(a.log) if a.log else max((HERE / "runs").glob("llm-*-judge_*.jsonl"), key=lambda p: p.stat().st_mtime)
    meta, latest = read_log(log)
    ds = ev.load(a.dataset)
    assert not ev.check(ds)
    e5 = json.loads(Path(a.e5).read_text(encoding="utf-8"))
    order = {qid: [x["id"] for x in lst] for qid, lst in e5["rankings"].items()}
    qs = ds["queries"]
    cand = {c["id"]: (q, c) for q in qs for c in q["candidates"]}
    attempts = sorted({k[1] for k in latest})

    def label(cid, attempt):
        r = latest.get((cid, attempt))
        return r["label"] if r and r["status"] == "ok" else None

    def select(n, attempt=1, both=False):
        out = {}
        for q in qs:
            pool = order[q["id"]][:n]
            ok = [cid for cid in pool if label(cid, attempt) == 2 and (not both or all(label(cid, t) == 2 for t in attempts))]
            out[q["id"]] = ok[:ev.K]
        return out

    big = 10 ** 6
    systems = [("e5 Top5 (baseline)", {qid: lst[:ev.K] for qid, lst in order.items()})]
    systems.append(("e5 all → LLM", select(big, 1)))
    systems.append(("e5 Top10 → LLM", select(10, 1)))
    systems.append(("e5 Top7 → LLM", select(7, 1)))
    if 2 in attempts:
        systems.append(("e5 all → LLM (2회차)", select(big, 2)))
        systems.append(("e5 all → LLM (두 번 다 2)", select(big, 1, both=True)))
    res = {name: ev.compute(ds, r) for name, r in systems}
    names = [n for n, _ in systems]

    out = []
    p = out.append
    total_pairs = len(cand)
    p("# M5-2a 결과 · local LLM resurfacing-value 판정\n")
    p(f"- 판정 기록: `{log.name}` · model `{meta['model']}` · prompt `{meta['prompt_file']}` ({meta['prompt_sha']}) · "
      f"dataset {meta['dataset_version']} · options `{json.dumps(meta['options'], ensure_ascii=False)}`")
    p("- 입력은 현재 기록 text + 과거 기록 text + 일반 판단 기준뿐 (label/case/note/date/emotion/category/e5 순위/cosine 없음). 후보 하나씩 판정.")
    p("- 선택: e5 순서의 Top N 중 LLM label 2만, e5 순서대로 최대 5개. 2가 없으면 0개.\n")

    # coverage / failures
    p("## 판정 완료 여부\n")
    errors = [r for r in latest.values() if r["status"] != "ok"]
    for t in attempts:
        okc = sum(1 for (cid, at), r in latest.items() if at == t and r["status"] == "ok")
        p(f"- {t}회차: ok {okc}/{total_pairs}" + ("" if okc == total_pairs else f" · **미완료 {total_pairs - okc}**"))
    if errors:
        p(f"\n> ⚠️ **판정 실패 {len(errors)}건.** 실패한 pair는 2로 치지 않았다. 아래 지표는 실패를 포함한 상태다.\n")
        p("| candidate | 회차 | error | raw |")
        p("| --- | --- | --- | --- |")
        for r in sorted(errors, key=lambda r: (r["attempt"], r["cid"])):
            p(f"| {r['cid']} | {r['attempt']} | {esc(r['error'])} | {esc((r.get('raw') or '')[:120])} |")
    else:
        p("- 판정 실패 0건")
    think = sum(1 for r in latest.values() if r.get("thinking_present"))
    if think:
        p(f"- ⚠️ thinking 출력이 섞인 응답 {think}건 (think=false가 적용되지 않았을 수 있음)")
    el = sorted(r.get("elapsed_ms", 0) for r in latest.values() if r["status"] == "ok")
    if el:
        p(f"- 호출 시간: 중앙 {el[len(el) // 2]}ms · 최대 {el[-1]}ms")

    # 1. metrics
    p("\n## 1. 전체 지표\n")
    p("주 지표는 **Bad% · Top1=0 · Quiet miss · hard negative 유입**. 주 실험은 `e5 all → LLM`(이 평가셋에서는 Top10과 거의 같음), 보조는 `Top7`.\n")
    p("| metric | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- |" + " --- |" * len(names))
    for k in res[names[0]]["summary"]:
        p(f"| {k} | " + " | ".join(f"{res[n]['summary'][k]:.2f}" for n in names) + " |")
    shown = {n: sum(r["shown"] for r in res[n]["rows"]) for n in names}
    p("| 보여준 총 개수 (17 queries) | " + " | ".join(str(shown[n]) for n in names) + " |")

    # 2. quiet queries
    nolab2 = [q for q in qs if not any(c["label"] == 2 for c in q["candidates"])]
    p("\n## 2. 보여줄 게 없어야 하는 query (정답 0개)\n")
    p("| query | 현재 기록 | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- | --- |" + " --- |" * len(names))
    sel = dict(systems)
    for q in nolab2:
        cells = []
        for n in names:
            ids = sel[n][q["id"]]
            cells.append("**0개 ✓**" if not ids else f"{len(ids)}개 ({', '.join(i.split('-')[1] for i in ids)})")
        p(f"| {q['id']} | {esc(q['text'])} | " + " | ".join(cells) + " |")

    # 3. failure types
    p("\n## 3. 실패 유형별 변화\n")
    p("유입 = Top5(최종 결과)에 들어온 수 / 전체, 회수 = 최종 결과에 들어온 label 2 수 / 전체.\n")
    p("| | 유형 | case | 목표 | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- | --- | --- | --- |" + " --- |" * len(names))
    for f, title, case, goal in F_TYPES:
        p(f"| {f} | {title} | {case} | {goal} | " + " | ".join(f"{res[n]['case_shown'][case]}/{res[n]['case_total'][case]}" for n in names) + " |")
    for case in ("change", "reversal"):
        p(f"| – | (유지 확인) | {case} | 회수 유지 | " + " | ".join(f"{res[n]['case_shown'][case]}/{res[n]['case_total'][case]}" for n in names) + " |")
    p(f"| F7 | 보여줄 게 없음 | {len(nolab2)} queries | 0개 반환 | "
      + " | ".join(f"{sum(not sel[n][q['id']] for q in nolab2)}/{len(nolab2)} query 0개" for n in names) + " |")

    # 4. judgment quality (pair level, attempt 1)
    p("\n## 4. 판정 자체 (1회차, pair 단위)\n")
    p("정답 label × LLM label. 최종 Top5와 무관하게 157쌍 전체.\n")
    cm = Counter()
    for cid, (q, c) in cand.items():
        cm[(c["label"], label(cid, 1))] += 1
    p("| 정답 \\ LLM | 2 | 1 | 0 | 실패 |")
    p("| --- | --- | --- | --- | --- |")
    for g in (2, 1, 0):
        p(f"| **{g}** | " + " | ".join(str(cm[(g, x)]) for x in (2, 1, 0, None)) + " |")
    p("\ncase별 LLM 판정 분포 (2 / 1 / 0 / 실패)\n")
    p("| case | 정답 | LLM 2 | LLM 1 | LLM 0 | 실패 |")
    p("| --- | --- | --- | --- | --- | --- |")
    for case in ev.CASE_LABELS:
        cc = Counter(label(cid, 1) for cid, (q, c) in cand.items() if c["case"] == case)
        p(f"| {case} | {next(iter(ev.CASE_LABELS[case]))} | {cc[2]} | {cc[1]} | {cc[0]} | {cc[None]} |")

    # 5. stability
    if 2 in attempts:
        both = [cid for cid in cand if label(cid, 1) is not None and label(cid, 2) is not None]
        exact = sum(label(c, 1) == label(c, 2) for c in both)
        binary = sum((label(c, 1) == 2) == (label(c, 2) == 2) for c in both)
        p("\n## 5. 판정 안정성 (같은 설정 2회)\n")
        p(f"- 비교 가능한 pair {len(both)}/{len(cand)}")
        if both:
            p(f"- label exact agreement: {exact}/{len(both)} = {exact / len(both):.2f}")
            p(f"- 2 여부(binary) agreement: {binary}/{len(both)} = {binary / len(both):.2f}")
        diff = [c for c in both if label(c, 1) != label(c, 2)]
        if diff:
            p("\n| candidate | 정답 | 1회차 | 2회차 | 과거 기록 |")
            p("| --- | --- | --- | --- | --- |")
            for cid in diff:
                q, c = cand[cid]
                p(f"| {cid} | {c['label']} | {label(cid, 1)} | {label(cid, 2)} | {esc(c['text'])} |")

    # 6. examples
    def rows(pred, title, limit=20):
        xs = [(cid, q, c, latest[(cid, 1)]) for cid, (q, c) in cand.items() if (cid, 1) in latest and pred(c, label(cid, 1))]
        p(f"\n### {title} — {len(xs)}건\n")
        if not xs:
            return
        p("| candidate | case | LLM | 현재 기록 | 과거 기록 | LLM reason |")
        p("| --- | --- | --- | --- | --- | --- |")
        for cid, q, c, r in xs[:limit]:
            p(f"| {cid} | {c['case']} | {r['label']} | {esc(q['text'])} | {esc(c['text'])} | {esc(r['reason'])} |")

    p("\n## 6. 실패 분석용 사례 (1회차, reason은 점수에 쓰지 않음)\n")
    rows(lambda c, l: c["label"] == 0 and l == 2, "정답 0인데 LLM이 2 (보여주면 거슬리는 실패)")
    rows(lambda c, l: c["label"] == 2 and l in (0, 1), "정답 2인데 LLM이 0/1 (놓친 연결)")
    rows(lambda c, l: c["label"] == 1 and l == 2, "정답 1인데 LLM이 2 (애매한 것을 보여줌)", limit=12)

    body = "\n".join(out) + "\n"
    if a.out:
        Path(a.out).write_text(body, encoding="utf-8")
        print(f"wrote {a.out}")
    else:
        print(body)


if __name__ == "__main__":
    main()
