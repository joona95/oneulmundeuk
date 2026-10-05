#!/usr/bin/env python3
"""M5-2d strong_judge 테스트 (stdlib unittest, 실제 API 호출 없음 · fake 서버만).  python3 -m unittest test_strong_judge -v"""
import contextlib
import hashlib
import io
import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

import llm_judge as J
import strong_judge as S

HERE = Path(__file__).resolve().parent
PROTECTED = ["dataset.json", "runs/e5-small-ko.v1.1.json", "runs/llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl",
             "prompts/judge_v1.txt", "prompts/judge_v2.txt", "llm_judge.py", "llm_report.py", "compare_judges.py",
             "eval.py", "results-m5-2a.md"]
FAKE_KEY = "sk-test-not-a-real-key-0000"


def md5s():
    return {p: hashlib.md5((HERE / p).read_bytes()).hexdigest() for p in PROTECTED if (HERE / p).exists()}


def ok_resp(content, inp=420, out=40, status="completed", why=None, refusal=None, reasoning=0):
    part = {"type": "refusal", "refusal": refusal} if refusal else {"type": "output_text", "text": content}
    return {"id": "resp_fake", "model": "gpt-5.6-sol-2026-09-01", "status": status,
            "incomplete_details": {"reason": why} if why else None,
            "output": [{"type": "message", "role": "assistant", "content": [part]}],
            "usage": {"input_tokens": inp, "output_tokens": out, "output_tokens_details": {"reasoning_tokens": reasoning},
                      "input_tokens_details": {"cached_tokens": 0}}}


VALID = '{"reason": "과거의 걱정이 현재의 결과로 이어진다.", "label": 2}'


# ── fake OpenAI Responses API ────────────────────────────────────────────────
class FakeOpenAI(BaseHTTPRequestHandler):
    script = []   # dict → 200 JSON, int → 그 HTTP 코드
    seen = []     # (path, headers, body)

    def log_message(self, *a):
        pass

    def _send(self, code, obj):
        b = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        FakeOpenAI.seen.append((self.path, dict(self.headers), body))
        r = FakeOpenAI.script.pop(0) if FakeOpenAI.script else ok_resp(VALID)
        if isinstance(r, int):
            return self._send(r, {"error": {"message": f"fake {r}"}})
        self._send(200, r)


# ── fake Ollama (llm_judge.judge_once와 비교용) ────────────────────────────────
class FakeOllama(BaseHTTPRequestHandler):
    reply = ("", "stop")

    def log_message(self, *a):
        pass

    def do_POST(self):
        self.rfile.read(int(self.headers["Content-Length"]))
        content, done = FakeOllama.reply
        b = json.dumps({"message": {"content": content}, "done_reason": done, "prompt_eval_count": 400,
                        "eval_count": 30, "load_duration": 0}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)


def serve(handler):
    srv = HTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv, f"http://127.0.0.1:{srv.server_port}"


def load_real():
    system, user, sha = J.load_prompt(HERE / "prompts" / "judge_v1.txt")
    ds = json.loads((HERE / "dataset.json").read_text())
    e5 = json.loads((HERE / "runs" / "e5-small-ko.v1.1.json").read_text())
    return system, user, sha, ds, J.plan(ds, e5)


class Base(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.srv, cls.url = serve(FakeOpenAI)
        cls.osrv, cls.ourl = serve(FakeOllama)
        cls.before = md5s()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        cls.osrv.shutdown()
        assert md5s() == cls.before, "보호 파일이 바뀜"

    def setUp(self):
        FakeOpenAI.script, FakeOpenAI.seen = [], []
        self.tmp = tempfile.TemporaryDirectory()
        self.out = Path(self.tmp.name) / "strong.jsonl"

    def tearDown(self):
        self.tmp.cleanup()

    def run_main(self, *extra, key=FAKE_KEY):
        env = {k: v for k, v in os.environ.items() if k != "OPENAI_API_KEY"}
        if key:
            env["OPENAI_API_KEY"] = key
        buf = io.StringIO()
        with mock.patch.dict(os.environ, env, clear=True), contextlib.redirect_stdout(buf):
            try:
                rc = S.main(["--base-url", self.url, "--out", str(self.out), "--backoff", "0", *extra])
            except SystemExit as e:
                rc = e.code
        return rc, buf.getvalue()

    def records(self):
        return [json.loads(l) for l in self.out.read_text().splitlines() if '"judgment"' in l]


# ── 고정 조건 ─────────────────────────────────────────────────────────────────
class FixedConditionTest(unittest.TestCase):
    def test_prompt_hash_is_judge_v1(self):
        _, _, sha, _, _ = load_real()
        self.assertEqual(sha, "553ab6176c0293f9")
        self.assertEqual(sha, S.EXPECTED_PROMPT_SHA)
        meta, _ = J.read_log(S.QWEN_LOG)
        self.assertEqual(meta["prompt_sha"], sha)  # Qwen judge_v1 run과 같은 prompt

    def test_request_body(self):
        system, user, _, _, pairs = load_real()
        q, c, _ = pairs[0]
        b = S.request_body("gpt-5.6-sol", system, user, q["text"], c["text"])
        self.assertEqual(b["model"], "gpt-5.6-sol")
        self.assertEqual(b["temperature"], 0)
        self.assertEqual(b["reasoning"], {"effort": "none"})
        self.assertEqual(b["max_output_tokens"], 192)
        self.assertIs(b["store"], False)
        self.assertNotIn("seed", b)
        self.assertEqual(b["input"], J.messages(system, user, q["text"], c["text"]))
        fmt = b["text"]["format"]
        self.assertEqual((fmt["type"], fmt["strict"]), ("json_schema", True))
        self.assertEqual(list(fmt["schema"]["properties"]), ["reason", "label"])
        self.assertEqual({k: v for k, v in fmt["schema"].items() if k != "additionalProperties"}, J.SCHEMA)
        self.assertIs(fmt["schema"]["additionalProperties"], False)

    def test_all_157_payloads_verified(self):
        system, user, _, ds, pairs = load_real()
        self.assertEqual(len(pairs), 157)
        self.assertEqual(ds["version"], "1.1")
        for q, c, _ in pairs:
            b = S.request_body("gpt-5.6-sol", system, user, q["text"], c["text"])
            self.assertEqual(S.verify_payload(b, "gpt-5.6-sol", system, user, q, c), [], c["id"])
            u = b["input"][1]["content"]
            for leak in ("label", "repeat_low_value", "lexical_trap", "implicit_link", "cosine", "e5"):
                self.assertNotIn(leak, u)

    def test_verify_catches_changes(self):
        system, user, _, _, pairs = load_real()
        q, c, _ = pairs[0]
        for k, v in [("temperature", 0.2), ("max_output_tokens", 48), ("reasoning", {"effort": "low"}), ("seed", 7)]:
            b = S.request_body("gpt-5.6-sol", system, user, q["text"], c["text"]) | {k: v}
            self.assertTrue(S.verify_payload(b, "gpt-5.6-sol", system, user, q, c), k)

    def test_same_order_as_judge_v1_run(self):
        _, _, _, _, pairs = load_real()
        rows = [json.loads(l) for l in S.QWEN_LOG.read_text().splitlines() if '"judgment"' in l]
        first = []
        for r in rows:
            if r["attempt"] == 1 and r["cid"] not in first:
                first.append(r["cid"])
        self.assertEqual([c["id"] for _, c, _ in pairs], first)

    def test_wrong_prompt_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "judge_v1.txt"
            p.write_text((HERE / "prompts" / "judge_v1.txt").read_text() + "\n추가")
            with contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit) as cm:
                    S.main(["--prompt", str(p), "--dry-run"])
            self.assertIn("553ab6176c0293f9", str(cm.exception.code))


# ── 검증 규칙이 llm_judge.judge_once와 같은가 ─────────────────────────────────────
class ParityTest(Base):
    CASES = [VALID, '{"reason": "  앞뒤 공백  ", "label": 0}', '{"reason": "x", "label": 1}',
             '{"reason": "x", "label": 3}', '{"reason": "x", "label": "2"}', '{"reason": "x", "label": true}',
             '{"reason": "x", "label": 2.0}', '{"reason": "", "label": 2}', '{"reason": "   ", "label": 1}',
             '{"label": 2}', '{"reason": "x"}', "not json", "[]", '{"reason": "x", "label": 2', ""]

    def test_same_validation_as_llm_judge(self):
        a_ol = SimpleNamespace(model="m", host=self.ourl, keep_alive="1m", seed=7, num_ctx=2048, num_predict=192, timeout=10)
        a_oa = SimpleNamespace(model="gpt-5.6-sol", base_url=self.url, timeout=10, http_retries=0, backoff=0)
        keys = ("status", "label", "reason", "error", "raw")
        for content in self.CASES:
            for truncated in (False, True):
                FakeOllama.reply = (content, "length" if truncated else "stop")
                FakeOpenAI.script = [ok_resp(content, status="incomplete" if truncated else "completed",
                                             why="max_output_tokens" if truncated else None)]
                r1 = J.judge_once(a_ol, "sys", "{current}/{past}", "c", "p")
                r2 = S.judge_once(a_oa, FAKE_KEY, "sys", "{current}/{past}", "c", "p")
                self.assertEqual({k: r1[k] for k in keys}, {k: r2[k] for k in keys}, (content, truncated))


# ── 실행 ─────────────────────────────────────────────────────────────────────
class RunTest(Base):
    def test_dry_run_no_network_no_file(self):
        rc, outp = self.run_main("--dry-run", key=None)
        self.assertEqual(rc, 0)
        self.assertEqual(FakeOpenAI.seen, [])
        self.assertFalse(self.out.exists())
        self.assertIn("payload 검증 157/157 통과", outp)
        self.assertIn("남음 157", outp)
        self.assertIn("input $4.0/1M · output $20.0/1M", outp)

    def test_print_request_no_network(self):
        rc, outp = self.run_main("--print-request", key=None)
        self.assertEqual(rc, 0)
        self.assertEqual(FakeOpenAI.seen, [])
        self.assertIn('"effort": "none"', outp)
        self.assertNotIn(FAKE_KEY, outp)

    def test_live_requires_env_key(self):
        rc, _ = self.run_main("--max-calls", "1", key=None)
        self.assertIn("OPENAI_API_KEY", str(rc))
        self.assertEqual(FakeOpenAI.seen, [])
        self.assertFalse(self.out.exists())

    def test_full_run_format_and_reports(self):
        rc, outp = self.run_main()
        self.assertEqual(rc, 0)
        self.assertEqual(len(FakeOpenAI.seen), 157)
        path, headers, body = FakeOpenAI.seen[0]
        self.assertEqual(path, "/responses")
        self.assertEqual(headers["Authorization"], f"Bearer {FAKE_KEY}")
        self.assertEqual((body["temperature"], body["reasoning"], body["max_output_tokens"]), (0, {"effort": "none"}, 192))
        text = self.out.read_text()
        self.assertNotIn(FAKE_KEY, text)
        self.assertNotIn(FAKE_KEY, outp)
        meta = json.loads(text.splitlines()[0])
        self.assertEqual((meta["type"], meta["provider"], meta["model"], meta["prompt_sha"], meta["dataset_version"]),
                         ("meta", "openai", "gpt-5.6-sol", "553ab6176c0293f9", "1.1"))
        recs = self.records()
        self.assertEqual(len(recs), 157)
        self.assertTrue(all(r["attempt"] == 1 and r["status"] == "ok" and r["label"] == 2 for r in recs))
        self.assertEqual(recs[0]["response_model"], "gpt-5.6-sol-2026-09-01")
        self.assertAlmostEqual(recs[0]["cost_usd"], 420 * 4 / 1e6 + 40 * 20 / 1e6)
        # 기존 리포트 도구가 그대로 읽는다
        for cmd in (["llm_report.py", "--log", str(self.out)],
                    ["compare_judges.py", "--log", f"qwen2b={S.QWEN_LOG}", "--log", f"sol={self.out}",
                     "--out", str(Path(self.tmp.name) / "cmp.md")]):
            r = subprocess.run([sys.executable, *cmd], cwd=HERE, capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stderr[-800:])
        self.assertIn("`sol`", (Path(self.tmp.name) / "cmp.md").read_text())

    def test_errors_recorded_and_resume(self):
        FakeOpenAI.script = [ok_resp('{"reason": "x", "label": 5}'),
                             ok_resp("", status="incomplete", why="max_output_tokens"),
                             ok_resp("", refusal="no"),
                             ok_resp("", status="incomplete", why="content_filter"),
                             ok_resp('{"reason": "x", "label": 1'),
                             429, ok_resp(VALID),          # 전송 재시도 후 성공
                             400]
        rc, _ = self.run_main("--max-calls", "7", "--max-consecutive-errors", "10")
        self.assertEqual(rc, 0)
        recs = self.records()
        self.assertEqual([r["status"] for r in recs], ["error"] * 5 + ["ok", "error"])
        self.assertTrue(recs[0]["error"].startswith("invalid label"))
        self.assertEqual(recs[1]["error"], S.TRUNCATED)
        self.assertTrue(recs[2]["error"].startswith("refusal"))
        self.assertEqual(recs[3]["error"], "incomplete: content_filter")
        self.assertTrue(recs[4]["error"].startswith("malformed JSON"))
        self.assertEqual(recs[5]["http_retries"], 1)
        self.assertTrue(recs[6]["error"].startswith("http 400"))
        self.assertTrue(all(r["label"] is None for r in recs if r["status"] == "error"))
        # 이어서: 실패 6개 + 미완료 150개 = 156회, 그다음엔 0회
        n = len(FakeOpenAI.seen)
        rc, outp = self.run_main()
        self.assertEqual(len(FakeOpenAI.seen) - n, 156)
        n = len(FakeOpenAI.seen)
        rc, outp = self.run_main()
        self.assertEqual(len(FakeOpenAI.seen), n)
        self.assertIn("남은 호출 없음", outp)

    def test_first_call_auth_error_aborts(self):
        FakeOpenAI.script = [401]
        rc, _ = self.run_main()
        self.assertIn("401", str(rc))
        self.assertEqual(len(FakeOpenAI.seen), 1)

    def test_reasoning_tokens_abort(self):
        FakeOpenAI.script = [ok_resp(VALID, reasoning=12)]
        rc, _ = self.run_main()
        self.assertIn("reasoning_tokens", str(rc))
        self.assertEqual(len(FakeOpenAI.seen), 1)

    def test_consecutive_errors_abort(self):
        FakeOpenAI.script = [ok_resp(VALID)] + [ok_resp("oops")] * 10
        rc, _ = self.run_main("--max-consecutive-errors", "3")
        self.assertIn("연속 3회", str(rc))
        self.assertEqual(len(FakeOpenAI.seen), 4)

    def test_cost_cap(self):
        rc, _ = self.run_main("--max-cost-usd", "0.004")  # 호출당 $0.00248
        self.assertIn("비용 한도", str(rc))
        self.assertEqual(len(FakeOpenAI.seen), 2)

    def test_meta_mismatch_refused(self):
        self.out.write_text(json.dumps({"type": "meta", "provider": "openai", "model": "gpt-5.6-sol", "prompt_sha": "x"}) + "\n")
        rc, _ = self.run_main("--max-calls", "1")
        self.assertIn("다른 설정", str(rc))
        self.assertEqual(FakeOpenAI.seen, [])


if __name__ == "__main__":
    unittest.main()
