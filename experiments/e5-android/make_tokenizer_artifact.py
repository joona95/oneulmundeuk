#!/usr/bin/env python3
"""
tokenizer.json (HF fast XLMRobertaTokenizer: Precompiled + Replace(' {2,}') normalizer, Metaspace(prepend always),
Unigram, <s> A </s>) → a compact UTF-8 text file the Android tokenizer (kotlin/XlmrTokenizer.kt) reads without a JSON
library. Deterministic: same tokenizer.json → same bytes. Fails loudly if the tokenizer config is not the expected one.

  python3 make_tokenizer_artifact.py --model-dir <snapshot dir>    → out/e5-small-ko-v2-tokenizer.txt

Format (one record per line, TAB separated):
  #oneulmundeuk xlmr-unigram v1
  unk_id 3 | bos_id 0 | eos_id 2 | pad_id 1 | max_length 512 | min_score <float>
  charsmap <base64 of precompiled_charsmap>
  added <id> <content>          (special tokens matched before normalization)
  vocab <count>
  <piece> <score>               (count lines, line index = token id)
"""
import argparse
import json
from pathlib import Path
from common import OUT, MAX_SEQ


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model-dir", required=True)
    ap.add_argument("--out", default=OUT / "e5-small-ko-v2-tokenizer.txt")
    a = ap.parse_args()
    t = json.loads((Path(a.model_dir) / "tokenizer.json").read_text(encoding="utf-8"))
    norms = t["normalizer"]["normalizers"]
    assert [n["type"] for n in norms] == ["Precompiled", "Replace"], norms
    assert norms[1]["pattern"] == {"Regex": " {2,}"} and norms[1]["content"] == " "
    assert t["pre_tokenizer"] == {"type": "Metaspace", "replacement": "▁", "prepend_scheme": "always", "split": True}
    m = t["model"]
    assert m["type"] == "Unigram" and m["unk_id"] == 3 and not m.get("byte_fallback")
    pp = t["post_processor"]
    assert pp["type"] == "TemplateProcessing" and [list(x)[0] for x in pp["single"]] == ["SpecialToken", "Sequence", "SpecialToken"]
    assert t["truncation"] is None and t["padding"] is None
    vocab = m["vocab"]
    for piece, _ in vocab:
        assert "\t" not in piece and "\n" not in piece and "\r" not in piece, repr(piece)
    added = [(x["id"], x["content"]) for x in t["added_tokens"]]
    assert all(x["special"] and not x["normalized"] for x in t["added_tokens"])
    lines = ["#oneulmundeuk xlmr-unigram v1",
             "unk_id\t3", "bos_id\t0", "eos_id\t2", "pad_id\t1", f"max_length\t{MAX_SEQ}",
             f"min_score\t{repr(min(s for _, s in vocab))}",
             f"charsmap\t{norms[0]['precompiled_charsmap']}"]
    lines += [f"added\t{i}\t{c}" for i, c in added]
    lines.append(f"vocab\t{len(vocab)}")
    lines += [f"{p}\t{repr(float(s))}" for p, s in vocab]
    OUT.mkdir(exist_ok=True)
    Path(a.out).write_bytes(("\n".join(lines) + "\n").encode("utf-8"))
    print("→", a.out, len(vocab), "pieces")


if __name__ == "__main__":
    main()
