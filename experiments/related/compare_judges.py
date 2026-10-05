#!/usr/bin/env python3
"""
오늘문득 M5-2a: judge 버전 비교 리포트 (e5 Top5 baseline vs judge_v1 vs judge_v2 …). 모델을 호출하지 않는다.

  python3 compare_judges.py \
    --log v1=runs/llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl \
    --log v2=runs/llm-qwen3.5-2b-q4_K_M-judge_v2.jsonl \
    --out results-m5-2a-judge_v2.md

선택 규칙과 지표는 llm_report.py와 같다 (e5 순서 Top N 중 label 2만, e5 순서대로 최대 5개, 2가 없으면 0개 · eval.compute).
1회차(attempt 1) 판정만 쓴다. reason 형식 점검은 진단용이며 점수에 쓰지 않는다.
"""
import argparse
import json
import re
from collections import Counter
from pathlib import Path

import eval as ev
from llm_report import F_TYPES, esc, read_log

HERE = Path(__file__).resolve().parent
TAGS = ["걱정·기대→결과", "다짐→실행", "변화", "반전", "되풀이", "추측 필요", "반복", "주제만 겹침", "다른 맥락"]
REASON_RE = re.compile(r'과거\s*"([^"]+)"\s*/\s*현재\s*"([^"]+)"\s*/\s*관계\s*:\s*(.+)$')


def norm(t):
    return re.sub(r"[\s.,!?~…\"'·]", "", t or "")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--log", action="append", required=True, help="NAME=path (여러 번)")
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--diag", default="v2", help="reason 형식을 점검할 judge 이름")
    ap.add_argument("--out")
    a = ap.parse_args()

    ds = ev.load(a.dataset)
    assert not ev.check(ds)
    e5 = json.loads(Path(a.e5).read_text(encoding="utf-8"))
    order = {qid: [x["id"] for x in lst] for qid, lst in e5["rankings"].items()}
    qs = ds["queries"]
    cand = {c["id"]: (q, c) for q in qs for c in q["candidates"]}

    judges = {}
    for spec in a.log:
        name, path = spec.split("=", 1)
        meta, latest = read_log(path)
        judges[name] = {"path": Path(path), "meta": meta,
                        "lab": {cid: (r["label"] if r["status"] == "ok" else None) for (cid, at), r in latest.items() if at == 1},
                        "rec": {cid: r for (cid, at), r in latest.items() if at == 1}}

    def select(name, n):
        lab = judges[name]["lab"]
        return {q["id"]: [cid for cid in order[q["id"]][:n] if lab.get(cid) == 2][:ev.K] for q in qs}

    big = 10 ** 6
    systems = [("e5 Top5", {qid: lst[:ev.K] for qid, lst in order.items()})]
    for n, tag in ((big, "all"), (10, "Top10"), (7, "Top7")):
        for name in judges:
            systems.append((f"{name} {tag}", select(name, n)))
    res = {s: ev.compute(ds, r) for s, r in systems}
    sel = dict(systems)
    names = [s for s, _ in systems]

    out = []
    p = out.append
    p("# M5-2a 결과 · judge 비교 (" + " vs ".join(["e5 Top5", *judges]) + ")\n")
    p("> ⚠️ **development benchmark 결과다.** judge_v2는 이 평가셋(dataset v1.1, frozen)에서 judge_v1이 틀린 사례를 보고 설계했다. "
      "prompt에 benchmark 문장은 넣지 않았지만, 같은 평가셋으로 판단 기준을 다듬었으므로 아래 개선폭은 낙관적이다. "
      "**최종 일반화 성능으로 해석하지 않으며, 이후 별도 hold-out 세트로 검증한다.**\n")
    for name, j in judges.items():
        m = j["meta"]
        ok = sum(v is not None for v in j["lab"].values())
        err = len(cand) - ok
        p(f"- `{name}`: `{j['path'].name}` · model `{m['model']}` · prompt `{m['prompt_file']}` ({m['prompt_sha']}) · "
          f"options `{json.dumps(m['options'], ensure_ascii=False)}` · 1회차 ok {ok}/{len(cand)}" + (f" · **실패/미완료 {err}**" if err else ""))
    p("- 입력: 현재 기록 text + 과거 기록 text만. dataset v1.1 · e5-small-ko.v1.1 순서 · 선택/평가 로직은 judge_v1과 동일.\n")

    p("## 1. 전체 지표\n")
    p("| metric | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- |" + " --- |" * len(names))
    for k in res[names[0]]["summary"]:
        p(f"| {k} | " + " | ".join(f"{res[n]['summary'][k]:.2f}" for n in names) + " |")
    p("| 보여준 총 개수 | " + " | ".join(str(sum(r["shown"] for r in res[n]["rows"])) for n in names) + " |")

    nolab2 = [q for q in qs if not any(c["label"] == 2 for c in q["candidates"])]
    p("\n## 2. 실패 유형별 (Top5 결과 기준: 유입 = 들어온 수/전체, 회수 = 들어온 label 2/전체)\n")
    p("| | 유형 | case | 목표 | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- | --- | --- | --- |" + " --- |" * len(names))
    for f, title, case, goal in F_TYPES:
        p(f"| {f} | {title} | {case} | {goal} | " + " | ".join(f"{res[n]['case_shown'][case]}/{res[n]['case_total'][case]}" for n in names) + " |")
    for case in ("change", "reversal"):
        p(f"| – | 유지 확인 | {case} | 회수↑ | " + " | ".join(f"{res[n]['case_shown'][case]}/{res[n]['case_total'][case]}" for n in names) + " |")
    p(f"| F7 | 보여줄 게 없음 | {len(nolab2)} queries | 0개 반환 | "
      + " | ".join(f"{sum(not sel[n][q['id']] for q in nolab2)}/{len(nolab2)}" for n in names) + " |")
    p("\n정답 0개 query별 반환 개수 (all)\n")
    alls = [n for n in names if n.endswith(" all")]
    p("| query | 현재 기록 | " + " | ".join(f"`{n}`" for n in alls) + " |")
    p("| --- | --- |" + " --- |" * len(alls))
    for q in nolab2:
        p(f"| {q['id']} | {esc(q['text'])} | " + " | ".join(
            "**0개 ✓**" if not sel[n][q["id"]] else f"{len(sel[n][q['id']])}개 ({', '.join(i.split('-')[1] for i in sel[n][q['id']])})" for n in alls) + " |")

    p("\n## 3. pair-level confusion (1회차, 157쌍)\n")
    for name, j in judges.items():
        cm = Counter((c["label"], j["lab"].get(cid)) for cid, (q, c) in cand.items())
        p(f"\n`{name}`\n")
        p("| 정답 \\ 판정 | 2 | 1 | 0 | 실패 |")
        p("| --- | --- | --- | --- | --- |")
        for g in (2, 1, 0):
            p(f"| **{g}** | " + " | ".join(str(cm[(g, x)]) for x in (2, 1, 0, None)) + " |")
    if len(judges) >= 2:
        n1, n2 = list(judges)[:2]
        p(f"\n`{n1}` → `{n2}` 판정이 바뀐 pair\n")
        moves = Counter()
        for cid, (q, c) in cand.items():
            l1, l2 = judges[n1]["lab"].get(cid), judges[n2]["lab"].get(cid)
            if l1 != l2:
                moves[(c["label"], l1, l2)] += 1
        p("| 정답 | " + n1 + " → " + n2 + " | 건수 |")
        p("| --- | --- | --- |")
        for (g, l1, l2), k in sorted(moves.items(), key=lambda x: (-x[0][0], -x[1])):
            p(f"| {g} | {l1} → {l2} | {k} |")
        p(f"\ncase별 label 2 판정 수 (`{n1}` → `{n2}`)\n")
        p("| case | 정답 | 전체 | " + f"`{n1}` 2 | `{n2}` 2 |")
        p("| --- | --- | --- | --- | --- |")
        for case in ev.CASE_LABELS:
            ids = [cid for cid, (q, c) in cand.items() if c["case"] == case]
            p(f"| {case} | {next(iter(ev.CASE_LABELS[case]))} | {len(ids)} | "
              f"{sum(judges[n1]['lab'].get(i) == 2 for i in ids)} | {sum(judges[n2]['lab'].get(i) == 2 for i in ids)} |")

    if a.diag in judges:
        j = judges[a.diag]
        p(f"\n## 4. `{a.diag}` reason 형식 점검 (진단용 · 점수에 쓰지 않음)\n")
        recs = [(cid, j["rec"][cid]) for cid in cand if cid in j["rec"] and j["rec"][cid]["status"] == "ok"]
        parsed, grounded_p, grounded_c, tags, tag_vs_label = 0, 0, 0, Counter(), Counter()
        for cid, r in recs:
            q, c = cand[cid]
            m = REASON_RE.search(r["reason"].strip())
            if not m:
                continue
            parsed += 1
            past_q, cur_q, rel = m.group(1), m.group(2), m.group(3).strip()
            grounded_p += norm(past_q) in norm(c["text"])
            grounded_c += norm(cur_q) in norm(q["text"])
            tag = next((t for t in TAGS if rel.startswith(t)), "(목록 밖)")
            tags[tag] += 1
            tag_vs_label[(tag, r["label"])] += 1
        n = len(recs)
        p(f"- 형식(과거 \"…\" / 현재 \"…\" / 관계: …)을 지킨 reason: {parsed}/{n}")
        if parsed:
            p(f"- 과거 인용이 실제 과거 기록에 있는 문자열: {grounded_p}/{parsed} · 현재 인용이 실제 현재 기록에 있는 문자열: {grounded_c}/{parsed} (공백·문장부호 무시)")
            p("\n| 관계 tag | 건수 | label 2 | label 1 | label 0 |")
            p("| --- | --- | --- | --- | --- |")
            for t in TAGS + ["(목록 밖)"]:
                if tags[t]:
                    p(f"| {t} | {tags[t]} | {tag_vs_label[(t, 2)]} | {tag_vs_label[(t, 1)]} | {tag_vs_label[(t, 0)]} |")
            pos = {"걱정·기대→결과", "다짐→실행", "변화", "반전", "되풀이"}
            incons = sum(k for (t, l), k in tag_vs_label.items() if (t in pos) != (l == 2) and t != "(목록 밖)")
            p(f"\n- tag와 label이 어긋난 경우 (긍정 tag인데 2가 아님, 또는 부정 tag인데 2): {incons}건")
        bad = [(cid, r) for cid, r in recs if not REASON_RE.search(r["reason"].strip())][:8]
        if bad:
            p("\n형식을 벗어난 reason 예시\n")
            for cid, r in bad:
                p(f"- {cid} (label {r['label']}): {esc(r['reason'])}")

    body = "\n".join(out) + "\n"
    if a.out:
        Path(a.out).write_text(body, encoding="utf-8")
        print(f"wrote {a.out}")
    else:
        print(body)


if __name__ == "__main__":
    main()
