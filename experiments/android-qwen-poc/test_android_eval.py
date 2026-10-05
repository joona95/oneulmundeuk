#!/usr/bin/env python3
"""android_eval / poc_judge all task 테스트 (가짜 session · fake server).  python3 -m unittest test_android_eval -v"""
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

import android_eval as E
import poc_judge as P
import test_poc_judge as T


def fake_session(path, labels, plan, drop=0, server_args=None):
    meta = {"type": "meta", "prompt_sha": P.EXPECTED_PROMPT_SHA,
            "server_args": server_args or P.server_args(P.argparse.Namespace(port=8089, threads=0, server_arg=None)),
            "request_example": {"messages": [], "temperature": 0}, "tasks": ["all"],
            "device": {"model": "TEST", "soc": "X", "android": "16", "mem": "MemTotal: 1 kB", "build_info": "llama.cpp ref: test\nndk\nmarch: x",
                       "model_file": "-rw-rw-rw- 1 shell shell 1274396992 2026-10-05 11:35 /data/local/tmp/qwen-poc/models/m.gguf"}}
    lines = [meta, {"type": "load", "load_ms": 3000, "mem": {"alive": True, "VmHWM": 2500.0}}]
    for i, (q, c, _) in enumerate(plan[:len(plan) - drop], 1):
        lab = labels(c["id"])
        lines.append({"type": "judgment", "task": "all", "idx": i, "cid": c["id"], "status": "ok" if lab is not None else "error",
                      "label": lab, "reason": "r" if lab is not None else None, "error": None if lab is not None else "invalid label: 9",
                      "raw": "{}", "wall_ms": 2000 + i, "prompt_n": 60, "prompt_ms": 500.0, "predicted_n": 50, "predicted_ms": 1800.0,
                      "mem": {"alive": True, "VmHWM": 2600.0}})
    Path(path).write_text("\n".join(json.dumps(l, ensure_ascii=False) for l in lines) + "\n")


class EvalTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = Path(self.tmp.name)
        self.ds, self.plan = P.load_pairs()
        self.mac = P.mac_labels()

    def tearDown(self):
        self.tmp.cleanup()

    def run_eval(self, session):
        args = ["--session", str(session), "--out", str(self.d / "r.md"), "--csv", str(self.d / "p.csv"), "--converted", str(self.d / "c.jsonl")]
        with contextlib.redirect_stdout(io.StringIO()):
            try:
                return E.main(args)
            except SystemExit as e:
                return e.code

    def test_same_labels_give_same_metrics_and_full_agreement(self):
        fake_session(self.d / "s.jsonl", lambda cid: self.mac[cid], self.plan)
        self.assertEqual(self.run_eval(self.d / "s.jsonl"), 0)
        rep = (self.d / "r.md").read_text()
        self.assertIn("일치 157/157 (100%)", rep)
        self.assertIn("| 보여준 총 개수 | 66 | 66 | +0 |", rep)       # Mac judge_v1 = M5-2b judge2_only@5
        self.assertIn("| label 0 보여줌 | 17 | 17 | +0 |", rep)
        for s in ("Good@5", "nDCG@5", "Worth%", "Bad%", "Top1=0", "Quiet miss", "F7 0개 반환", "peak RSS", "confusion", *E.CASES, *E.NOLAB2):
            self.assertIn(s, rep)
        csv_rows = (self.d / "p.csv").read_text().splitlines()
        self.assertEqual(len(csv_rows), 158)
        conv = [json.loads(l) for l in (self.d / "c.jsonl").read_text().splitlines()]
        self.assertEqual(sum(r["type"] == "judgment" and r["attempt"] == 1 for r in conv), 157)

    def test_failures_count_as_not_shown(self):
        fake_session(self.d / "s.jsonl", lambda cid: None if self.mac[cid] == 2 else self.mac[cid], self.plan)
        self.assertEqual(self.run_eval(self.d / "s.jsonl"), 0)
        rep = (self.d / "r.md").read_text()
        self.assertIn("| label 2 보여줌 | 27 | 0 | -27 |", rep)
        self.assertIn("실패", rep)

    def test_incomplete_session_refused(self):
        fake_session(self.d / "s.jsonl", lambda cid: 2, self.plan, drop=1)
        self.assertIn("157쌍", str(self.run_eval(self.d / "s.jsonl")))

    def test_different_server_args_refused(self):
        fake_session(self.d / "s.jsonl", lambda cid: 2, self.plan, server_args=["-m", "x", "--port", "8089", "--temp", "0.7"])
        self.assertIn("설정이 PoC", str(self.run_eval(self.d / "s.jsonl")))

    def test_converted_not_overwritten(self):
        fake_session(self.d / "s.jsonl", lambda cid: 0, self.plan)
        (self.d / "c.jsonl").write_text("다른 내용")
        self.assertIn("덮어쓰지", str(self.run_eval(self.d / "s.jsonl")))


class AllTaskTest(T.PocTest):
    def test_all_task_sends_157_in_mac_order(self):
        rc, out = self.run_session("--tasks", "all")
        self.assertEqual(rc, 0, out)
        self.assertEqual(len(T.FakeServer.seen), 157)
        _, plan = P.load_pairs()
        recs = [json.loads(l) for f in Path(self.tmp.name).glob("session-*.jsonl") for l in f.read_text().splitlines()]
        self.assertEqual([r["cid"] for r in recs if r["type"] == "judgment"], [c["id"] for _, c, _ in plan])
        mac_order = []
        for l in P.QWEN_LOG.read_text().splitlines():
            r = json.loads(l)
            if r.get("type") == "judgment" and r["attempt"] == 1 and r["cid"] not in mac_order:
                mac_order.append(r["cid"])
        self.assertEqual(mac_order, [c["id"] for _, c, _ in plan])


if __name__ == "__main__":
    unittest.main()
