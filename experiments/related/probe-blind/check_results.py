#!/usr/bin/env python3
"""판정 결과 형식 검사. 정답 정보는 읽지 않는다 (이 폴더의 blind_input.jsonl과 결과 파일만 읽음).
  python3 check_results.py [결과 파일, 기본 claude-code-feasibility-judge_v1.jsonl]"""
import hashlib, json, sys
from pathlib import Path
D = Path(__file__).resolve().parent
res = Path(sys.argv[1]) if len(sys.argv) > 1 else D / "claude-code-feasibility-judge_v1.jsonl"
ids = [json.loads(l)["pair_id"] for l in (D / "blind_input.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
print("judge_v1.txt sha256[:16] =", hashlib.sha256((D / "judge_v1.txt").read_bytes()).hexdigest()[:16], "(553ab6176c0293f9 이어야 함)")
errs, seen = [], []
rows = res.read_text(encoding="utf-8").splitlines() if res.exists() else []
for n, line in enumerate(rows, 1):
    if not line.strip():
        errs.append(f"{n}행: 빈 줄"); continue
    try:
        r = json.loads(line)
    except json.JSONDecodeError as e:
        errs.append(f"{n}행: JSON 오류 {e}"); continue
    if not isinstance(r, dict) or set(r) != {"pair_id", "reason", "label"}:
        errs.append(f"{n}행: key는 pair_id, reason, label 셋뿐이어야 함"); continue
    lab, rea, pid = r["label"], r["reason"], r["pair_id"]
    if pid not in ids: errs.append(f"{n}행: 모르는 pair_id {pid!r}")
    elif pid in seen: errs.append(f"{n}행: 중복 pair_id {pid}")
    if not (isinstance(lab, int) and not isinstance(lab, bool) and lab in (0, 1, 2)): errs.append(f"{n}행: label은 정수 0/1/2")
    if not (isinstance(rea, str) and rea.strip()): errs.append(f"{n}행: reason이 비어 있음")
    seen.append(pid)
if seen != ids[:len(seen)]:
    errs.append("pair_id가 blind_input.jsonl 순서와 다름 (앞에서부터 순서대로 판정해야 함)")
for e in errs: print("✗", e)
nxt = ids[len(seen)] if len(seen) < len(ids) else None
print(f"{'OK' if not errs else 'ERROR'} · 완료 {len(seen)}/{len(ids)}" + (f" · 다음 {nxt}" if nxt else " · 전부 완료"))
sys.exit(1 if errs else 0)
