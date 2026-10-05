#!/usr/bin/env python3
"""M5-4a 클라이언트 · 리포트 테스트 (fake vLLM server · 가짜 run, Modal/모델 호출 없음).  python3 -m unittest test_judge_client -v"""
import contextlib
import hashlib
import io
import json
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import judge_client as C
import modal_eval as E

PROTECTED = [C.RELATED / p for p in ("dataset.json", "runs/e5-small-ko.v1.1.json", "runs/llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl",
                                     "prompts/judge_v1.txt", "llm_judge.py", "strong_judge.py")] + [E.ANDROID_LOG, E.ANDROID_SESSION]


def md5s():
    return {str(p): hashlib.md5(p.read_bytes()).hexdigest() for p in PROTECTED}


class FakeVllm(BaseHTTPRequestHandler):
    script, seen, delay = [], [], 0.0
    lock = threading.Lock()

    def log_message(self, *a):
        pass

    def do_GET(self):
        body = b"ok" if self.path == "/health" else (
            b'# HELP x\nvllm:e2e_request_latency_seconds_sum{model_name="judge"} 3.5\nvllm:e2e_request_latency_seconds_count{model_name="judge"} 2\n')
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        with FakeVllm.lock:
            FakeVllm.seen.append(body)
            item = FakeVllm.script.pop(0) if FakeVllm.script else None
        if item == 500:
            self.send_response(500)
            self.send_header("Content-Length", "4")
            self.end_headers()
            self.wfile.write(b"boom")
            return
        content, finish, reasoning = item or ('{"reason": "근거", "label": 2}', "stop", None)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.end_headers()
        time.sleep(FakeVllm.delay)
        events = []
        if reasoning:
            events.append({"choices": [{"delta": {"reasoning": reasoning}}]})
        half = len(content) // 2
        events += [{"choices": [{"delta": {"content": content[:half]}}]}, {"choices": [{"delta": {"content": content[half:]}, "finish_reason": finish}]},
                   {"choices": [], "usage": {"prompt_tokens": 410, "completion_tokens": 45, "prompt_tokens_details": {"cached_tokens": 300}}}]
        for ev in events:
            self.wfile.write(f"data: {json.dumps(ev, ensure_ascii=False)}\n\n".encode())
            self.wfile.flush()
        self.wfile.write(b"data: [DONE]\n\n")


class Base(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = md5s()
        cls.srv = ThreadingHTTPServer(("127.0.0.1", 0), FakeVllm)
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        cls.url = f"http://127.0.0.1:{cls.srv.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        assert md5s() == cls.before, "기존 실험 파일이 바뀜"

    def setUp(self):
        FakeVllm.script, FakeVllm.seen, FakeVllm.delay = [], [], 0.0
        self.system, self.user, _ = C.load_prompt()


class ClientTest(Base):
    def test_request_matches_judge_v1_conditions(self):
        b = C.request_body(self.system, self.user, "현재", "과거")
        self.assertEqual(b["messages"], C.J.messages(self.system, self.user, "현재", "과거"))
        self.assertEqual((b["model"], b["temperature"], b["seed"], b["max_tokens"]), ("judge", 0, 7, 192))
        self.assertEqual(b["response_format"]["json_schema"]["schema"], C.J.SCHEMA)
        self.assertEqual(b["chat_template_kwargs"], {"enable_thinking": False})
        self.assertTrue(b["stream"] and b["stream_options"]["include_usage"])

    def test_stream_parse_and_timing(self):
        FakeVllm.delay = 0.05
        r = C.judge(self.url, self.system, self.user, "현재", "과거")
        self.assertEqual((r["status"], r["label"], r["reason"]), ("ok", 2, "근거"))
        self.assertEqual((r["prompt_tokens"], r["completion_tokens"], r["cached_tokens"]), (410, 45, 300))
        self.assertGreaterEqual(r["ttft_ms"], 40)
        self.assertLessEqual(abs(r["ttft_ms"] + r["gen_ms"] - r["wall_ms"]), 1)
        self.assertFalse(r["thinking_present"])

    def test_validation_truncation_thinking_http(self):
        FakeVllm.script = [('{"reason": "x", "label": 3}', "stop", None), ('{"reason": "길', "length", None),
                           ('{"reason": "x", "label": 0}', "stop", "생각…"), 500]
        rs = [C.judge(self.url, self.system, self.user, "a", "b") for _ in range(4)]
        self.assertTrue(rs[0]["error"].startswith("invalid label"))
        self.assertTrue(rs[1]["error"].startswith("truncated"))
        self.assertEqual((rs[2]["label"], rs[2]["thinking_present"]), (0, True))
        self.assertTrue(rs[3]["error"].startswith("http 500"))

    def test_wait_ready_and_metrics(self):
        self.assertIsNotNone(C.wait_ready(self.url, 5))
        self.assertIsNone(C.wait_ready("http://127.0.0.1:9", 0.3, poll=0.1))
        m = C.metrics_snapshot(self.url)
        self.assertEqual(m["vllm:e2e_request_latency_seconds_count"], 2.0)
        self.assertEqual(C.diff({"a": 3.0}, {"a": 1.0}), {"a": 2.0})

    def test_burst_is_concurrent(self):
        FakeVllm.delay = 0.3
        pairs = [(f"x{i}", "현재", f"과거{i}") for i in range(10)]
        wall, recs = C.burst(self.url, self.system, self.user, pairs, workers=10)
        self.assertEqual(len(recs), 10)
        self.assertTrue(all(r["label"] == 2 for r in recs))
        self.assertLess(wall, 2.0)  # 순차면 3초 이상

    def test_plan_order_is_mac_order(self):
        _, plan = C.load_plan()
        self.assertEqual(len(plan), 157)
        mac = []
        for l in E.MAC_LOG.read_text().splitlines():
            r = json.loads(l)
            if r.get("type") == "judgment" and r["attempt"] == 1 and r["cid"] not in mac:
                mac.append(r["cid"])
        self.assertEqual([c["id"] for _, c, _ in plan], mac)


def fake_run(path, plan, labels, drop=0):
    lines = [{"type": "meta", "model": "Qwen/Qwen3.5-4B", "model_revision": "main", "precision": "bf16", "engine": "vllm==0.21.0", "gpu": "L4",
              "gpu_price_per_s": 0.000222, "vllm_args": ["--max-model-len", "2048"], "prompt_sha": C.EXPECTED_PROMPT_SHA,
              "request_example": {"temperature": 0}, "created": "x"},
             {"type": "cold_start", "ready_s": 95.0}]
    for i, (q, c, rank) in enumerate(plan[:len(plan) - drop], 1):
        lab = labels(c["id"])
        lines.append({"type": "judgment", "task": "seq", "idx": i, "qid": q["id"], "cid": c["id"], "e5_rank": rank,
                      "status": "ok" if lab is not None else "error", "label": lab, "reason": "r" if lab is not None else None,
                      "error": None if lab is not None else "invalid label: 9", "raw": "{}", "wall_ms": 1500 + i, "ttft_ms": 300,
                      "gen_ms": 1200 + i, "prompt_tokens": 410, "completion_tokens": 50})
    lines += [{"type": "seq_done", "wall_s": 250.0, "server_metrics": {"vllm:e2e_request_latency_seconds_sum": 200.0, "vllm:e2e_request_latency_seconds_count": 157.0}},
              {"type": "burst", "idx": 1, "size": 30, "wall_s": 4.2, "ok": 30, "pair_wall_ms": [], "labels": {}},
              {"type": "burst", "idx": 2, "size": 30, "wall_s": 3.9, "ok": 30, "pair_wall_ms": [], "labels": {}},
              {"type": "end", "client_wall_s": 400.0, "scaledown_s": 120, "resolved_revision": "abc123"}]
    Path(path).write_text("\n".join(json.dumps(l, ensure_ascii=False) for l in lines) + "\n")


class EvalTest(Base):
    def setUp(self):
        super().setUp()
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)
        _, self.plan = C.load_plan()
        self.mac = E.labels_from_log(E.MAC_LOG)

    def tearDown(self):
        self.tmp.cleanup()

    def run_eval(self, run):
        args = ["--run", str(run), "--out", str(self.d / "r.md"), "--csv", str(self.d / "p.csv"), "--converted", str(self.d / "c.jsonl")]
        with contextlib.redirect_stdout(io.StringIO()):
            try:
                return E.main(args)
            except SystemExit as e:
                return e.code

    def test_same_labels_as_mac_reproduce_mac_metrics(self):
        fake_run(self.d / "m.jsonl", self.plan, lambda cid: self.mac[cid])
        self.assertEqual(self.run_eval(self.d / "m.jsonl"), 0)
        rep = (self.d / "r.md").read_text()
        self.assertIn("| 보여준 총 개수 | 85 | 66 | 64 | 66 |", rep)       # e5 · Mac 2B · Android 2B · Modal(=Mac)
        self.assertIn("| label 0 보여줌 | 37 | 17 | 16 | 17 |", rep)
        self.assertIn("| label 2 보여줌 (/47) | 33 | 27 | 30 | 27 |", rep)
        self.assertIn("157/157 | 1.00", rep)                              # Modal vs Mac 일치
        self.assertIn("107/157 | 0.44", rep)                              # Mac vs Android (기존 결과)
        for s in ("Good@5", "nDCG@5", "Worth%", "Bad%", "Top1=0", "Quiet miss", "F7 0개 반환", "콜드 스타트 95.0", "4.2 s · 3.9 s",
                  "저장 1,000회", "revision `abc123`", *E.CASES, *E.NOLAB2):
            self.assertIn(s, rep)
        self.assertEqual(len((self.d / "p.csv").read_text().splitlines()), 158)

    def test_failures_not_shown(self):
        fake_run(self.d / "m.jsonl", self.plan, lambda cid: None)
        self.assertEqual(self.run_eval(self.d / "m.jsonl"), 0)
        self.assertIn("| 보여준 총 개수 | 85 | 66 | 64 | 0 |", (self.d / "r.md").read_text())

    def test_incomplete_run_refused(self):
        fake_run(self.d / "m.jsonl", self.plan, lambda cid: 2, drop=3)
        self.assertIn("157쌍", str(self.run_eval(self.d / "m.jsonl")))

    def test_no_overwrite(self):
        fake_run(self.d / "m.jsonl", self.plan, lambda cid: 0)
        (self.d / "c.jsonl").write_text("다른 내용")
        self.assertIn("덮어쓰지", str(self.run_eval(self.d / "m.jsonl")))


if __name__ == "__main__":
    unittest.main()
