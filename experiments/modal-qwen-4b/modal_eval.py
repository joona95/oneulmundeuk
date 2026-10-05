#!/usr/bin/env python3
"""
M5-4a 리포트: Modal Qwen3.5-4B(BF16, vLLM) judge_v1 vs Mac/Android Qwen3.5-2B judge_v1. 모델 · Modal을 호출하지 않는다.

  python3 modal_eval.py                         # 가장 최근 runs/modal-*.jsonl
  python3 modal_eval.py --run runs/modal-<시각>.jsonl

품질: 기존과 같은 선택 규칙 (e5 순서에서 label 2만, 최대 5개) → eval.compute 지표 (selector_experiment.evaluate). 실패는 2가 아님.
비용: Modal GPU 초당 가격 × 시간 (CPU · 메모리 요금은 별도라 GPU 기준 하한). 실제 청구액은 Modal dashboard로 확인.
"""
import argparse
import csv
import json
import statistics
from collections import Counter
from pathlib import Path

import judge_client as C
import selector_experiment as S
from llm_report import F_TYPES, read_log

HERE = Path(__file__).resolve().parent
MAC_LOG = C.RELATED / "runs" / "llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl"
ANDROID_LOG = HERE.parent / "android-qwen-poc" / "runs" / "llm-android-qwen3.5-2b-q4_K_M-judge_v1.jsonl"
ANDROID_SESSION = HERE.parent / "android-qwen-poc" / "runs" / "session-20261005-164627.jsonl"
CONVERTED = HERE / "runs" / "llm-modal-qwen3.5-4b-bf16-judge_v1.jsonl"
CASES = ["repeat_low_value", "lexical_trap", "resolve_action", "worry_outcome", "implicit_link", "recurring", "change", "reversal"]
NOLAB2 = ("q02", "q15", "q16", "q17")
KEYS = [("Good@5", "Good@5 (2 회수율, query 평균)"), ("nDCG@5", "nDCG@5 (gain 2→3, 1→1)"), ("Worth%", "Worth% (보여준 것 중 2 비율)"),
        ("Bad%", "Bad% (보여준 것 중 0 비율, 전체)"), ("Top1=0", "Top1=0 (1위에 0을 올린 query 비율)"),
        ("Quiet miss", "Quiet miss (2 없는 query에서 보여준 개수 평균)")]


def labels_from_log(path):
    _, latest = read_log(path)
    return {cid: (r["label"] if r["status"] == "ok" else None) for (cid, at), r in latest.items() if at == 1}


def load_run(path, plan):
    lines = [json.loads(l) for l in Path(path).read_text(encoding="utf-8").splitlines() if l.strip()]
    by = {}
    for r in lines:
        by.setdefault(r["type"], []).append(r)
    meta = by["meta"][0]
    if meta["prompt_sha"] != C.EXPECTED_PROMPT_SHA:
        raise SystemExit(f"prompt sha {meta['prompt_sha']} ≠ {C.EXPECTED_PROMPT_SHA}")
    seq = [r for r in by.get("judgment", []) if r["task"] == "seq"]
    if [r["cid"] for r in seq] != [c["id"] for _, c, _ in plan]:
        raise SystemExit(f"seq가 157쌍을 순서대로 다 담고 있지 않음 ({len(seq)}개) — 중단된 run?")
    return meta, by, seq


def write_converted(path, meta, seq):
    out = [json.dumps({"type": "meta", "model": f"{meta['model']}@{meta.get('resolved_revision') or meta['model_revision']} ({meta['precision']}, {meta['engine']}, {meta['gpu']})",
                       "options": {"vllm_args": meta["vllm_args"], "request": meta["request_example"]}, "prompt_file": "judge_v1.txt",
                       "prompt_sha": meta["prompt_sha"], "dataset_version": "1.1", "e5_run": "e5-small-ko.v1.1.json",
                       "inputs": "query text + candidate text only"}, ensure_ascii=False)]
    for r in seq:
        out.append(json.dumps({"type": "judgment", "qid": r["qid"], "cid": r["cid"], "attempt": 1, "e5_rank": r["e5_rank"], "status": r["status"],
                               "label": r["label"], "reason": r["reason"], "error": r["error"], "raw": r.get("raw"), "elapsed_ms": r["wall_ms"],
                               "prompt_eval_count": r.get("prompt_tokens"), "eval_count": r.get("completion_tokens")}, ensure_ascii=False))
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


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(int(len(xs) * p), len(xs) - 1)]


def android_latency(path):
    if not Path(path).exists():
        return None
    recs = [json.loads(l) for l in Path(path).read_text(encoding="utf-8").splitlines()]
    w = [r["wall_ms"] for r in recs if r.get("type") == "judgment" and r.get("task") == "all"]
    hwm = max((r.get("mem") or {}).get("VmHWM", 0) for r in recs if r.get("type") in ("judgment", "load"))
    return {"median": statistics.median(w), "p90": pct(w, .9), "max": max(w), "mean": statistics.mean(w), "total": sum(w), "hwm": hwm}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--run")
    ap.add_argument("--out", default=HERE / "results-m5-4a-modal-4b.md")
    ap.add_argument("--csv", default=HERE / "runs" / "modal-4b-judge_v1-pairs.csv")
    ap.add_argument("--converted", default=CONVERTED)
    ap.add_argument("--android-log", default=ANDROID_LOG)
    ap.add_argument("--android-session", default=ANDROID_SESSION)
    a = ap.parse_args(argv)

    ds, plan = C.load_plan()
    run = Path(a.run) if a.run else max(Path(HERE / "runs").glob("modal-*.jsonl"), default=None)
    if run is None:
        raise SystemExit("runs/modal-*.jsonl 없음. 먼저 `modal run modal_judge.py`")
    meta, by, seq = load_run(run, plan)
    end = (by.get("end") or [{}])[0]
    meta["resolved_revision"] = end.get("resolved_revision")
    write_converted(Path(a.converted), meta, seq)

    qs = ds["queries"]
    order = {}
    for q, c, _ in plan:
        order.setdefault(q["id"], []).append(c["id"])
    truth = {c["id"]: c for q in qs for c in q["candidates"]}
    judges = {"Mac 2B (Ollama Q4_K_M)": labels_from_log(MAC_LOG), "Android 2B (llama.cpp Q4_K_M)": labels_from_log(a.android_log),
              "Modal 4B (vLLM BF16)": {r["cid"]: (r["label"] if r["status"] == "ok" else None) for r in seq}}
    sel = {"e5 Top5": {q["id"]: order[q["id"]][:5] for q in qs}}
    for n, lab in judges.items():
        sel[n] = {q["id"]: [c for c in order[q["id"]] if lab.get(c) == 2][:5] for q in qs}
    names = list(sel)
    res = {n: S.evaluate(ds, sel[n], 5) for n in names}
    total2 = sum(c["label"] == 2 for c in truth.values())
    m4 = judges["Modal 4B (vLLM BF16)"]

    wall = [r["wall_ms"] for r in seq]
    ttft = [r["ttft_ms"] for r in seq if r.get("ttft_ms") is not None]
    gen = [r["gen_ms"] for r in seq if r.get("gen_ms") is not None]
    ctoks = [r["completion_tokens"] for r in seq if r.get("completion_tokens")]
    errs = [r for r in seq if r["status"] != "ok"]
    bursts = by.get("burst", [])
    cold = (by.get("cold_start") or [{}])[0].get("ready_s")
    seq_done = (by.get("seq_done") or [{}])[0]
    price = meta["gpu_price_per_s"]
    andr = android_latency(a.android_session)

    out = []
    p = out.append
    p("# M5-4a 결과 · Modal Qwen3.5-4B judge_v1 vs Qwen3.5-2B (Mac · Android)\n")
    p("> ⚠️ dataset v1.1은 여러 실험에 이미 쓴 development benchmark다. 이 비교는 judge 후보(모델 크기 · 실행 위치)를 고르는 근거이며 일반화 성능 추정이 아니다. "
      "Modal 4B는 모델 크기(2B → 4B)와 함께 정밀도(Q4_K_M → BF16) · 엔진(Ollama/llama.cpp → vLLM GPU)도 바뀌었다. judge_v1 prompt · 요청 조건 · 선택 규칙은 같다.\n")
    p(f"- Modal: `{meta['model']}` revision `{meta.get('resolved_revision') or meta['model_revision']}` · {meta['precision']} · {meta['engine']} · GPU {meta['gpu']} (${price}/s)")
    p(f"- vLLM: `{' '.join(meta['vllm_args'])}`")
    p(f"- 요청: temperature 0 · seed 7 · max_tokens 192 · json_schema · enable_thinking=false · prompt sha {meta['prompt_sha']} · run `{run.name}`\n")

    p("## 1. 품질 (frozen 157쌍, label 2만 · e5 순서 · 최대 5개)\n")
    p("| metric | " + " | ".join(f"`{n}`" for n in names) + " |")
    p("| --- |" + " --- |" * len(names))
    for label, k in KEYS:
        p(f"| {label} | " + " | ".join(f"{res[n]['s'][k]:.2f}" for n in names) + " |")
    p("| 보여준 총 개수 | " + " | ".join(str(res[n]["shown"]) for n in names) + " |")
    p(f"| label 2 보여줌 (/{total2}) | " + " | ".join(str(res[n]["good"]) for n in names) + " |")
    p("| label 0 보여줌 | " + " | ".join(str(res[n]["bad"]) for n in names) + " |")
    p("| F7 0개 반환 | " + " | ".join(f"{res[n]['f7']}/{res[n]['f7_total']}" for n in names) + " |")
    p(f"\nModal 4B 판정: ok {len(seq) - len(errs)}/157" + (f" · 실패 {Counter(r['error'].split(':')[0] for r in errs)}" if errs else "") +
      f" · thinking 출력 {sum(bool(r.get('thinking_present')) for r in seq)}건\n")

    p("## 2. failure case별 (보여준 수 / 전체)\n")
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

    p("\n## 3. confusion matrix (정답 × 판정)\n")
    for n, lab in judges.items():
        cm = Counter((c["label"], lab.get(cid)) for cid, c in truth.items())
        p(f"`{n}`\n")
        p("| 정답 \\ 판정 | 2 | 1 | 0 | 실패 |")
        p("| --- | --- | --- | --- | --- |")
        for g in (2, 1, 0):
            p(f"| **{g}** | " + " | ".join(str(cm[(g, x)]) for x in (2, 1, 0, None)) + " |")
        p("")
    p("label 일치 (참고, 판단 기준은 1·2절의 제품 지표)\n")
    p("| 비교 | 일치 | κ |")
    p("| --- | --- | --- |")
    jn = list(judges)
    for x, y in ((jn[2], jn[0]), (jn[2], jn[1]), (jn[0], jn[1])):
        both = [(judges[x][c], judges[y][c]) for c in truth if judges[x].get(c) is not None and judges[y].get(c) is not None]
        p(f"| {x} vs {y} | {sum(u == v for u, v in both)}/{len(both)} | " + (f"{kappa(both):.2f}" if both else "–") + " |")

    p("\n## 4. latency\n")
    p("| 항목 | Android 2B (기기 CPU) | Modal 4B (L4, Mac에서 호출) |")
    p("| --- | --- | --- |")
    p(f"| 시작 | model load 2.6 s | 콜드 스타트 {cold} s (컨테이너 · 모델 로드 · vLLM 준비, 첫 실행은 다운로드 포함) |")
    p(f"| 1쌍 순차 median / p90 / max | " + (f"{andr['median'] / 1000:.2f} / {andr['p90'] / 1000:.2f} / {andr['max'] / 1000:.2f} s" if andr else "–") +
      f" | {statistics.median(wall) / 1000:.2f} / {pct(wall, .9) / 1000:.2f} / {max(wall) / 1000:.2f} s |")
    if ttft and gen:
        p(f"| 1쌍 구성 (Modal) | – | TTFT median {statistics.median(ttft)} ms (네트워크 + 대기 + prompt) · 생성 median {statistics.median(gen)} ms · 출력 평균 {statistics.mean(ctoks):.0f} tok |")
    p(f"| 157쌍 순차 합계 | " + (f"{andr['total'] / 1000:.1f} s" if andr else "–") + f" | {sum(wall) / 1000:.1f} s |")
    p(f"| 저장 1회 = Top 30 판정 | 순차만 가능: 약 {andr['mean'] * 30 / 1000:.0f} s (평균 × 30)" if andr else "| 저장 1회 = Top 30 판정 | –")
    out[-1] += " | " + (" · ".join(f"{b['wall_s']:.1f} s" for b in bursts) + " (30쌍 동시, 회차별)" if bursts else "–") + " |"
    if andr:
        p(f"| peak 메모리 | 기기 RSS {andr['hwm']:.0f} MB | 서버 GPU (기기 부담 없음) |")
    sm = seq_done.get("server_metrics") or {}
    if sm.get("vllm:e2e_request_latency_seconds_count"):
        n = sm["vllm:e2e_request_latency_seconds_count"]
        p(f"| 서버 내부 (vLLM metrics, 순차 평균) | – | e2e {sm.get('vllm:e2e_request_latency_seconds_sum', 0) / n:.2f} s · prefill {sm.get('vllm:request_prefill_time_seconds_sum', 0) / n:.3f} s · decode {sm.get('vllm:request_decode_time_seconds_sum', 0) / n:.2f} s |")

    p("\n## 5. 추론 비용 (Modal GPU 요금 기준 하한)\n")
    p(f"GPU {meta['gpu']} ${price}/s (${price * 3600:.4f}/h). CPU · 메모리 요금과 컨테이너 대기(scaledown {end.get('scaledown_s', 120)} s)는 별도라 실제 청구액은 이보다 크다.\n")
    p("| 항목 | 계산 | 비용 |")
    p("| --- | --- | --- |")
    p(f"| 1쌍 (순차, 서버 독점) | median {statistics.median(wall) / 1000:.2f} s × ${price} | ${statistics.median(wall) / 1000 * price:.5f} |")
    if bursts:
        bw = statistics.median(b["wall_s"] for b in bursts)
        p(f"| 저장 1회 = Top 30 (동시) | burst median {bw:.1f} s × ${price} | ${bw * price:.4f} |")
        p(f"| 저장 1,000회 | 위 × 1,000 (요청이 몰리지 않아 매번 GPU를 혼자 쓴다고 가정) | ${bw * price * 1000:.2f} |")
    if cold:
        p(f"| 콜드 스타트 1회 | {cold} s × ${price} | ${cold * price:.4f} |")
    if end.get("client_wall_s"):
        tot = end["client_wall_s"] + end.get("scaledown_s", 120)
        p(f"| 이번 실행 전체 (GPU 점유 추정) | ({end['client_wall_s']} s + scaledown {end.get('scaledown_s', 120)} s) × ${price} | ${tot * price:.3f} |")
    p("| Android 2B | 서버 비용 없음 (기기 CPU · 배터리 · 1.27 GB 모델 저장 · RSS 2.7 GB) | $0 |")

    body = "\n".join(out) + "\n"
    if Path(a.out).exists() and Path(a.out).read_text(encoding="utf-8") != body:
        raise SystemExit(f"{a.out}가 이미 있고 내용이 다름 — 덮어쓰지 않는다.")
    Path(a.out).write_text(body, encoding="utf-8")
    with open(a.csv, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["pair_id", "case", "truth", "mac_2b", "android_2b", "modal_4b", "status", "wall_ms", "ttft_ms", "gen_ms", "prompt_tokens", "completion_tokens"])
        for r in seq:
            c = truth[r["cid"]]
            w.writerow([r["cid"], c["case"], c["label"], judges[jn[0]].get(r["cid"]), judges[jn[1]].get(r["cid"]), r["label"], r["status"],
                        r["wall_ms"], r.get("ttft_ms"), r.get("gen_ms"), r.get("prompt_tokens"), r.get("completion_tokens")])
    print(body)
    print(f"wrote {a.out} · {a.csv} · {a.converted}")
    return 0


if __name__ == "__main__":
    main()
