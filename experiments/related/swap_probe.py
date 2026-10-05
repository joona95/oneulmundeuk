#!/usr/bin/env python3
"""
오늘문득 M5-2a 보조: warm 상태 모델의 추론 중 swap 증가 확인 (판정 품질 실험이 아님).

  python3 swap_probe.py --model qwen3.5:2b-q4_K_M

- llm_judge.py의 judge_once / prompt / SCHEMA / options를 그대로 사용한다 (설정을 바꾸지 않음).
- 서로 다른 benchmark pair 5건을 연속 판정하고, 호출 전후 swap used와 Ollama load_duration을 기록한다.
- 결과는 runs/swap-probe-<model>.jsonl (smoke / 본실험 파일과 분리). 본실험 resume 기록에는 들어가지 않는다.
- 이 스크립트는 swap 증가로 중단하지 않는다 (관찰용). llm_judge.py의 guard 기본값은 그대로다.
- load_duration이 크면(> 1초) 그 호출에서 모델이 다시 로드된 것으로 본다.
"""
import argparse
import datetime as dt
import json
import time
from pathlib import Path

import llm_judge as J

HERE = Path(__file__).resolve().parent
# smoke pair(q01-g)와 겹치지 않게, 서로 다른 query · case에서 고른 고정 5건
PROBE = ["q02-a", "q05-j", "q09-b", "q13-b", "q16-c"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="qwen3.5:2b-q4_K_M")
    ap.add_argument("--host", default="http://127.0.0.1:11434")
    ap.add_argument("--out")
    a = ap.parse_args()
    # llm_judge.py 기본값과 동일한 호출 설정
    opt = argparse.Namespace(model=a.model, host=a.host, num_ctx=2048, seed=7, num_predict=192,
                             keep_alive="10m", timeout=120, allow_cpu=False)

    system, user_tpl, sha = J.load_prompt(HERE / "prompts" / "judge_v1.txt")
    ds = json.loads((HERE / "dataset.json").read_text(encoding="utf-8"))
    e5 = json.loads((HERE / "runs" / "e5-small-ko.v1.1.json").read_text(encoding="utf-8"))
    pairs = {c["id"]: (q, c, r) for q, c, r in J.plan(ds, e5)}
    out = Path(a.out) if a.out else HERE / "runs" / f"swap-probe-{J.slug(a.model)}.jsonl"

    # judge_once는 그대로 쓰고, 응답 원본(load_duration 등)만 옆에서 받아 둔다
    last = {}
    real = J.http_json

    def spy(url, body=None, timeout=120):
        r = real(url, body, timeout)
        if url.endswith("/api/chat"):
            last.clear()
            last.update(r)
        return r

    J.http_json = spy

    st0 = J.model_state(opt)
    print(f"model {a.model} · prompt {sha} · 저장 {out}")
    print(f"시작 ollama ps: {st0}")
    if not st0.get("loaded"):
        print("⚠️ 모델이 아직 메모리에 없음 → 1번째 호출에 로드 시간이 포함됩니다 (warm 측정이 아님).")
    s_start = J.swap_used_mb()
    print(f"시작 swap used: {s_start} MB\n")

    rows = []
    for i, cid in enumerate(PROBE, 1):
        q, c, rank = pairs[cid]
        before = J.swap_used_mb()
        t0 = time.time()
        rec = J.judge_once(opt, system, user_tpl, q["text"], c["text"])
        after = J.swap_used_mb()
        st = J.model_state(opt)
        load_ms = round((last.get("load_duration") or 0) / 1e6)
        row = {
            "i": i, "qid": q["id"], "cid": cid, "status": rec["status"], "label": rec["label"], "reason": rec["reason"],
            "error": rec["error"], "elapsed_ms": rec.get("elapsed_ms"), "load_duration_ms": load_ms,
            "reloaded": load_ms > 1000, "swap_before_mb": before, "swap_after_mb": after,
            "swap_delta_mb": None if before is None or after is None else round(after - before, 1),
            "ps_after": st, "model": a.model, "prompt_sha": sha, "ts": dt.datetime.now().isoformat(timespec="seconds"),
        }
        rows.append(row)
        with out.open("a", encoding="utf-8") as f:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
        shown = f"label {rec['label']}" if rec["status"] == "ok" else f"ERROR {rec['error']}"
        print(f"{i}/5 {cid:6s} {rec.get('elapsed_ms', 0):6d}ms load {load_ms}ms "
              f"swap {before}→{after} ({row['swap_delta_mb']:+} MB) GPU {st.get('gpu_pct')}% · {shown}"
              if row["swap_delta_mb"] is not None else
              f"{i}/5 {cid:6s} {rec.get('elapsed_ms', 0):6d}ms load {load_ms}ms swap n/a GPU {st.get('gpu_pct')}% · {shown}")
        if rec["status"] == "ok":
            print(f"      reason: {rec['reason']}")

    s_end = J.swap_used_mb()
    print("\n── 요약 ──")
    print(f"시작 swap {s_start} MB · 종료 swap {s_end} MB · 총 증가 "
          f"{'n/a' if s_start is None or s_end is None else f'{s_end - s_start:+.1f} MB'}")
    print("호출별 증가: " + ", ".join(f"{r['cid']} {r['swap_delta_mb']:+}" if r["swap_delta_mb"] is not None else f"{r['cid']} n/a" for r in rows))
    el = [r["elapsed_ms"] for r in rows if r["status"] == "ok"]
    if el:
        print(f"elapsed: 최소 {min(el)}ms · 최대 {max(el)}ms · 평균 {sum(el) // len(el)}ms")
    rel = [r["cid"] for r in rows if r["reloaded"]]
    print(f"reload: {'없음 (5회 모두 warm)' if not rel else '있음 → ' + ', '.join(rel)}")
    print(f"실패: {sum(r['status'] != 'ok' for r in rows)}건 · 종료 ollama ps: {J.model_state(opt)}")


if __name__ == "__main__":
    main()
