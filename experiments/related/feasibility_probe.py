#!/usr/bin/env python3
"""
오늘문득 M5-2d: Claude Code "strong-model feasibility probe" 준비 · 연결 · 평가. stdlib만 사용. 모델을 호출하지 않는다.

이것은 공정한 model benchmark가 아니다. 판정은 별도의 새 Claude Code session이 blind 파일만 보고 한다.

  python3 feasibility_probe.py make      # 1) blind 판정 패키지 + 비공개 mapping 생성
  (새 Claude Code session이 probe-blind/ 복사본에서 INSTRUCTIONS.md대로 157개 판정)
  python3 feasibility_probe.py check  --results <결과.jsonl>   # 형식만 확인 (정답 안 봄)
  python3 feasibility_probe.py join   --results <결과.jsonl>   # 2) 157/157 완료 후에만 정답과 연결
  python3 feasibility_probe.py report                          # 3) e5 · Qwen 2B judge_v1 · Claude Code 비교 리포트

파일:
  probe-blind/        판정 session에 주는 것 전부: INSTRUCTIONS.md, judge_v1.txt(복사본), blind_input.jsonl, check_results.py
                      blind_input 한 줄 = {"pair_id": "p001", "current": …, "past": …} — 그 외 정보 없음.
                      pair 순서는 query와 e5 순위를 섞은 고정 shuffle (같은 query의 후보가 붙어 있지 않음).
  probe-private/mapping.json   p001 → 원래 candidate id. 판정 session에는 주지 않는다.
  runs/claude-code-feasibility-judge_v1.jsonl        판정 결과 그대로 (pair_id, reason, label)
  runs/llm-claude-code-feasibility-judge_v1.jsonl    정답 쪽 id와 연결한 것 (llm_judge.py 형식 → compare_judges.py로 읽음)
  results-m5-2d-feasibility.md                       비교 리포트
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

import llm_judge as J

HERE = Path(__file__).resolve().parent
SALT = "m5-2d-feasibility-blind-v1"
PROMPT = HERE / "prompts" / "judge_v1.txt"
EXPECTED_PROMPT_SHA = "553ab6176c0293f9"
BLIND_KEYS = ["pair_id", "current", "past"]
RESULT_KEYS = {"pair_id", "reason", "label"}
RESULT_NAME = "claude-code-feasibility-judge_v1.jsonl"
QWEN_LOG = HERE / "runs" / "llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl"
WARNING = (
    "> ⚠️ **이 결과는 feasibility probe이며, 공정한 model benchmark가 아니다.** "
    "Claude 계열 모델이 이 benchmark(dataset v1.1의 문장 · label · note)의 설계에 참여했으므로 같은 계열의 판단 습관이 점수를 부풀릴 수 있다(contamination). "
    "또 Claude Code session에서의 판정은 temperature · 출력 길이 · 판정 간 독립성 등을 API 실행처럼 통제할 수 없다. "
    "dataset v1.1은 이미 여러 실험에 쓴 development benchmark이기도 하다. "
    "**이 결과는 \"더 강한 모델에서 질적으로 큰 개선이 보이는가\"를 확인하는 데만 쓰고, 수치 차이를 모델 성능이나 production 판단 근거로 해석하지 않는다.**\n"
)

CHECKER = r'''#!/usr/bin/env python3
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
'''

INSTRUCTIONS = """# 판정 작업 지시 (blind)

이 폴더의 파일만 사용해서, 157개의 (현재 기록, 과거 기록) 쌍을 하나씩 판정한다.

## 사용할 수 있는 파일 (이 폴더 안의 이것들뿐)

- `INSTRUCTIONS.md` — 이 문서
- `judge_v1.txt` — 판정 기준. `### SYSTEM` 부분이 너의 판정 지시이고, `### USER` 부분이 각 쌍이 주어지는 형식이다.
- `blind_input.jsonl` — 한 줄 = `{"pair_id", "current", "past"}`
- `check_results.py` — 결과 형식 검사
- `claude-code-feasibility-judge_v1.jsonl` — 네가 쓰는 결과 파일

## 하지 말 것

- 이 폴더 밖의 파일·폴더를 열거나 검색하지 않는다 (상위 폴더, 홈 폴더, git 저장소, 다른 프로젝트 포함). 웹도 쓰지 않는다.
- 판정에 쓰는 정보는 `judge_v1.txt`, 그 쌍의 `current`, 그 쌍의 `past` 셋뿐이다.
- 판정을 코드로 하지 않는다. 키워드 규칙, 유사도 계산, 다른 모델/API 호출, subagent 사용 금지. 네가 직접 읽고 판단한다.
  (파일을 읽고, 결과 줄을 덧붙이고, `check_results.py`를 실행하는 데에만 명령을 쓴다.)
- `judge_v1.txt`와 `blind_input.jsonl`을 수정하지 않는다. 판정 기준을 바꾸거나 보태지 않는다.
- 앞에서 쓴 결과를 고치지 않는다. 결과 파일은 덧붙이기만 한다.
- 앞의 판정을 참고해 뒤의 판정 기준을 조정하지 않는다. label 비율을 맞추려고 하지 않는다. 같은 `current`가 여러 번 나와도 각 쌍은 독립적으로 판정한다.

## 순서

1. `python3 check_results.py`를 실행한다. 첫 줄의 sha가 `553ab6176c0293f9`인지 확인한다. 다르면 멈추고 알린다.
2. `judge_v1.txt` 전체를 읽는다.
3. `blind_input.jsonl`을 앞에서부터 20줄씩 읽는다 (이미 판정한 것이 있으면 `check_results.py`가 알려주는 "다음" pair부터).
4. 각 쌍마다 `judge_v1.txt`의 SYSTEM 지시를 따르고, USER 형식의 `{current}`, `{past}` 자리에 그 쌍의 텍스트가 들어간 것으로 보고 판정한다.
   - 결과: `{"pair_id": "p001", "reason": "판정 근거 한국어 한 문장", "label": 0}` (label은 정수 0, 1, 2 중 하나)
   - reason을 먼저 정하고 label을 정한다. reason은 짧게 한 문장.
5. 20개를 판정할 때마다 결과 파일 끝에 한 줄씩(JSON Lines, UTF-8) 덧붙이고 `python3 check_results.py`를 실행한다. ERROR가 나오면 형식만 고쳐서 다시 확인한다 (이미 쓴 판정 내용은 바꾸지 않는다).
6. `완료 157/157 · 전부 완료`가 나오면 끝낸다. 결과를 요약하거나 분석하지 않는다.

중간에 session이 끊기면, 새 session에서 이 문서부터 다시 읽고 `check_results.py`가 알려주는 "다음" pair부터 이어서 한다.
"""


def sha256_file(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def load_ds(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def blind_order(ds):
    """원래 candidate id를 감추는 고정 shuffle. query 순서·e5 순위와 무관."""
    cands = [(q, c) for q in ds["queries"] for c in q["candidates"]]
    return sorted(cands, key=lambda qc: hashlib.sha256(f"{SALT}|{qc[1]['id']}".encode()).hexdigest())


def leak_problems(row, q, c):
    """blind row에 정답·메타 정보가 들어갔는지."""
    p = []
    if list(row) != BLIND_KEYS:
        p.append(f"keys {list(row)}")
    if not re.fullmatch(r"p\d{3}", row["pair_id"]):
        p.append("pair_id 형식")
    blob = json.dumps(row, ensure_ascii=False)
    # category/emotion 값은 일상 단어라 본문에 자연스럽게 나올 수 있다 → key가 없는 것으로 확인 (위 keys 검사)
    for k in ("id", "date", "case", "note"):
        for src in (q, c):
            v = src.get(k)
            if isinstance(v, str) and len(v) >= 3 and v in blob and v not in (q["text"], c["text"]):
                p.append(f"{k} 값 포함")
    if row["current"] != q["text"] or row["past"] != c["text"]:
        p.append("text 불일치")
    return p


def cmd_make(a):
    _, _, sha = J.load_prompt(PROMPT)
    if sha != EXPECTED_PROMPT_SHA:
        sys.exit(f"judge_v1 sha {sha} ≠ {EXPECTED_PROMPT_SHA}")
    ds = load_ds(a.dataset)
    blind, priv = Path(a.blind_dir), Path(a.private_dir)
    if (blind / RESULT_NAME).exists():
        sys.exit(f"{blind / RESULT_NAME}가 이미 있음 — 판정이 시작된 패키지는 다시 만들지 않는다.")
    rows, mapping = [], {}
    for i, (q, c) in enumerate(blind_order(ds), 1):
        pid = f"p{i:03d}"
        row = {"pair_id": pid, "current": q["text"], "past": c["text"]}
        pr = leak_problems(row, q, c)
        if pr:
            sys.exit(f"{pid}: {pr}")
        rows.append(row)
        mapping[pid] = c["id"]
    blind.mkdir(parents=True, exist_ok=True)
    priv.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(PROMPT, blind / "judge_v1.txt")
    (blind / "blind_input.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
    (blind / "check_results.py").write_text(CHECKER, encoding="utf-8")
    (blind / "INSTRUCTIONS.md").write_text(INSTRUCTIONS, encoding="utf-8")
    info = {"salt": SALT, "dataset_version": ds.get("version"), "dataset_md5": hashlib.md5(Path(a.dataset).read_bytes()).hexdigest(),
            "prompt_sha": sha, "blind_input_sha256": sha256_file(blind / "blind_input.jsonl"), "n": len(rows), "mapping": mapping}
    (priv / "mapping.json").write_text(json.dumps(info, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    groups = sum(mapping[f"p{i:03d}"].split("-")[0] == mapping[f"p{i + 1:03d}"].split("-")[0] for i in range(1, len(rows)))
    print(f"blind 패키지: {blind} ({len(rows)}쌍, keys {BLIND_KEYS}, 같은 query가 연속인 경우 {groups}/{len(rows) - 1})")
    print(f"mapping: {priv / 'mapping.json'} (판정 session에 주지 않음)")
    print(f"prompt sha {sha} · blind_input sha256 {info['blind_input_sha256'][:16]}")


def run_checker(blind, results):
    r = subprocess.run([sys.executable, str(Path(blind) / "check_results.py"), str(results)], capture_output=True, text=True)
    return r.returncode, r.stdout


def cmd_check(a):
    rc, out = run_checker(a.blind_dir, a.results)
    print(out, end="")
    return rc


def cmd_join(a):
    priv = json.loads((Path(a.private_dir) / "mapping.json").read_text(encoding="utf-8"))
    blind = Path(a.blind_dir)
    # 판정 session이 받은 파일이 만들 때 그대로인지
    if sha256_file(blind / "blind_input.jsonl") != priv["blind_input_sha256"]:
        sys.exit("blind_input.jsonl이 생성 후 바뀜")
    if J.load_prompt(blind / "judge_v1.txt")[2] != EXPECTED_PROMPT_SHA:
        sys.exit("blind 폴더의 judge_v1.txt가 바뀜")
    rc, out = run_checker(blind, a.results)
    if rc != 0 or f"완료 {priv['n']}/{priv['n']}" not in out:
        sys.exit("판정이 완료되지 않았거나 형식 오류 — 157/157 전에는 정답과 연결하지 않는다.\n" + out)
    ds = load_ds(a.dataset)
    if hashlib.md5(Path(a.dataset).read_bytes()).hexdigest() != priv["dataset_md5"]:
        sys.exit("dataset이 blind 패키지 생성 때와 다름")
    e5 = json.loads(Path(a.e5).read_text(encoding="utf-8"))
    rank = {c["id"]: (q, r) for q, c, r in J.plan(ds, e5)}
    results = [json.loads(l) for l in Path(a.results).read_text(encoding="utf-8").splitlines() if l.strip()]

    runs = Path(a.runs_dir)
    raw_out, joined_out = runs / RESULT_NAME, runs / "llm-claude-code-feasibility-judge_v1.jsonl"
    meta = {"type": "meta", "provider": "claude-code", "model": "claude-code-session (feasibility probe, 통제되지 않은 실행)",
            "options": {"mode": "blind manual judging in a fresh Claude Code session", "attempts": 1},
            "prompt_file": "judge_v1.txt", "prompt_sha": EXPECTED_PROMPT_SHA, "dataset_version": ds.get("version"),
            "e5_run": Path(a.e5).name, "inputs": "query text + candidate text only (blind, opaque pair id)",
            "blind_input_sha256": priv["blind_input_sha256"], "results_sha256": sha256_file(a.results)}
    lines = [json.dumps(meta, ensure_ascii=False)]
    for r in results:
        cid = priv["mapping"][r["pair_id"]]
        q, rk = rank[cid]
        lines.append(json.dumps({"type": "judgment", "qid": q["id"], "cid": cid, "attempt": 1, "e5_rank": rk, "pair_id": r["pair_id"],
                                 "status": "ok", "label": r["label"], "reason": r["reason"].strip(), "error": None}, ensure_ascii=False))
    body = "\n".join(lines) + "\n"
    raw = Path(a.results).read_bytes()
    for path, content in ((raw_out, raw), (joined_out, body.encode("utf-8"))):
        if path.exists() and path.read_bytes() != content:
            sys.exit(f"{path}가 이미 있고 내용이 다름 — 덮어쓰지 않는다.")
    runs.mkdir(exist_ok=True)
    raw_out.write_bytes(raw)
    joined_out.write_bytes(body.encode("utf-8"))
    print(f"연결 완료: {len(results)}개\n  판정 원본: {raw_out}\n  연결본: {joined_out}")


def cmd_report(a):
    joined = Path(a.runs_dir) / "llm-claude-code-feasibility-judge_v1.jsonl"
    if not joined.exists():
        sys.exit("먼저 join")
    tmp = Path(a.out).with_suffix(".tmp.md")
    r = subprocess.run([sys.executable, str(HERE / "compare_judges.py"), "--dataset", str(a.dataset), "--e5", str(a.e5),
                        "--log", f"qwen2b_judge_v1={a.qwen_log}", "--log", f"claude_code_probe={joined}",
                        "--diag", "none", "--out", str(tmp)], capture_output=True, text=True, cwd=HERE)
    if r.returncode != 0:
        sys.exit(r.stderr[-1500:])
    text = tmp.read_text(encoding="utf-8")
    tmp.unlink()
    lines = text.splitlines()
    # compare_judges.py의 제목/경고(judge_v2용 문구)를 이 실험의 것으로 바꾼다. 나머지 표는 그대로.
    assert lines[0].startswith("# ") and any(l.startswith("> ⚠️") for l in lines[:5])
    lines[0] = "# M5-2d 결과 · strong-model feasibility probe (e5 Top5 vs Qwen 2B judge_v1 vs Claude Code judge_v1)"
    lines = [WARNING.rstrip("\n") if l.startswith("> ⚠️") else l for l in lines]
    Path(a.out).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {a.out}")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", choices=["make", "check", "join", "report"])
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--e5", default=HERE / "runs" / "e5-small-ko.v1.1.json")
    ap.add_argument("--qwen-log", default=QWEN_LOG)
    ap.add_argument("--blind-dir", default=HERE / "probe-blind")
    ap.add_argument("--private-dir", default=HERE / "probe-private")
    ap.add_argument("--runs-dir", default=HERE / "runs")
    ap.add_argument("--results", help="판정 결과 파일 (기본: <blind-dir>/" + RESULT_NAME + ")")
    ap.add_argument("--out", default=HERE / "results-m5-2d-feasibility.md")
    a = ap.parse_args(argv)
    a.results = Path(a.results) if a.results else Path(a.blind_dir) / RESULT_NAME
    return {"make": cmd_make, "check": cmd_check, "join": cmd_join, "report": cmd_report}[a.cmd](a) or 0


if __name__ == "__main__":
    sys.exit(main())
