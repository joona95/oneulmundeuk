#!/usr/bin/env python3
"""Android Qwen PoC 클라이언트 테스트 (fake llama-server · fake adb, 실제 기기/모델 없음).  python3 -m unittest test_poc_judge -v"""
import contextlib
import hashlib
import io
import json
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import poc_judge as P

PROTECTED = [P.RELATED / p for p in ("dataset.json", "runs/e5-small-ko.v1.1.json", "runs/llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl",
                                     "prompts/judge_v1.txt", "llm_judge.py", "strong_judge.py")]


def md5s():
    return {str(p): hashlib.md5(p.read_bytes()).hexdigest() for p in PROTECTED}


class FakeServer(BaseHTTPRequestHandler):
    script, seen = [], []

    def log_message(self, *a):
        pass

    def _send(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        self._send(200, {"status": "ok"})

    def do_POST(self):
        FakeServer.seen.append((self.path, json.loads(self.rfile.read(int(self.headers["Content-Length"])))))
        content, finish, reasoning = FakeServer.script.pop(0) if FakeServer.script else ('{"reason": "근거", "label": 2}', "stop", None)
        msg = {"role": "assistant", "content": content} | ({"reasoning_content": reasoning} if reasoning else {})
        self._send(200, {"choices": [{"message": msg, "finish_reason": finish}],
                         "usage": {"prompt_tokens": 410, "completion_tokens": 40},
                         "timings": {"prompt_n": 120, "prompt_ms": 900.0, "predicted_n": 40, "predicted_ms": 2000.0, "cache_n": 290}})


class FakeDev:
    def __init__(self, alive=True):
        self.alive, self.cmds = alive, []

    def shell(self, cmd, timeout=30):
        self.cmds.append(cmd)
        if cmd.startswith("pidof"):
            return "4242" if self.alive else ""
        if cmd.startswith("cat /proc/"):
            return "Name:\tllama-server\nVmHWM:\t 1843200 kB\nVmRSS:\t 1720320 kB\n"
        return ""


class PocTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = md5s()
        cls.srv = HTTPServer(("127.0.0.1", 0), FakeServer)
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        cls.url = f"http://127.0.0.1:{cls.srv.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        assert md5s() == cls.before, "실험 파일이 바뀜"

    def setUp(self):
        FakeServer.script, FakeServer.seen = [], []
        self.tmp = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.tmp.cleanup()

    def run_session(self, *args, dev=None):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            try:
                rc = P.main(["session", "--external", self.url, "--out-dir", self.tmp.name, *args], dev=dev or FakeDev())
            except SystemExit as e:
                rc = e.code
        return rc, buf.getvalue()

    def test_request_matches_judge_v1_conditions(self):
        system, user, sha = P.J.load_prompt(P.RELATED / "prompts" / "judge_v1.txt")
        self.assertEqual(sha, "553ab6176c0293f9")
        b = P.request_body(system, user, "현재", "과거")
        self.assertEqual(b["messages"], P.J.messages(system, user, "현재", "과거"))
        self.assertEqual((b["temperature"], b["seed"], b["max_tokens"]), (0, 7, 192))
        self.assertEqual(b["response_format"]["json_schema"]["schema"], P.J.SCHEMA)
        self.assertEqual(b["chat_template_kwargs"], {"enable_thinking": False})
        args = P.server_args(P.argparse.Namespace(port=8089, threads=0, server_arg=None))
        for flag, val in (("-c", "2048"), ("-n", "192"), ("--temp", "0"), ("--seed", "7"), ("-np", "1")):
            self.assertEqual(args[args.index(flag) + 1], val)
        self.assertIn("--jinja", args)

    def test_parse_status(self):
        self.assertEqual(P.parse_status("VmHWM:\t 2048 kB\nVmRSS:\t 1024 kB\n"), {"VmRSS": 1.0, "VmHWM": 2.0})

    def test_parity_pairs_fixed_and_cover_required(self):
        ds, _ = P.load_pairs()
        pairs = P.parity_pairs(ds)
        self.assertEqual(len(pairs), 10)
        self.assertEqual({c["label"] for _, c in pairs}, {0, 1, 2})
        self.assertEqual([c["case"] for _, c in pairs], P.PARITY_CASES)
        self.assertEqual(len({q["id"] for q, _ in pairs}), 10)
        self.assertEqual(P.parity_pairs(ds), pairs)
        mac = P.mac_labels()
        self.assertTrue(all(c["id"] in mac for _, c in pairs))

    def test_session_runs_tasks_on_one_server(self):
        rc, out = self.run_session("--tasks", "one,bench10,bench30,parity")
        self.assertEqual(rc, 0, out)
        self.assertEqual(len(FakeServer.seen), 1 + 10 + 30 + 10)
        self.assertTrue(all(p == "/v1/chat/completions" for p, _ in FakeServer.seen))
        recs = [json.loads(l) for f in Path(self.tmp.name).glob("session-*.jsonl") for l in f.read_text().splitlines()]
        j = [r for r in recs if r["type"] == "judgment"]
        self.assertEqual(len(j), 51)
        self.assertEqual((j[0]["prompt_n"], j[0]["predicted_ms"], j[0]["mem"]["VmHWM"]), (120, 2000.0, 1800.0))
        self.assertIn("[bench30] 30쌍 연속", out)
        self.assertIn("parity (Mac/Ollama judge_v1 vs Android llama.cpp)", out)
        self.assertTrue(list(Path(self.tmp.name).glob("session-*-summary.md")))

    def test_custom_pair_and_validation(self):
        FakeServer.script = [('{"reason": "x", "label": 3}', "stop", None)]
        rc, out = self.run_session("--tasks", "one", "--current", "오늘 생각", "--past", "예전 생각")
        body = FakeServer.seen[0][1]
        self.assertIn("오늘 생각", body["messages"][1]["content"])
        self.assertIn("invalid label", out)

    def test_truncated_and_thinking_flagged(self):
        FakeServer.script = [('{"reason": "길', "length", None), ('{"reason": "x", "label": 1}', "stop", "생각…")]
        rc, out = self.run_session("--tasks", "bench2")
        self.assertIn("truncated", out)
        self.assertIn("thinking off가 적용되지 않음", out)

    def test_server_death_stops_session(self):
        rc, out = self.run_session("--tasks", "bench10", dev=FakeDev(alive=False))
        self.assertIn("종료 감지", str(rc))
        self.assertEqual(len(FakeServer.seen), 1)

    def test_dry_run_needs_no_device(self):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            P.main(["dry-run"])
        self.assertIn('"enable_thinking": false', buf.getvalue())
        self.assertEqual(FakeServer.seen, [])


if __name__ == "__main__":
    unittest.main()
