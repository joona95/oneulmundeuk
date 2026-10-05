#!/usr/bin/env python3
"""M5-2c listwise 테스트 (stdlib unittest, 실제 Ollama 호출 없음).  python3 -m unittest test_listwise -v"""
import contextlib
import hashlib
import io
import json
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import listwise_select as L

HERE = Path(__file__).resolve().parent
PROTECTED = ["dataset.json", "runs/e5-small-ko.v1.1.json", "runs/llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl",
             "prompts/judge_v1.txt", "prompts/judge_v2.txt", "llm_judge.py", "selector_experiment.py",
             "results-m5-2a.md", "results-m5-2b-selector.md"]


def md5s():
    return {p: hashlib.md5((HERE / p).read_bytes()).hexdigest() for p in PROTECTED if (HERE / p).exists()}


# ── fake Ollama ──────────────────────────────────────────────────────────────
class Fake(BaseHTTPRequestHandler):
    script = []          # 응답 content를 순서대로 (None → HTTP 500)
    seen = []            # 받은 요청 body
    pec = 500            # prompt_eval_count

    def log_message(self, *a):
        pass

    def _send(self, code, obj):
        b = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        self._send(200, {"models": [{"name": "qwen3.5:2b-q4_K_M", "size": 1, "size_vram": 1, "context_length": 2048}]})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        Fake.seen.append(body)
        content = Fake.script.pop(0) if Fake.script else '{"selected": []}'
        if content is None:
            return self._send(500, {"error": "boom"})
        self._send(200, {"message": {"content": content}, "done_reason": "stop", "prompt_eval_count": Fake.pec,
                         "eval_count": 9, "load_duration": 5_000_000})


class Base(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.srv = HTTPServer(("127.0.0.1", 0), Fake)
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        cls.host = f"http://127.0.0.1:{cls.srv.server_port}"
        cls.before = md5s()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()

    def setUp(self):
        Fake.script, Fake.seen, Fake.pec = [], [], 500
        self.tmp = tempfile.TemporaryDirectory()
        self.out = Path(self.tmp.name) / "lw.jsonl"

    def tearDown(self):
        self.tmp.cleanup()

    def run_main(self, *extra):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            try:
                rc = L.main(["--host", self.host, "--out", str(self.out), *extra])
            except SystemExit as e:
                rc = e.code
        return rc, buf.getvalue()

    def records(self):
        return [json.loads(l) for l in self.out.read_text().splitlines() if '"listwise"' in l]


class ShuffleTest(unittest.TestCase):
    IDS = [f"q01-{c}" for c in "abcdefghij"]

    def test_deterministic(self):
        self.assertEqual(L.shuffled("q01", self.IDS), L.shuffled("q01", list(self.IDS)))

    def test_independent_of_input_order(self):
        self.assertEqual(L.shuffled("q01", self.IDS), L.shuffled("q01", list(reversed(self.IDS))))

    def test_different_queries_differ(self):
        ids2 = [i.replace("q01", "q02") for i in self.IDS]
        a = [i.split("-")[1] for i in L.shuffled("q01", self.IDS)]
        b = [i.split("-")[1] for i in L.shuffled("q02", ids2)]
        self.assertNotEqual(a, b)

    def test_not_e5_order_on_real_data(self):
        system, user, _ = L.load_prompt(HERE / "prompts" / "listwise_v1.txt")
        ds = json.loads((HERE / "dataset.json").read_text())
        e5 = json.loads((HERE / "runs" / "e5-small-ko.v1.1.json").read_text())
        items = L.plan(ds, e5, system, user)
        same = sum(list(it["letters"].values()) == it["e5_top"] for it in items)
        firsts = sum(it["letters"]["A"] == it["e5_top"][0] for it in items)
        self.assertEqual(same, 0)
        self.assertLess(firsts, 6)  # A가 e5 1위인 query가 우연 수준 이하

    def test_letter_mapping_roundtrip(self):
        mp = L.letter_map(L.shuffled("q05", self.IDS))
        self.assertEqual(list(mp), list("ABCDEFGHIJ"))
        self.assertEqual(sorted(mp.values()), sorted(self.IDS))


class ValidateTest(unittest.TestCase):
    LET = {"A": "x-a", "B": "x-b", "C": "x-c", "D": "x-d"}

    def test_empty_is_valid(self):
        self.assertEqual(L.validate('{"selected": []}', self.LET), ([], None))

    def test_order_preserved(self):
        self.assertEqual(L.validate('{"selected": ["C", "A"]}', self.LET)[0], ["C", "A"])

    def test_max3(self):
        self.assertIn("too many", L.validate('{"selected": ["A","B","C","D"]}', self.LET)[1])

    def test_duplicate(self):
        self.assertIn("duplicate", L.validate('{"selected": ["A","A"]}', self.LET)[1])

    def test_unknown(self):
        self.assertIn("unknown", L.validate('{"selected": ["Z"]}', self.LET)[1])

    def test_malformed(self):
        for bad in ["not json", '{"picked": []}', '{"selected": "A"}', '{"selected": [1]}', "[]"]:
            self.assertIsNone(L.validate(bad, self.LET)[0], bad)


class RunTest(Base):
    def test_dry_run_makes_no_call_and_writes_nothing(self):
        rc, outp = self.run_main("--dry-run")
        self.assertEqual(rc, 0)
        self.assertEqual(Fake.seen, [])
        self.assertFalse(self.out.exists())
        self.assertIn("호출 예정 17회", outp)

    def test_request_contents(self):
        Fake.script = ['{"selected": ["B"]}'] + ['{"selected": []}'] * 16
        rc, _ = self.run_main()
        self.assertEqual(rc, 0)
        body = Fake.seen[0]
        self.assertEqual(body["options"], {"temperature": 0, "seed": 7, "num_ctx": 2048, "num_predict": 192})
        self.assertIs(body["think"], False)
        self.assertEqual(body["keep_alive"], "10m")
        self.assertEqual(body["format"]["properties"]["selected"]["maxItems"], 3)
        user = body["messages"][1]["content"]
        for leak in ("2025-", "2026-", "label", "repeat_low_value", "커리어", "calm", "cosine"):
            self.assertNotIn(leak, user)
        recs = self.records()
        self.assertEqual(len(recs), 17)
        r = recs[0]
        self.assertEqual(r["selected_ids"], [r["letters"]["B"]])
        self.assertEqual(sorted(r["prompt_order"]), sorted(r["e5_top"]))

    def test_invalid_outputs_recorded_as_failures_and_resume(self):
        Fake.script = ['{"selected": ["A","B","C","D"]}', '{"selected": ["A","A"]}', "oops"]
        rc, outp = self.run_main()
        self.assertIn("연속 3회 실패", str(rc) + outp)
        recs = self.records()
        self.assertEqual([r["status"] for r in recs], ["error"] * 3)
        self.assertTrue(all(r["selected_ids"] is None for r in recs))
        # resume: 실패한 3개 포함 17개를 다시 시도, 성공 후 재실행 시 호출 없음
        Fake.script = ['{"selected": []}'] * 17
        rc, _ = self.run_main()
        self.assertEqual(rc, 0)
        n_calls = len(Fake.seen)
        rc, outp = self.run_main()
        self.assertEqual(len(Fake.seen), n_calls)
        self.assertIn("남은 호출 없음", outp)

    def test_http_error_recorded(self):
        Fake.script = [None] + ['{"selected": []}'] * 16
        self.run_main()
        recs = self.records()
        self.assertTrue(recs[0]["error"].startswith("http 500"))
        self.assertEqual(sum(r["status"] == "ok" for r in recs), 16)

    def test_truncation_detected(self):
        Fake.pec = 1900  # >= num_ctx - num_predict
        rc, _ = self.run_main()
        self.assertIn("잘림", str(rc))
        self.assertEqual(len(Fake.seen), 1)
        Fake.pec = 50    # 추정 하한보다 작음 → 잘림 의심
        self.out.unlink()
        rc, _ = self.run_main()
        self.assertIn("잘림", str(rc))

    def test_token_safety_real_data(self):
        system, user, _ = L.load_prompt(HERE / "prompts" / "listwise_v1.txt")
        ds = json.loads((HERE / "dataset.json").read_text())
        e5 = json.loads((HERE / "runs" / "e5-small-ko.v1.1.json").read_text())
        items = L.plan(ds, e5, system, user)
        self.assertEqual(len(items), 17)
        self.assertTrue(all(len(it["letters"]) <= 10 for it in items))
        self.assertLess(max(it["tok_upper"] for it in items) + 192, 2048)
        self.assertLessEqual(max(it["tok_upper"] for it in items), L.PREFLIGHT_LIMIT)

    def test_protected_files_unchanged(self):
        Fake.script = ['{"selected": []}'] * 17
        self.run_main()
        self.assertEqual(md5s(), self.before)


if __name__ == "__main__":
    unittest.main()
