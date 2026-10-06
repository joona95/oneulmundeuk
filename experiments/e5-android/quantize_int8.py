#!/usr/bin/env python3
"""
INT8 variants of the FP32 e5 ONNX (the FP32 file is only read, never written). ONNX Runtime dynamic quantization:
weights stored as int8 (symmetric QInt8, per output channel where the op allows), activations quantized at run time —
no calibration data, the most conservative ORT INT8 mode. Two scopes:

  matmul        : only MatMul weights (encoder; the 250k-row word-embedding table stays FP32)
  matmul-gather : MatMul + Gather (also the word-embedding table — that is ~384 MB of the 470 MB)
  gather        : only Gather (the embedding tables → int8 + DequantizeLinear; every MatMul stays FP32)

  python3 quantize_int8.py [--variant matmul|matmul-gather|gather|all]
    → out/e5-small-ko-v2-fcfc26bf.int8-<variant>.onnx
"""
import argparse
from onnxruntime.quantization import QuantType, quantize_dynamic
from common import OUT

FP32 = OUT / "e5-small-ko-v2-fcfc26bf.onnx"
VARIANTS = {"matmul": ["MatMul"], "matmul-gather": ["MatMul", "Gather"], "gather": ["Gather"]}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--variant", choices=[*VARIANTS, "all"], default="all")
    a = ap.parse_args()
    for name in (VARIANTS if a.variant == "all" else [a.variant]):
        out = OUT / f"e5-small-ko-v2-fcfc26bf.int8-{name}.onnx"
        assert out != FP32
        quantize_dynamic(str(FP32), str(out), op_types_to_quantize=VARIANTS[name], per_channel=True,
                         weight_type=QuantType.QInt8)
        print("→", out, out.stat().st_size)


if __name__ == "__main__":
    main()
