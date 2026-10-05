#!/usr/bin/env python3
"""M5-3 e5 threshold 실험 테스트 (모델 호출 없음).  python3 -m unittest test_e5_threshold_experiment -v"""
import contextlib
import hashlib
import io
import json
import tempfile
import unittest
from pathlib import Path

import e5_threshold_experiment as T
import selector_experiment as S

HERE = Path(__file__).resolve().parent
PROTECTED = ["dataset.json", "runs/e5-small-ko.v1.1.json", "eval.py", "selector_experiment.py", "llm_report.py",
             "results-m5-2a.md", "results-m5-2b-selector.md"]


def md5s():
    return {p: hashlib.md5((HERE / p).read_bytes()).hexdigest() for p in PROTECTED}


def real():
    return T.load(HERE / "dataset.json", HERE / "runs" / "e5-small-ko.v1.1.json")


class UnitTest(unittest.TestCase):
    RANKED = {"q1": [("a", 8000), ("b", 6500), ("c", 6499), ("d", 4000)], "q2": [("e", 5000)]}

    def test_select_inclusive_ordered_capped(self):
        self.assertEqual(T.select(self.RANKED, 6500, 5), {"q1": ["a", "b"], "q2": []})
        self.assertEqual(T.select(self.RANKED, 0, 2), {"q1": ["a", "b"], "q2": ["e"]})
        self.assertEqual(T.select(self.RANKED, 9000, 5), {"q1": [], "q2": []})

    def test_to_int_exact(self):
        self.assertEqual(T.to_int(0.6500), 6500)
        self.assertEqual(T.to_int(0.8809), 8809)
        self.assertEqual(T.to_int(0.35 + 0.01 * 7), 4200)

    def test_grid(self):
        self.assertEqual(T.grid(self.RANKED), list(range(4000, 8001, 100)))
        self.assertEqual(T.grid({"q": [("x", 3536), ("y", 8809)]})[:2], [3500, 3600])
        self.assertEqual(T.grid({"q": [("x", 3536), ("y", 8809)]})[-1], 8900)

    def test_quantile(self):
        xs = [1, 2, 3, 4, 5]
        self.assertEqual([T.quantile(xs, p) for p in (0, .25, .5, .9, 1)], [1, 2, 3, 4.6, 5])

    def test_pareto(self):
        mk = lambda g, b, k: {"key": k, "r": {"good": g, "bad": b}}
        rows = [mk(10, 5, "a"), mk(8, 2, "b"), mk(8, 4, "c"), mk(10, 6, "d"), mk(10, 5, "a")]
        front = T.pareto(rows, [(lambda r: r["r"]["good"], 1), (lambda r: r["r"]["bad"], -1)])
        self.assertEqual([r["key"] for r in front], ["a", "b"])

    def test_candidate_rule(self):
        mk = lambda t, cap, g, b, sh: {"t": t, "cap": cap, "r": {"good": g, "bad": b, "shown": sh}}
        rows = [mk(6000, 5, 30, 10, 50), mk(6100, 5, 30, 1, 40),  # 0.61은 0.05 배수가 아님 → 제외
                mk(6500, 5, 25, 6, 35), mk(6500, 3, 25, 6, 30), mk(7000, 1, 15, 2, 15)]
        c = {code: r for code, _, _, r in T.candidates(rows, 32)}
        self.assertEqual((c["A"]["t"], c["A"]["cap"]), (6000, 5))     # ≥28.8 → 30만
        self.assertEqual((c["B"]["t"], c["B"]["cap"]), (6500, 3))     # ≥24 → bad 같음, shown 적은 N=3
        self.assertEqual((c["C"]["t"], c["C"]["cap"]), (6500, 3))     # ≥16: 15 < 16 → 0.65·N=3
        rows[-1]["r"]["good"] = 16
        self.assertEqual(T.candidates(rows, 32)[2][3]["t"], 7000)


class RealDataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = md5s()
        cls.ds, cls.e5, cls.ranked = real()

    @classmethod
    def tearDownClass(cls):
        assert md5s() == cls.before, "보호 파일이 바뀜"

    def test_scores_loaded_in_e5_order(self):
        self.assertEqual(sum(len(v) for v in self.ranked.values()), 157)
        for qid, lst in self.ranked.items():
            self.assertEqual([c for c, _ in lst], [x["id"] for x in self.e5["rankings"][qid]])

    def test_grid_covers_range(self):
        g = T.grid(self.ranked)
        sc = [s for v in self.ranked.values() for _, s in v]
        self.assertLessEqual(g[0], min(sc))
        self.assertGreaterEqual(g[-1], max(sc))
        self.assertTrue(all(b - a == 100 for a, b in zip(g, g[1:])))

    def test_lowest_threshold_is_e5_top5(self):
        lo = T.grid(self.ranked)[0]
        sel = T.select(self.ranked, lo, 5)
        self.assertEqual(sel, {qid: [c for c, _ in lst][:5] for qid, lst in self.ranked.items()})
        r = S.evaluate(self.ds, sel, 5)
        self.assertEqual((r["shown"], r["good"], r["bad"]), (85, 33, 37))  # M5-2b A@5와 같음

    def test_monotone(self):
        rows = T.evaluate_all(self.ds, self.ranked)
        self.assertEqual(len(rows), len(T.grid(self.ranked)) * 3)
        for cap in T.CAPS:
            shown = [r["r"]["shown"] for r in rows if r["cap"] == cap]
            self.assertEqual(shown, sorted(shown, reverse=True))
        top = [r for r in rows if r["t"] == T.grid(self.ranked)[-1]]
        self.assertTrue(all(r["r"]["shown"] == 0 and r["zero_q"] == 17 for r in top))

    def test_distribution_counts(self):
        by, stats, ov, _ = T.distribution(self.ds, self.ranked)
        self.assertEqual({g: stats[g]["count"] for g in (2, 1, 0)}, {2: 47, 1: 33, 0: 77})
        for g in (2, 1, 0):
            s = stats[g]
            self.assertTrue(s["min"] <= s["p10"] <= s["p25"] <= s["median"] <= s["p75"] <= s["p90"] <= s["max"])
        self.assertTrue(0 <= ov["auc_2_vs_0"] <= 1)

    def test_main_writes_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as d:
            out, csvp = Path(d) / "r.md", Path(d) / "s.csv"
            with contextlib.redirect_stdout(io.StringIO()):
                T.main(["--out", str(out), "--csv", str(csvp)])
            rep = out.read_text()
            for s in ("development benchmark", "최적 threshold", "## 1.", "## 2.", "## 5. Pareto", "## 6. MVP", "q02", "q17",
                      "repeat_low_value", "unrelated", "reversal"):
                self.assertIn(s, rep)
            self.assertEqual(len(csvp.read_text().splitlines()), 1 + len(T.grid(self.ranked)) * 3)
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit):
                T.main(["--out", str(out)])


if __name__ == "__main__":
    unittest.main()
