#!/usr/bin/env python3
"""M5-2b selector 테스트 (stdlib unittest).  python3 -m unittest test_selector_experiment -v"""
import random
import unittest
from pathlib import Path

import selector_experiment as S

HERE = Path(__file__).resolve().parent
# e5 순서 a..h, judge label
ORDER = list("abcdefgh")
LAB = {"a": 0, "b": 2, "c": 1, "d": 0, "e": 2, "f": 2, "g": 1, "h": 0}  # Top5 = a b c d e


class PolicyTest(unittest.TestCase):
    def test_e5_top5_keeps_order_and_cap(self):
        self.assertEqual(S.e5_top5(ORDER, LAB, 5), list("abcde"))
        self.assertEqual(S.e5_top5(ORDER, LAB, 2), list("ab"))

    def test_judge2_only_uses_all_candidates_in_e5_order(self):
        self.assertEqual(S.judge2_only(ORDER, LAB, 5), list("bef"))
        self.assertEqual(S.judge2_only(ORDER, LAB, 1), ["b"])

    def test_remove_0_drops_zero_inside_top5_without_refill(self):
        self.assertEqual(S.remove_0(ORDER, LAB, 5), list("bce"))  # f(2) is outside Top5 → not added

    def test_refill_appends_label2_from_outside_top5_in_e5_order(self):
        self.assertEqual(S.remove_0_refill_2(ORDER, LAB, 5), list("bcef"))
        self.assertEqual(S.remove_0_refill_2(ORDER, LAB, 3), list("bce"))  # cap before refill is needed
        lab = dict(LAB, b=0, c=0, e=0)  # Top5 all 0 → only refill
        self.assertEqual(S.remove_0_refill_2(ORDER, lab, 5), ["f"])

    def test_label2_then_1(self):
        self.assertEqual(S.label2_then_1(ORDER, LAB, 5), list("befcg"))
        self.assertEqual(S.label2_then_1(ORDER, LAB, 4), list("befc"))

    def test_empty_result_when_judge_finds_nothing(self):
        zero = {k: 0 for k in ORDER}
        for name, fn in S.POLICIES:
            got = fn(ORDER, zero, 5)
            if fn is S.e5_top5:
                self.assertEqual(len(got), 5, "baseline never abstains")
            else:
                self.assertEqual(got, [], name)

    def test_cap_and_no_duplicates_randomized(self):
        rng = random.Random(0)
        for _ in range(300):
            n = rng.randint(1, 11)
            order = [f"x{i}" for i in range(n)]
            rng.shuffle(order)
            lab = {c: rng.choice([0, 1, 2]) for c in order}
            for name, fn in S.POLICIES:
                for cap in S.CAPS:
                    got = fn(order, lab, cap)
                    self.assertLessEqual(len(got), cap, name)
                    self.assertEqual(len(got), len(set(got)), f"duplicate in {name}")
                    self.assertTrue(set(got) <= set(order), name)
                    # 선택된 것끼리는 정책 안의 구간(2 먼저 / Top5 먼저)을 제외하면 e5 순서를 지킨다
                    if fn in (S.e5_top5, S.judge2_only, S.remove_0):
                        self.assertEqual(got, sorted(got, key=order.index), name)

    def test_pareto(self):
        pts = [("A", 10, 10), ("B", 8, 5), ("C", 8, 6), ("D", 5, 5)]
        self.assertEqual(S.pareto(pts), ["A", "B"])


class RealDataTest(unittest.TestCase):
    """저장된 frozen 입력으로 baseline이 기존 리포트 수치를 그대로 재현하는지."""

    @classmethod
    def setUpClass(cls):
        cls.ds, cls.order, cls.lab, _ = S.load_inputs(
            HERE / "dataset.json", HERE / "runs" / "e5-small-ko.v1.1.json",
            HERE / "runs" / "llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl")

    def run_policy(self, fn, cap):
        sel = {q["id"]: fn(self.order[q["id"]], self.lab, cap) for q in self.ds["queries"]}
        return S.evaluate(self.ds, sel, cap)

    def test_judge_complete(self):
        self.assertEqual(len(self.lab), 157)

    def test_e5_baseline_matches_m5_1(self):
        r = self.run_policy(S.e5_top5, 5)
        self.assertAlmostEqual(r["s"]["Good@5 (2 회수율, query 평균)"], 0.71, places=2)
        self.assertAlmostEqual(r["s"]["Bad% (보여준 것 중 0 비율, 전체)"], 0.44, places=2)
        self.assertEqual(r["shown"], 85)

    def test_judge2_only_matches_m5_2a(self):
        r = self.run_policy(S.judge2_only, 5)
        self.assertAlmostEqual(r["s"]["Good@5 (2 회수율, query 평균)"], 0.58, places=2)
        self.assertAlmostEqual(r["s"]["Bad% (보여준 것 중 0 비율, 전체)"], 0.26, places=2)
        self.assertEqual(r["shown"], 66)


if __name__ == "__main__":
    unittest.main()
