#!/usr/bin/env python3
"""M5-2d feasibility probe 준비/연결 테스트 (모델 호출 없음).  python3 -m unittest test_feasibility_probe -v
판정 결과는 정답과 무관한 가짜 label(pair 번호 기반)로 만든다."""
import contextlib
import hashlib
import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import feasibility_probe as P

HERE = Path(__file__).resolve().parent
PROTECTED = ["dataset.json", "runs/e5-small-ko.v1.1.json", "runs/llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl",
             "prompts/judge_v1.txt", "llm_judge.py", "llm_report.py", "compare_judges.py", "eval.py", "results-m5-2a.md"]


def md5s():
    return {p: hashlib.md5((HERE / p).read_bytes()).hexdigest() for p in PROTECTED}


class ProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = md5s()

    @classmethod
    def tearDownClass(cls):
        assert md5s() == cls.before, "보호 파일이 바뀜"

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        t = Path(self.tmp.name)
        self.blind, self.priv, self.runs = t / "blind", t / "private", t / "runs"
        self.res = self.blind / P.RESULT_NAME
        self.call("make")

    def tearDown(self):
        self.tmp.cleanup()

    def call(self, *args):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            try:
                rc = P.main([*args, "--blind-dir", str(self.blind), "--private-dir", str(self.priv),
                             "--runs-dir", str(self.runs), "--out", str(Path(self.tmp.name) / "report.md")])
            except SystemExit as e:
                rc = e.code
        return rc, buf.getvalue()

    def blind_rows(self):
        return [json.loads(l) for l in (self.blind / "blind_input.jsonl").read_text().splitlines()]

    def write_results(self, n=157, fmt=lambda r, i: {"pair_id": r["pair_id"], "reason": "가짜 근거", "label": i % 3}):
        self.res.write_text("".join(json.dumps(fmt(r, i), ensure_ascii=False) + "\n" for i, r in enumerate(self.blind_rows()[:n])))

    # ── blind input ──
    def test_blind_package_contents(self):
        self.assertEqual(sorted(p.name for p in self.blind.iterdir()),
                         ["INSTRUCTIONS.md", "blind_input.jsonl", "check_results.py", "judge_v1.txt"])
        self.assertFalse(any((self.blind / n).exists() for n in ("mapping.json", "dataset.json")))
        self.assertEqual((self.blind / "judge_v1.txt").read_bytes(), (HERE / "prompts" / "judge_v1.txt").read_bytes())

    def test_blind_rows_have_only_text(self):
        ds = json.loads((HERE / "dataset.json").read_text())
        rows = self.blind_rows()
        self.assertEqual(len(rows), 157)
        self.assertEqual([r["pair_id"] for r in rows], [f"p{i:03d}" for i in range(1, 158)])
        self.assertTrue(all(list(r) == ["pair_id", "current", "past"] for r in rows))
        blob = (self.blind / "blind_input.jsonl").read_text()
        cids = [c["id"] for q in ds["queries"] for c in q["candidates"]]
        for cid in cids:  # 원래 id(q01-a 등)가 없음
            self.assertNotIn(f'"{cid}"', blob)
        for c in (c for q in ds["queries"] for c in q["candidates"]):
            for k in ("case", "note", "date"):
                if c.get(k) and len(str(c[k])) >= 3:
                    self.assertNotIn(str(c[k]), blob, (c["id"], k))
        for word in ("label", "case", "note", "date", "e5", "cosine", "rank", "repeat_low_value", "worry_outcome"):
            self.assertNotIn(f'"{word}', blob)

    def test_mapping_roundtrip_and_shuffled(self):
        ds = json.loads((HERE / "dataset.json").read_text())
        m = json.loads((self.priv / "mapping.json").read_text())
        text = {c["id"]: (q["text"], c["text"]) for q in ds["queries"] for c in q["candidates"]}
        for r in self.blind_rows():
            self.assertEqual(text[m["mapping"][r["pair_id"]]], (r["current"], r["past"]))
        self.assertEqual(len(set(m["mapping"].values())), 157)
        order = [m["mapping"][f"p{i:03d}"] for i in range(1, 158)]
        dataset_order = [c["id"] for q in ds["queries"] for c in q["candidates"]]
        self.assertNotEqual(order, dataset_order)
        same_q = sum(a.split("-")[0] == b.split("-")[0] for a, b in zip(order, order[1:]))
        self.assertLess(same_q, 25)  # query별로 묶여 있지 않음 (무작위면 약 156/17 ≈ 9)

    def test_make_is_deterministic(self):
        first = (self.blind / "blind_input.jsonl").read_bytes()
        self.call("make")
        self.assertEqual((self.blind / "blind_input.jsonl").read_bytes(), first)

    def test_make_refuses_after_judging_started(self):
        self.write_results(3)
        rc, _ = self.call("make")
        self.assertIn("이미 있음", str(rc))

    # ── checker (정답을 읽지 않음) ──
    def test_checker_is_standalone(self):
        src = (self.blind / "check_results.py").read_text()
        for bad in ("dataset", "mapping", "e5", "runs/", "llm_judge"):
            self.assertNotIn(bad, src)

    def test_checker_progress_and_errors(self):
        rc, out = self.call("check")
        self.assertEqual(rc, 0)
        self.assertIn("완료 0/157 · 다음 p001", out)
        self.assertIn("553ab6176c0293f9", out)
        self.write_results(20)
        rc, out = self.call("check")
        self.assertIn("OK · 완료 20/157 · 다음 p021", out)
        bad = [{"pair_id": "p001", "reason": "x", "label": 3}, {"pair_id": "p002", "reason": " ", "label": 1},
               {"pair_id": "p002", "reason": "x", "label": True}, {"pair_id": "p009", "reason": "x", "label": 0, "extra": 1},
               {"pair_id": "q01-a", "reason": "x", "label": 0}]
        self.res.write_text("".join(json.dumps(b) + "\n" for b in bad) + "not json\n")
        rc, out = self.call("check")
        self.assertNotEqual(rc, 0)
        for msg in ("label은 정수", "reason이 비어", "중복", "key는", "모르는 pair_id", "JSON 오류"):
            self.assertIn(msg, out)

    def test_checker_requires_input_order(self):
        rows = self.blind_rows()
        self.res.write_text("".join(json.dumps({"pair_id": r["pair_id"], "reason": "x", "label": 0}) + "\n" for r in (rows[1], rows[0])))
        rc, out = self.call("check")
        self.assertIn("순서", out)

    # ── join / report ──
    def test_join_refused_before_complete(self):
        self.write_results(156)
        rc, _ = self.call("join")
        self.assertIn("157/157 전에는", str(rc))
        self.assertFalse(self.runs.exists())

    def test_join_refused_if_blind_input_changed(self):
        self.write_results()
        p = self.blind / "blind_input.jsonl"
        p.write_text(p.read_text().replace("p001", "p001", 1) + "\n")
        rc, _ = self.call("join")
        self.assertIn("바뀜", str(rc))

    def test_join_and_report(self):
        self.write_results()
        rc, out = self.call("join")
        self.assertEqual(rc, 0, out)
        raw = self.runs / P.RESULT_NAME
        joined = self.runs / "llm-claude-code-feasibility-judge_v1.jsonl"
        self.assertEqual(raw.read_bytes(), self.res.read_bytes())  # 판정 원본은 그대로
        recs = [json.loads(l) for l in joined.read_text().splitlines()]
        meta, js = recs[0], recs[1:]
        self.assertEqual((meta["type"], meta["prompt_sha"], meta["dataset_version"]), ("meta", "553ab6176c0293f9", "1.1"))
        self.assertEqual(len(js), 157)
        m = json.loads((self.priv / "mapping.json").read_text())["mapping"]
        src = {json.loads(l)["pair_id"]: json.loads(l) for l in self.res.read_text().splitlines()}
        for r in js:
            self.assertEqual(m[r["pair_id"]], r["cid"])
            self.assertEqual(src[r["pair_id"]]["label"], r["label"])
            self.assertEqual((r["attempt"], r["status"]), (1, "ok"))
        # 같은 내용이면 다시 실행 가능, 다르면 덮어쓰기 거부
        self.assertEqual(self.call("join")[0], 0)
        self.write_results(fmt=lambda r, i: {"pair_id": r["pair_id"], "reason": "다른 근거", "label": 0})
        self.assertIn("덮어쓰지 않는다", str(self.call("join")[0]))
        # report
        rc, out = self.call("report")
        self.assertEqual(rc, 0, out)
        rep = (Path(self.tmp.name) / "report.md").read_text()
        self.assertTrue(rep.startswith("# M5-2d 결과 · strong-model feasibility probe"))
        self.assertIn("feasibility probe이며, 공정한 model benchmark가 아니다", rep)
        self.assertNotIn("judge_v2는 이 평가셋", rep)
        for s in ("`e5 Top5`", "`qwen2b_judge_v1 all`", "`claude_code_probe all`", "Good@5", "nDCG", "Worth%", "Bad%",
                  "Top1=0", "Quiet miss", "q02", "q15", "q16", "q17", "repeat_low_value", "lexical_trap", "resolve_action",
                  "worry_outcome", "implicit_link", "recurring", "change", "reversal"):
            self.assertIn(s, rep, s)

    def test_cli_help(self):
        r = subprocess.run([sys.executable, "feasibility_probe.py", "--help"], cwd=HERE, capture_output=True, text=True)
        self.assertEqual(r.returncode, 0)


if __name__ == "__main__":
    unittest.main()
