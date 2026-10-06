#!/usr/bin/env python3
"""
Tokenizer-only fuzz fixture: N seeded random strings mixing Hangul / jamo / Latin / digits / punctuation / CJK / emoji /
combining marks / whitespace & control chars → out/tokens_fuzz.tsv (same format as tokens.tsv), ids from the HF fast
tokenizer exactly as sentence-transformers calls it (truncation 512).

  python3 make_token_fuzz.py --model-dir <snapshot dir> [--n 3000] [--seed 7]
"""
import argparse
import base64
import random
from common import OUT, load_st

POOLS = [
    (0xAC00, 0xD7A3), (0x3131, 0x318E), (0x1100, 0x11FF), (0x0041, 0x007A), (0x0030, 0x0039), (0x0021, 0x002F),
    (0x4E00, 0x4FFF), (0x3040, 0x30FF), (0xFF01, 0xFF5E), (0x0300, 0x036F), (0x1F300, 0x1F64F), (0x2000, 0x206F),
    (0x00A0, 0x00FF), (0x0E00, 0x0E7F), (0x0600, 0x06FF), (0x2460, 0x24FF), (0x1F1E6, 0x1F1FF), (0x20000, 0x2001F),
]
WS = [" ", "  ", "\t", "\n", "　", "​", "‍"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model-dir", required=True)
    ap.add_argument("--n", type=int, default=3000)
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    rnd = random.Random(a.seed)
    st = load_st(a.model_dir)
    with open(OUT / "tokens_fuzz.tsv", "w", encoding="utf-8") as f:
        for i in range(a.n):
            parts = []
            for _ in range(rnd.randint(1, 40)):
                if rnd.random() < 0.15:
                    parts.append(rnd.choice(WS))
                else:
                    lo, hi = rnd.choice(POOLS)
                    parts.append("".join(chr(rnd.randint(lo, hi)) for _ in range(rnd.randint(1, 6))))
            t = "".join(parts)
            ids = st.tokenize([t])["input_ids"][0].tolist()
            f.write(f"fz{i:04d}\t{base64.b64encode(t.encode('utf-8', 'surrogatepass')).decode()}\t{','.join(map(str, ids))}\n")
    print("→", OUT / "tokens_fuzz.tsv", a.n)


if __name__ == "__main__":
    main()
