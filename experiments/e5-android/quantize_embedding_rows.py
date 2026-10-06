#!/usr/bin/env python3
"""
INT8 word-embedding table, one scale PER ROW (= per token), everything else FP32 — the most conservative INT8 that
still removes most of the size (the 250,037 × 384 table is ~384 MB of the 470 MB FP32 file).

ORT dynamic quantization of Gather uses ONE scale for the whole table (checked: DequantizeLinear without axis), which
moved rankings. Here, by hand (opset 17 ops only):
    Gather(W_int8, input_ids) → Cast(float) → Mul(Gather(scale[V,1], input_ids))   ==  W_fp32[input_ids] (≈)
symmetric: scale = max|row| / 127, q = round(row / scale). Encoder MatMuls, pooling and normalize are untouched.

  python3 quantize_embedding_rows.py   → out/e5-small-ko-v2-fcfc26bf.int8-embrows.onnx   (FP32 file only read)
"""
import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper
from common import OUT

FP32 = OUT / "e5-small-ko-v2-fcfc26bf.onnx"
DST = OUT / "e5-small-ko-v2-fcfc26bf.int8-embrows.onnx"
TABLE = "bert.embeddings.word_embeddings.weight"


def main():
    m = onnx.load(str(FP32))
    inits = {i.name: i for i in m.graph.initializer}
    w = numpy_helper.to_array(inits[TABLE]).astype(np.float32)
    scale = np.abs(w).max(axis=1, keepdims=True) / 127.0
    scale[scale == 0] = 1.0
    q = np.clip(np.rint(w / scale), -127, 127).astype(np.int8)
    err = np.abs(q.astype(np.float32) * scale - w)
    print(f"table {w.shape} · max |row err| {err.max():.2e} · mean {err.mean():.2e}")

    gather = next(n for n in m.graph.node if n.op_type == "Gather" and n.input[0] == TABLE)
    ids, out = gather.input[1], gather.output[0]
    m.graph.initializer.remove(inits[TABLE])
    m.graph.initializer.extend([numpy_helper.from_array(q, TABLE + "_int8"),
                                numpy_helper.from_array(scale.astype(np.float32), TABLE + "_row_scale")])
    idx = list(m.graph.node).index(gather)
    m.graph.node.remove(gather)
    new = [
        helper.make_node("Gather", [TABLE + "_int8", ids], [out + "_q"], name="word_embeddings/GatherInt8"),
        helper.make_node("Gather", [TABLE + "_row_scale", ids], [out + "_s"], name="word_embeddings/GatherScale"),
        helper.make_node("Cast", [out + "_q"], [out + "_f"], to=TensorProto.FLOAT, name="word_embeddings/Cast"),
        helper.make_node("Mul", [out + "_f", out + "_s"], [out], name="word_embeddings/Dequant"),
    ]
    for k, n in enumerate(new):
        m.graph.node.insert(idx + k, n)
    onnx.checker.check_model(m)
    onnx.save(m, str(DST))
    print("→", DST, DST.stat().st_size)


if __name__ == "__main__":
    main()
