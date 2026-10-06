#!/usr/bin/env python3
"""
Export dragonkue/multilingual-e5-small-ko-v2 @ fcfc26bf to ONNX (FP32, no quantization) with the sentence-transformers
pipeline INSIDE the graph: BertModel → mean pooling over attention_mask → L2 normalize.

  inputs : input_ids int64 [batch, seq], attention_mask int64 [batch, seq]   (token_type_ids = 0 inside, as HF does)
  output : sentence_embedding float32 [batch, 384], already L2-normalized (cosine = dot product)

  python3 export_onnx.py --model-dir <snapshot dir>     → out/e5-small-ko-v2-fcfc26bf.onnx
"""
import argparse
import json
from pathlib import Path
import torch
from common import OUT, load_st


class E5(torch.nn.Module):
    def __init__(self, bert):
        super().__init__()
        self.bert = bert

    def forward(self, input_ids, attention_mask):
        h = self.bert(input_ids=input_ids, attention_mask=attention_mask,
                      token_type_ids=torch.zeros_like(input_ids)).last_hidden_state
        m = attention_mask.unsqueeze(-1).to(h.dtype)
        pooled = (h * m).sum(1) / m.sum(1).clamp(min=1e-9)  # = sentence_transformers Pooling(mean)
        return torch.nn.functional.normalize(pooled, p=2, dim=1)  # = sentence_transformers Normalize


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model-dir", required=True)
    ap.add_argument("--out", default=OUT / "e5-small-ko-v2-fcfc26bf.onnx")
    a = ap.parse_args()
    st = load_st(a.model_dir)
    names = [type(m).__name__ for m in st]
    assert names == ["Transformer", "Pooling", "Normalize"], names
    pool = json.loads((Path(a.model_dir) / "1_Pooling" / "config.json").read_text())  # the config ST loaded
    modes = {k for k, v in pool.items() if k.startswith("pooling_mode_") and v}
    assert modes == {"pooling_mode_mean_tokens"} and pool["word_embedding_dimension"] == 384, pool
    model = E5(st[0].auto_model).eval()
    ids = st.tokenize(["query: 안녕하세요", "query: 조금 더 긴 두 번째 문장"])
    OUT.mkdir(exist_ok=True)
    with torch.no_grad():
        torch.onnx.export(
            model, (ids["input_ids"], ids["attention_mask"]), str(a.out),
            input_names=["input_ids", "attention_mask"], output_names=["sentence_embedding"],
            dynamic_axes={"input_ids": {0: "batch", 1: "seq"}, "attention_mask": {0: "batch", 1: "seq"},
                          "sentence_embedding": {0: "batch"}},
            opset_version=17, do_constant_folding=True,
        )
    import onnx
    onnx.checker.check_model(str(a.out))
    print("→", a.out)


if __name__ == "__main__":
    main()
