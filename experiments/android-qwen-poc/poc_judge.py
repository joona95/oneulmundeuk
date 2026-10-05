#!/usr/bin/env python3
"""
Android on-device Qwen3.5 2B PoC: 실제 기기에서 llama.cpp llama-server를 띄우고, Mac에서 adb로 judge_v1 판정을 보내 측정한다.
모델은 기기 안에서만 돈다 (Mac은 adb forward로 요청만 보냄). production 앱과 무관. stdlib만 사용.

  python3 poc_judge.py session --tasks one,bench10,bench30,parity
  python3 poc_judge.py session --tasks one --current "현재 기록" --past "과거 기록"
  python3 poc_judge.py session --tasks all          # frozen 157쌍 전체 (→ android_eval.py)
  python3 poc_judge.py dry-run                # 보낼 요청 · 선택된 parity pair만 출력 (기기 불필요)

session = 서버 1회 시작(로드 시간 측정) → 같은 모델 instance로 task들을 순서대로 실행 → 서버 종료.
기록: runs/session-<시각>.jsonl (+ 서버 로그), 마지막에 요약 출력.

고정 조건 (Mac/Ollama judge_v1과 같게): judge_v1.txt (sha 553ab6176c0293f9) system/user 그대로 · temperature 0 · seed 7 ·
context 2048 · 출력 최대 192 · thinking off (enable_thinking=false) · JSON schema {reason, label 0/1/2} · 출력 검증 규칙 동일.
"""
import argparse
import datetime as dt
import json
import re
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
RELATED = HERE.parent / "related"
sys.path.insert(0, str(RELATED))
import llm_judge as J  # noqa: E402  (읽기만: prompt 로딩 · 메시지 · schema · 판정 순서)
import strong_judge as SJ  # noqa: E402  (judge_v1과 같은 출력 검증 규칙, 테스트로 고정됨)

EXPECTED_PROMPT_SHA = "553ab6176c0293f9"
DEVICE_DIR = "/data/local/tmp/qwen-poc"
DEVICE_MODEL = f"{DEVICE_DIR}/models/qwen3.5-2b-q4_K_M.gguf"
QWEN_LOG = RELATED / "runs" / "llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl"
N_CTX, N_PREDICT, SEED = 2048, 192, 7
# parity pair 선택 규칙 (결과를 보기 전에 고정): case마다 1쌍, 아직 쓰지 않은 query 우선, 그중 candidate id가 가장 작은 것 → 10쌍
PARITY_CASES = ["repeat_low_value", "lexical_trap", "unrelated", "ambiguous", "resolve_action", "worry_outcome",
                "implicit_link", "change", "reversal", "recurring"]


# ── request ──────────────────────────────────────────────────────────────────
def request_body(system, user_tpl, current, past):
    return {
        "messages": J.messages(system, user_tpl, current, past),
        "temperature": 0,
        "seed": SEED,
        "max_tokens": N_PREDICT,
        "response_format": {"type": "json_schema", "json_schema": {"name": "judgment", "schema": J.SCHEMA}},
        "chat_template_kwargs": {"enable_thinking": False},
        "cache_prompt": True,
        "stream": False,
    }


def http(url, body=None, timeout=600):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, json.loads(r.read().decode("utf-8") or "{}")


def judge(base, system, user_tpl, current, past, timeout=600):
    t0 = time.time()
    try:
        _, resp = http(f"{base}/v1/chat/completions", request_body(system, user_tpl, current, past), timeout)
    except urllib.error.HTTPError as e:
        return {"status": "error", "label": None, "reason": None, "raw": None, "wall_ms": int((time.time() - t0) * 1000),
                "error": f"http {e.code}: {e.read().decode('utf-8', 'replace')[:300]}"}
    except Exception as e:
        return {"status": "error", "label": None, "reason": None, "raw": None, "wall_ms": int((time.time() - t0) * 1000),
                "error": f"{type(e).__name__}: {e}"}
    wall = int((time.time() - t0) * 1000)
    choice = (resp.get("choices") or [{}])[0]
    msg = choice.get("message") or {}
    finish = choice.get("finish_reason")
    rec = SJ.validate(msg.get("content") or "", finish == "length")
    tm, usage = resp.get("timings") or {}, resp.get("usage") or {}
    rec.update(wall_ms=wall, finish_reason=finish, thinking_present=bool(msg.get("reasoning_content")),
               prompt_tokens=usage.get("prompt_tokens"), completion_tokens=usage.get("completion_tokens"),
               prompt_n=tm.get("prompt_n"), prompt_ms=tm.get("prompt_ms"), predicted_n=tm.get("predicted_n"),
               predicted_ms=tm.get("predicted_ms"), cache_n=tm.get("cache_n"))
    return rec


# ── device ───────────────────────────────────────────────────────────────────
class Adb:
    def __init__(self, serial=None):
        self.base = ["adb"] + (["-s", serial] if serial else [])

    def shell(self, cmd, timeout=30):
        r = subprocess.run(self.base + ["shell", cmd], capture_output=True, text=True, timeout=timeout)
        return r.stdout.strip()

    def forward(self, port):
        subprocess.run(self.base + ["forward", f"tcp:{port}", f"tcp:{port}"], check=True, capture_output=True)

    def start_server(self, args, log_path):
        log = open(log_path, "w", encoding="utf-8")
        cmd = f"cd {DEVICE_DIR} && LD_LIBRARY_PATH=lib exec ./bin/llama-server {' '.join(args)}"
        return subprocess.Popen(self.base + ["shell", cmd], stdout=log, stderr=subprocess.STDOUT)

    def stop_server(self):
        self.shell("pkill -f bin/llama-server || true")


def parse_status(text):
    """/proc/<pid>/status → {'VmRSS': MB, 'VmHWM': MB}"""
    out = {}
    for k in ("VmRSS", "VmHWM"):
        m = re.search(rf"^{k}:\s+(\d+)\s+kB", text, re.M)
        if m:
            out[k] = round(int(m.group(1)) / 1024, 1)
    return out


def memory(dev):
    pid = dev.shell("pidof llama-server").split()
    if not pid:
        return {"alive": False}
    return {"alive": True, **parse_status(dev.shell(f"cat /proc/{pid[0]}/status"))}


def server_args(a):
    args = ["-m", DEVICE_MODEL, "-c", str(N_CTX), "-n", str(N_PREDICT), "--temp", "0", "--seed", str(SEED),
            "-np", "1", "--jinja", "--host", "127.0.0.1", "--port", str(a.port)]
    if a.threads:
        args += ["-t", str(a.threads)]
    return args + list(a.server_arg or [])


# ── pairs ────────────────────────────────────────────────────────────────────
def load_pairs():
    ds = json.loads((RELATED / "dataset.json").read_text(encoding="utf-8"))
    e5 = json.loads((RELATED / "runs" / "e5-small-ko.v1.1.json").read_text(encoding="utf-8"))
    return ds, J.plan(ds, e5)


def parity_pairs(ds):
    """case마다 1쌍. 가능하면 아직 쓰지 않은 query에서, 그중 candidate id가 가장 작은 것 (현재 기록이 겹치지 않게)."""
    out, used = [], set()
    for case in PARITY_CASES:
        cands = sorted(((c["id"], q, c) for q in ds["queries"] for c in q["candidates"] if c["case"] == case), key=lambda x: x[0])
        fresh = [x for x in cands if x[1]["id"] not in used]
        if cands:
            _, q, c = (fresh or cands)[0]
            used.add(q["id"])
            out.append((q, c))
    return out


def mac_labels():
    _, latest = J.read_log(QWEN_LOG)
    return {cid: r["label"] for (cid, at), r in latest.items() if at == 1 and r["status"] == "ok"}


def tasks_for(name, a, ds, plan):
    """→ [(cid or None, current, past)]"""
    if name == "one":
        if a.current and a.past:
            return [(None, a.current, a.past)]
        q, c, _ = plan[0]
        return [(c["id"], q["text"], c["text"])]
    if name.startswith("bench"):
        n = int(name[5:])
        return [(c["id"], q["text"], c["text"]) for q, c, _ in plan[:n]]
    if name == "parity":
        return [(c["id"], q["text"], c["text"]) for q, c in parity_pairs(ds)]
    if name == "all":  # frozen v1.1 157쌍 전체, Mac judge_v1 실행과 같은 순서 (query 순 · e5 순위 순)
        return [(c["id"], q["text"], c["text"]) for q, c, _ in plan]
    raise SystemExit(f"모르는 task: {name}")


# ── summary ──────────────────────────────────────────────────────────────────
def summarize(load, records, ds):
    lines = [f"model load (서버 시작 → /health 200): {load['load_ms'] / 1000:.1f}s · 로드 직후 메모리 {load.get('mem')}"]
    by = {}
    for r in records:
        by.setdefault(r["task"], []).append(r)
    for task, rs in by.items():
        ok = [r for r in rs if r["status"] == "ok"]
        wall = [r["wall_ms"] for r in rs]
        pm = [r["prompt_ms"] for r in rs if r.get("prompt_ms") is not None]
        gm = [r["predicted_ms"] for r in rs if r.get("predicted_ms") is not None]
        pn = [r["prompt_n"] for r in rs if r.get("prompt_n") is not None]
        gn = [r["predicted_n"] for r in rs if r.get("predicted_n") is not None]
        hwm = max((r.get("mem") or {}).get("VmHWM", 0) for r in rs)
        dead = sum(not (r.get("mem") or {}).get("alive", True) for r in rs)
        line = (f"[{task}] {len(rs)}쌍 연속 · ok {len(ok)} · 합계 {sum(wall) / 1000:.1f}s · 1쌍 median {statistics.median(wall) / 1000:.2f}s "
                f"(min {min(wall) / 1000:.2f} · max {max(wall) / 1000:.2f})")
        if pm and gm:
            line += (f" · prompt {statistics.mean(pm):.0f}ms/쌍 ({sum(pn) / max(sum(pm), 1) * 1000:.1f} tok/s, 평균 {statistics.mean(pn):.0f} tok 처리)"
                     f" · generation {statistics.mean(gm):.0f}ms/쌍 ({sum(gn) / max(sum(gm), 1) * 1000:.1f} tok/s, 평균 {statistics.mean(gn):.0f} tok)")
        line += f" · peak RSS(VmHWM) {hwm}MB" + (f" · ⚠️ 서버 종료 감지 {dead}회" if dead else "")
        lines.append(line)
        errs = [r["error"] for r in rs if r["status"] != "ok"]
        if errs:
            lines.append(f"    실패: {errs[:3]}")
        if any(r.get("thinking_present") for r in rs):
            lines.append("    ⚠️ reasoning_content가 나온 응답 있음 (thinking off가 적용되지 않음)")
    if "parity" in by:
        truth = {c["id"]: (c["label"], c["case"]) for q in ds["queries"] for c in q["candidates"]}
        mac = mac_labels()
        lines.append("\nparity (Mac/Ollama judge_v1 vs Android llama.cpp)")
        lines.append("| pair | case | 정답 | Mac | Android | 같음 |")
        lines.append("| --- | --- | --- | --- | --- | --- |")
        same = 0
        for r in by["parity"]:
            t, case = truth[r["cid"]]
            m, an = mac.get(r["cid"]), r["label"]
            same += m == an
            lines.append(f"| {r['cid']} | {case} | {t} | {m} | {an if an is not None else 'ERR'} | {'✓' if m == an else '✗'} |")
        lines.append(f"일치 {same}/{len(by['parity'])}")
    return "\n".join(lines)


# ── main ─────────────────────────────────────────────────────────────────────
def main(argv=None, dev=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", choices=["session", "dry-run"])
    ap.add_argument("--tasks", default="one,bench10,bench30,parity")
    ap.add_argument("--current")
    ap.add_argument("--past")
    ap.add_argument("--port", type=int, default=8089)
    ap.add_argument("--threads", type=int, default=0, help="0 = llama.cpp 기본값")
    ap.add_argument("--server-arg", action="append", help="llama-server에 그대로 넘길 추가 인자 (기록됨)")
    ap.add_argument("--serial", help="adb -s")
    ap.add_argument("--external", help="이미 떠 있는 서버 URL (서버 시작/종료 · 로드 측정 생략, 테스트용)")
    ap.add_argument("--load-timeout", type=int, default=600)
    ap.add_argument("--out-dir", default=HERE / "runs")
    a = ap.parse_args(argv)

    system, user_tpl, sha = J.load_prompt(RELATED / "prompts" / "judge_v1.txt")
    if sha != EXPECTED_PROMPT_SHA:
        raise SystemExit(f"judge_v1 sha {sha} ≠ {EXPECTED_PROMPT_SHA}")
    ds, plan = load_pairs()
    names = [t.strip() for t in a.tasks.split(",") if t.strip()]
    work = [(n, tasks_for(n, a, ds, plan)) for n in names]

    if a.cmd == "dry-run":
        cid, cur, past = work[0][1][0]
        print(json.dumps(request_body(system, user_tpl, cur, past), ensure_ascii=False, indent=1))
        print("\nllama-server", " ".join(server_args(a)))
        print(f"prompt sha {sha} · tasks " + ", ".join(f"{n}={len(w)}쌍" for n, w in work))
        print("parity pairs: " + ", ".join(f"{c['id']}({c['case']}, 정답 {c['label']})" for _, c in parity_pairs(ds)))
        return 0

    out_dir = Path(a.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    out, log_path = out_dir / f"session-{stamp}.jsonl", out_dir / f"session-{stamp}-server.log"
    dev = dev or Adb(a.serial)
    base = a.external or f"http://127.0.0.1:{a.port}"
    proc = None
    meta = {"type": "meta", "prompt_sha": sha, "server_args": server_args(a), "request_example": request_body(system, user_tpl, "{current}", "{past}"),
            "tasks": names, "created": stamp}
    try:
        if not a.external:
            meta["device"] = {"model": dev.shell("getprop ro.product.model"), "soc": dev.shell("getprop ro.soc.model"),
                              "android": dev.shell("getprop ro.build.version.release"),
                              "mem": dev.shell("grep -E 'MemTotal|MemAvailable' /proc/meminfo"),
                              "build_info": dev.shell(f"cat {DEVICE_DIR}/BUILD_INFO.txt"),
                              "model_file": dev.shell(f"ls -l {DEVICE_MODEL}")}
            dev.stop_server()
            dev.forward(a.port)
            t0 = time.time()
            proc = dev.start_server(server_args(a), log_path)
            while True:
                if proc.poll() is not None:
                    raise SystemExit(f"✗ llama-server가 로드 중 종료됨 (code {proc.returncode}). 로그: {log_path}")
                try:
                    if http(f"{base}/health", timeout=5)[0] == 200:
                        break
                except Exception:
                    pass
                if time.time() - t0 > a.load_timeout:
                    raise SystemExit(f"✗ {a.load_timeout}s 안에 로드되지 않음. 로그: {log_path}")
                time.sleep(0.5)
            load = {"type": "load", "load_ms": int((time.time() - t0) * 1000), "mem": memory(dev)}
        else:
            load = {"type": "load", "load_ms": 0, "mem": memory(dev), "external": True}
        with out.open("w", encoding="utf-8") as f:
            f.write(json.dumps(meta, ensure_ascii=False) + "\n" + json.dumps(load, ensure_ascii=False) + "\n")
        print(f"load {load['load_ms'] / 1000:.1f}s · {load['mem']}", flush=True)
        records = []
        for name, items in work:  # 같은 서버(모델 instance)로 연속 판정, reload 없음
            for i, (cid, cur, past) in enumerate(items, 1):
                rec = judge(base, system, user_tpl, cur, past)
                rec.update(type="judgment", task=name, idx=i, cid=cid, mem=memory(dev),
                           ts=dt.datetime.now().isoformat(timespec="seconds"))
                records.append(rec)
                with out.open("a", encoding="utf-8") as f:
                    f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                shown = f"label {rec['label']}" if rec["status"] == "ok" else f"ERROR {rec['error']}"
                print(f"[{name}] {i}/{len(items)} {cid or '-'} {rec['wall_ms']}ms (prompt {rec.get('prompt_n')}tok {rec.get('prompt_ms')}ms · "
                      f"gen {rec.get('predicted_n')}tok {rec.get('predicted_ms')}ms) {shown} · {rec['mem']}", flush=True)
                if not rec["mem"].get("alive", True):
                    tail = dev.shell("logcat -d -t 200 | grep -iE 'lowmemorykiller|lmkd|llama' | tail -20")
                    with out.open("a", encoding="utf-8") as f:
                        f.write(json.dumps({"type": "crash", "logcat": tail}, ensure_ascii=False) + "\n")
                    raise SystemExit(f"✗ llama-server 종료 감지 (crash/OOM 의심). logcat:\n{tail}")
        summary = summarize(load, records, ds)
        (out_dir / f"session-{stamp}-summary.md").write_text(summary + "\n", encoding="utf-8")
        print("\n" + summary)
        print(f"\n기록: {out}")
        return 0
    finally:
        if proc is not None:
            dev.stop_server()
            proc.terminate()


if __name__ == "__main__":
    sys.exit(main())
