#!/usr/bin/env python3
"""
Python reference (sentence-transformers, the M5 path) for every fixture text:
  out/reference.json : {model, revision, versions, items: [{id, text, ids, emb}]}
  out/tokens.tsv     : id \t base64(utf-8 text) \t comma ids   (read by the Kotlin tokenizer parity check)

  python3 make_reference.py --model-dir <snapshot dir>
"""
import argparse
import base64
import json
import platform
import numpy as np
import sentence_transformers
import torch
import transformers
from common import OUT, MODEL_ID, REVISION, fixture_texts, load_st


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model-dir", required=True)
    a = ap.parse_args()
    st = load_st(a.model_dir)
    items = fixture_texts()
    texts = [t for _, t in items]
    ids = [st.tokenize([t])["input_ids"][0].tolist() for t in texts]  # exactly what encode() feeds the model
    emb = st.encode(texts, batch_size=8, normalize_embeddings=True, convert_to_numpy=True).astype(np.float32)
    OUT.mkdir(exist_ok=True)
    (OUT / "reference.json").write_text(json.dumps({
        "model": MODEL_ID, "revision": REVISION,
        "versions": {"sentence_transformers": sentence_transformers.__version__, "transformers": transformers.__version__,
                     "torch": torch.__version__, "machine": platform.machine()},
        "items": [{"id": i, "text": t, "ids": x, "emb": [float(v) for v in e]} for (i, t), x, e in zip(items, ids, emb)],
    }, ensure_ascii=False), encoding="utf-8")
    with open(OUT / "tokens.tsv", "w", encoding="utf-8") as f:
        for (i, t), x in zip(items, ids):
            f.write(f"{i}\t{base64.b64encode(t.encode('utf-8')).decode()}\t{','.join(map(str, x))}\n")
    print(f"{len(items)} texts, dim {emb.shape[1]}, max ids {max(map(len, ids))}")


if __name__ == "__main__":
    main()
