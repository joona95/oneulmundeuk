#!/usr/bin/env python3
"""
ONNX (onnxruntime CPU, the same engine as ONNX Runtime Android) vs the sentence-transformers reference.

  python3 parity.py      (after make_reference.py + export_onnx.py)  → out/parity.json + printed summary
  python3 parity.py --model out/<other>.onnx --report out/parity-<name>.json     (e.g. an INT8 variant)

1. embeddings: same token ids as the reference → ONNX embedding vs reference embedding (dim, norm, max |diff|, cosine)
2. ranking: M5 related set (record ↔ record, both "query: ") Top-5 and S1 search (query ↔ "passage: ") Top-10,
   recomputed from ONNX embeddings vs (a) the same ranking from reference embeddings, (b) the stored M5 / S1 runs.
3. host latency: one text at a time (as the app would), 4 lengths. Host only — not an Android number.
"""
import argparse
import json
import time
from pathlib import Path
import numpy as np
import onnxruntime as ort
from common import EXP, OUT

TOL_MAX_ABS = 1e-4     # per-dimension
TOL_COS = 0.99999      # cosine(reference, onnx)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default=OUT / "e5-small-ko-v2-fcfc26bf.onnx")
    ap.add_argument("--report", default=OUT / "parity.json")
    a = ap.parse_args()
    ref = json.loads((OUT / "reference.json").read_text(encoding="utf-8"))
    sess = ort.InferenceSession(str(a.model), providers=["CPUExecutionProvider"])
    io = ([i.name for i in sess.get_inputs()], [o.name for o in sess.get_outputs()], sess.get_outputs()[0].shape)

    def run(ids):
        x = np.array([ids], dtype=np.int64)
        return sess.run(None, {"input_ids": x, "attention_mask": np.ones_like(x)})[0][0]

    onnx_emb, worst = {}, []
    for it in ref["items"]:
        r = np.array(it["emb"], dtype=np.float32)
        o = run(it["ids"])
        onnx_emb[it["text"]] = o
        worst.append((float(np.max(np.abs(o - r))), float(np.dot(o, r) / np.linalg.norm(o) / np.linalg.norm(r)),
                      float(np.linalg.norm(o)), it["id"], len(it["ids"])))
    ref_emb = {it["text"]: np.array(it["emb"], dtype=np.float32) for it in ref["items"]}
    max_abs = max(w[0] for w in worst)
    min_cos = min(w[1] for w in worst)
    norms = (min(w[2] for w in worst), max(w[2] for w in worst))

    def rank_m5(emb):
        ds = json.loads((EXP / "related" / "dataset.json").read_text(encoding="utf-8"))
        out = {}
        for q in ds["queries"]:
            qv = emb["query: " + q["text"]]
            sc = [(round(float(np.dot(qv, emb["query: " + c["text"]])), 4), c["id"]) for c in q["candidates"]]
            out[q["id"]] = sorted(sc, key=lambda x: (-x[0], x[1]))
        return out

    def rank_s1(emb):
        ds = json.loads((EXP / "search" / "dataset.json").read_text(encoding="utf-8"))
        out = {}
        for q in ds["queries"]:
            qv = emb["query: " + q["text"]]
            sc = [(round(float(np.dot(qv, emb["passage: " + r["text"]])), 4), r["id"]) for r in ds["records"]]
            out[q["id"]] = sorted(sc, key=lambda x: (-x[0], x[1]))
        return out

    def stored(path):
        d = json.loads(path.read_text(encoding="utf-8"))
        return {k: [(x["score"], x["id"]) for x in v] for k, v in d["rankings"].items()}

    def compare(a, b, k):
        same_order = sum(1 for q in a if [i for _, i in a[q][:k]] == [i for _, i in b[q][:k]])
        same_set = sum(1 for q in a if {i for _, i in a[q][:k]} == {i for _, i in b[q][:k]})
        diff = max(abs(sa - dict((i, s) for s, i in b[q])[ia]) for q in a for sa, ia in a[q])
        full = sum(1 for q in a if [i for _, i in a[q]] == [i for _, i in b[q]])
        return {"queries": len(a), f"top{k}_same_order": same_order, f"top{k}_same_set": same_set,
                "full_ranking_same": full, "max_score_diff": round(diff, 4)}

    m5o, m5r = rank_m5(onnx_emb), rank_m5(ref_emb)
    s1o, s1r = rank_s1(onnx_emb), rank_s1(ref_emb)
    result = {
        "io": {"inputs": io[0], "outputs": io[1], "output_shape": io[2]},
        "embeddings": {"texts": len(worst), "dim": int(len(next(iter(onnx_emb.values())))), "max_abs_diff": max_abs,
                       "min_cosine": min_cos, "onnx_norm_range": norms, "tol_max_abs": TOL_MAX_ABS, "tol_cos": TOL_COS,
                       "pass": max_abs <= TOL_MAX_ABS and min_cos >= TOL_COS,
                       "worst": sorted(worst, reverse=True)[:3]},
        "m5_top5_onnx_vs_reference_now": compare(m5o, m5r, 5),
        "m5_top5_onnx_vs_stored_run": compare(m5o, stored(EXP / "related" / "runs" / "e5-small-ko.v1.1.json"), 5),
        "m5_reference_now_vs_stored_run": compare(m5r, stored(EXP / "related" / "runs" / "e5-small-ko.v1.1.json"), 5),
        "s1_top10_onnx_vs_reference_now": compare(s1o, s1r, 10),
        "s1_top10_onnx_vs_stored_run": compare(s1o, stored(EXP / "search" / "runs" / "e5-small-ko.s1.json"), 10),
        "s1_reference_now_vs_stored_run": compare(s1r, stored(EXP / "search" / "runs" / "e5-small-ko.s1.json"), 10),
        "reference_versions": ref["versions"],
        "onnxruntime": ort.__version__,
    }
    lat = {}
    for name, n in [("short(8)", 8), ("medium(48)", 48), ("long(128)", 128), ("max(512)", 512)]:
        ids = [0] + [6] * (n - 2) + [2]
        run(ids)
        t0 = time.perf_counter()
        for _ in range(10):
            run(ids)
        lat[name] = round((time.perf_counter() - t0) / 10 * 1000, 1)
    result["host_latency_ms_single_text"] = lat
    result["model"] = {"file": Path(a.model).name, "bytes": Path(a.model).stat().st_size}
    Path(a.report).write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
