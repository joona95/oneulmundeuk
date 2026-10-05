#!/usr/bin/env python3
"""M5-2e reject filter 테스트 (stdlib unittest, fake Ollama · 실제 모델 호출 없음).  python3 -m unittest test_reject_filter -v"""
import contextlib
import hashlib
import io
import json
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import llm_judge as J
import reject_filter as R
import reject_filter_report as RR
import selector_experiment as S

HERE = Path(__file__).resolve().parent
PROTECTED = ["dataset.json", "runs/e5-small-ko.v1.1.json", "runs/llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl", "prompts/judge_v1.txt",
             "prompts/reject_filter_v1.txt", "llm_judge.py", "llm_report.py", "selector_experiment.py", "eval.py",
             "results-m5-2a.md", "results-m5-2b-selector.md"]


def md5s():
    return {p: hashlib.md5((HERE / p).read_bytes()).hexdigest() for p in PROTECTED}


def real():
    ds = json.loads((HERE / "dataset.json").read_text())
    e5 = json.loads((HERE / "runs" / "e5-small-ko.v1.1.json").read_text())
    return ds, e5


class Fake(BaseHTTPRequestHandler):
    script, seen, pec = [], [], 300

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
        self._send(200, {"models": [{"name": "qwen3.5:2b-q4_K_M", "size": 1, "size_vram": 1, "context_length": 2048}]})

    def do_POST(self):
        Fake.seen.append(json.loads(self.rfile.read(int(self.headers["Content-Length"]))))
        r = Fake.script.pop(0) if Fake.script else '{"keep": true}'
        if r is None:
            return self._send(500, {"error": "boom"})
        content, done = r if isinstance(r, tuple) else (r, "stop")
        self._send(200, {"message": {"content": content}, "done_reason": done, "prompt_eval_count": Fake.pec,
                         "eval_count": 6, "load_duration": 1_000_000})


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
        assert md5s() == cls.before, "보호 파일이 바뀜"

    def setUp(self):
        Fake.script, Fake.seen, Fake.pec = [], [], 300
        self.tmp = tempfile.TemporaryDirectory()
        self.out = Path(self.tmp.name) / "rf.jsonl"

    def tearDown(self):
        self.tmp.cleanup()

    def run_main(self, *extra):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            try:
                rc = R.main(["--host", self.host, "--out", str(self.out), *extra])
            except SystemExit as e:
                rc = e.code
        return rc, buf.getvalue()

    def records(self):
        return [json.loads(l) for l in self.out.read_text().splitlines() if '"filter"' in l]


class PromptPlanTest(unittest.TestCase):
    def test_prompt_fixed(self):
        system, user, sha = J.load_prompt(HERE / "prompts" / "reject_filter_v1.txt")
        self.assertEqual(sha, R.EXPECTED_PROMPT_SHA)
        self.assertEqual(user, "현재 기록:\n{current}\n\n과거 기록:\n{past}")
        self.assertTrue(system.startswith("너는 과거 기록 추천 후보를 정리하는 필터다."))
        self.assertTrue(system.endswith('{"keep": true}\n{"keep": false}'))
        for s in ("애매하면 유지한다.", "제외는 명백한 경우에만 한다.", "[제외: keep=false]", "[유지: keep=true]"):
            self.assertIn(s, system)
        self.assertNotIn("reason", system)

    def test_plan_is_e5_top10(self):
        ds, e5 = real()
        pairs = R.plan(ds, e5)
        self.assertEqual(len(pairs), sum(min(10, len(q["candidates"])) for q in ds["queries"]))
        self.assertEqual(len(pairs), 154)
        for q in ds["queries"]:
            got = [c["id"] for qq, c, _ in pairs if qq["id"] == q["id"]]
            self.assertEqual(got, [x["id"] for x in e5["rankings"][q["id"]]][:10])

    def test_token_safety(self):
        ds, e5 = real()
        system, user, _ = J.load_prompt(HERE / "prompts" / "reject_filter_v1.txt")
        self.assertLess(max(R.tok_upper(system, user, q, c) for q, c, _ in R.plan(ds, e5)) + 192, 2048)


class ValidateTest(unittest.TestCase):
    def test_ok(self):
        self.assertEqual(R.validate('{"keep": true}', False)["keep"], True)
        self.assertEqual(R.validate('{"keep": false}', False)["keep"], False)
        r = R.validate('{"keep": false, "reason": "x"}', False)
        self.assertEqual((r["status"], r["keep"], r["extra_keys"]), ("ok", False, ["reason"]))

    def test_errors(self):
        for bad in ('{"keep": "true"}', '{"keep": 1}', '{"keep": null}', "{}", "[]", "true", "oops", ""):
            r = R.validate(bad, False)
            self.assertEqual((r["status"], r["keep"]), ("error", None), bad)
        self.assertTrue(R.validate('{"keep": true}', True)["error"].startswith("truncated"))


class RunTest(Base):
    def test_dry_run(self):
        rc, outp = self.run_main("--dry-run")
        self.assertEqual(rc, 0)
        self.assertEqual(Fake.seen, [])
        self.assertFalse(self.out.exists())
        self.assertIn("호출 대상 154쌍 × 1회", outp)

    def test_request_and_records(self):
        Fake.script = ['{"keep": false}'] + ['{"keep": true}'] * 153
        rc, _ = self.run_main()
        self.assertEqual(rc, 0)
        self.assertEqual(len(Fake.seen), 154)
        b = Fake.seen[0]
        self.assertEqual(b["options"], {"temperature": 0, "seed": 7, "num_ctx": 2048, "num_predict": 192})
        self.assertIs(b["think"], False)
        self.assertEqual(b["keep_alive"], "10m")
        self.assertEqual(b["format"], R.SCHEMA)
        self.assertNotIn("reason", json.dumps(b["format"]))
        ds, e5 = real()
        q, c, _ = R.plan(ds, e5)[0]
        self.assertEqual(b["messages"][1]["content"], f"현재 기록:\n{q['text']}\n\n과거 기록:\n{c['text']}")
        for body in Fake.seen:
            u = body["messages"][1]["content"]
            for leak in ("2025-", "2026-", "label", "case", "repeat_low_value", "e5", "cosine", "q01-"):
                self.assertNotIn(leak, u)
        meta = json.loads(self.out.read_text().splitlines()[0])
        self.assertEqual((meta["prompt_sha"], meta["top_n"], meta["schema"]), (R.EXPECTED_PROMPT_SHA, 10, R.SCHEMA))
        recs = self.records()
        self.assertEqual(len(recs), 154)
        self.assertEqual((recs[0]["keep"], recs[1]["keep"]), (False, True))
        self.assertTrue(all(r["attempt"] == 1 for r in recs))
        rc, outp = self.run_main()  # 1회만: 다시 실행해도 호출 없음
        self.assertEqual(len(Fake.seen), 154)
        self.assertIn("남은 호출 없음", outp)

    def test_failures_not_retried_by_default(self):
        Fake.script = [None, '{"keep": "yes"}', ('{"keep": tr', "length")] + ['{"keep": true}'] * 151
        self.run_main()
        recs = self.records()
        self.assertEqual([r["status"] for r in recs[:3]], ["error"] * 3)
        self.assertTrue(recs[0]["error"].startswith("http 500"))
        self.assertTrue(recs[1]["error"].startswith("invalid keep"))
        self.assertTrue(recs[2]["error"].startswith("truncated"))
        n = len(Fake.seen)
        rc, outp = self.run_main()
        self.assertEqual(len(Fake.seen), n)
        self.assertIn("남은 호출 없음", outp)
        rc, _ = self.run_main("--retry-failed")
        self.assertEqual(len(Fake.seen), n + 3)

    def test_consecutive_errors_abort(self):
        Fake.script = ["oops"] * 10
        rc, _ = self.run_main()
        self.assertIn("연속 5회", str(rc))
        self.assertEqual(len(Fake.seen), 5)

    def test_prompt_truncation_abort(self):
        Fake.pec = 1900
        rc, _ = self.run_main()
        self.assertIn("잘림", str(rc))
        self.assertEqual(len(Fake.seen), 1)

    def test_meta_mismatch_refused(self):
        self.out.write_text(json.dumps({"type": "meta", "model": "other"}) + "\n")
        rc, _ = self.run_main()
        self.assertIn("다른 설정", str(rc))
        self.assertEqual(Fake.seen, [])

    def test_wrong_prompt_refused(self):
        p = Path(self.tmp.name) / "p.txt"
        p.write_text((HERE / "prompts" / "reject_filter_v1.txt").read_text() + "\n추가")
        rc, _ = self.run_main("--prompt", str(p), "--dry-run")
        self.assertIn(R.EXPECTED_PROMPT_SHA, str(rc))


class SelectTest(unittest.TestCase):
    ORDER = [f"x-{c}" for c in "abcdefghijk"]

    def test_order_cap_refill(self):
        keep = {c: True for c in self.ORDER}
        self.assertEqual(RR.filter_select(self.ORDER, keep), self.ORDER[:5])
        keep.update({"x-a": False, "x-c": False})
        self.assertEqual(RR.filter_select(self.ORDER, keep), ["x-b", "x-d", "x-e", "x-f", "x-g"])

    def test_error_is_keep_and_top10_only(self):
        keep = {c: False for c in self.ORDER[:10]} | {"x-c": None}
        self.assertEqual(RR.filter_select(self.ORDER, keep), ["x-c"])  # 11위(x-k)는 후보가 아님
        self.assertEqual(RR.filter_select(self.ORDER, {c: False for c in self.ORDER[:10]}), [])


class ReportTest(Base):
    def make_log(self, fn):
        ds, e5 = real()
        lines = [json.dumps({"type": "meta", "model": "qwen3.5:2b-q4_K_M", "prompt_file": "reject_filter_v1.txt",
                             "prompt_sha": R.EXPECTED_PROMPT_SHA, "options": {"num_ctx": 2048}, "top_n": 10})]
        lab = {c["id"]: c["label"] for q in ds["queries"] for c in q["candidates"]}
        for q, c, r in R.plan(ds, e5):
            k = fn(c["id"], lab[c["id"]], r)
            lines.append(json.dumps({"type": "filter", "qid": q["id"], "cid": c["id"], "attempt": 1, "e5_rank": r,
                                     "status": "ok" if k is not None else "error", "keep": k, "error": None if k is not None else "x",
                                     "prompt_eval_count": 300, "load_duration_ms": 0}))
        self.out.write_text("\n".join(lines) + "\n")

    def report(self):
        rep = Path(self.tmp.name) / "rep.md"
        with contextlib.redirect_stdout(io.StringIO()):
            RR.main(["--log", str(self.out), "--out", str(rep)])
        return rep.read_text()

    def test_keep_all_equals_e5_top5(self):
        self.make_log(lambda cid, lab, r: True)
        rep = self.report()
        row = next(l for l in rep.splitlines() if l.startswith("| Good@5"))
        cells = [x.strip() for x in row.split("|")[2:5]]
        self.assertEqual(cells[0], cells[2])
        self.assertIn("| 보여준 총 개수 | 85 | 66 | 85 |", rep)
        for s in ("nDCG@5", "Worth%", "Bad%", "Top1=0", "Quiet miss", "q02", "q15", "q16", "q17", *RR.CASES, "reject된 label 2"):
            self.assertIn(s, rep)

    def test_label_table_counts(self):
        # 정답 0만 reject하는 가짜 filter → label 2 reject 0, label 0 reject 전부
        self.make_log(lambda cid, lab, r: lab != 0)
        rep = self.report()
        sec = rep.split("e5 Top10 전체")[1]
        line2 = next(l for l in sec.splitlines() if l.startswith("| **2**"))
        line0 = next(l for l in sec.splitlines() if l.startswith("| **0**"))
        self.assertEqual(line2.split("|")[4].strip(), "0")
        self.assertEqual(line0.split("|")[3].strip(), "0")
        self.assertIn("| label 0 보여줌 | 37 | 17 | 0 |", rep)

    def test_errors_counted_as_keep(self):
        self.make_log(lambda cid, lab, r: None if r == 1 else False)
        rep = self.report()
        self.assertIn("실패는 keep으로 계산", rep)
        self.assertIn("| 보여준 총 개수 | 85 | 66 | 17 |", rep)

    def test_report_no_overwrite(self):
        self.make_log(lambda cid, lab, r: True)
        rep = Path(self.tmp.name) / "rep.md"
        rep.write_text("다른 내용")
        with self.assertRaises(SystemExit):
            RR.main(["--log", str(self.out), "--out", str(rep)])

    def test_baselines_match_m5_2b(self):
        ds, order, jlab, _ = S.load_inputs(HERE / "dataset.json", HERE / "runs" / "e5-small-ko.v1.1.json",
                                           HERE / "runs" / "llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl")
        r = S.evaluate(ds, {q["id"]: S.judge2_only(order[q["id"]], jlab, 5) for q in ds["queries"]}, 5)
        self.assertEqual((r["shown"], r["good"], r["bad"]), (66, 27, 17))


if __name__ == "__main__":
    unittest.main()
