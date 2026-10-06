#!/usr/bin/env python3
"""
Device check fixture (read by app/src/androidTest/.../E5DeviceCheckTest.kt, pushed with adb, never packaged):
  out/device_fixture.tsv
    item <id> <base64 text> <comma ids> <comma floats(384)>      ← every out/reference.json item (host reference)
    pair <name> <b64 anchor> <b64 similar> <b64 unrelated> <host cos(anchor,similar)> <host cos(anchor,unrelated)>

  python3 make_device_fixture.py --model-dir <snapshot dir>      (after make_reference.py)
"""
import argparse
import base64
import json
import numpy as np
from common import OUT, load_st

PAIRS = [
    ("meeting", "query: 오늘 회의가 너무 길어서 완전히 지쳤다", "query: 회의가 길게 이어져서 피곤한 하루였다", "query: 주말에 고양이 사료를 새로 주문했다"),
    ("career", "query: 이 회사에서 내가 성장하고 있는지 모르겠다", "query: 요즘 일하면서 발전이 없는 것 같아 불안하다", "query: 비 오는 날 우산을 잃어버렸다"),
    ("search", "query: 사이드 프로젝트가 재미있었던 때", "passage: 퇴근 후에 만든 앱을 처음 배포했는데 너무 즐거웠다", "passage: 치과 예약을 다음 주로 미뤘다"),
    ("mixed", "query: React Native 설정 때문에 하루 종일 삽질함", "query: TypeScript 빌드 에러 잡느라 시간을 다 썼다", "query: 엄마랑 통화하면서 오랜만에 웃었다"),
]


def b64(s):
    return base64.b64encode(s.encode("utf-8")).decode()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model-dir", required=True)
    a = ap.parse_args()
    ref = json.loads((OUT / "reference.json").read_text(encoding="utf-8"))
    st = load_st(a.model_dir)
    lines = [f"item\t{it['id']}\t{b64(it['text'])}\t{','.join(map(str, it['ids']))}\t{','.join(repr(float(v)) for v in it['emb'])}"
             for it in ref["items"]]
    for name, x, y, z in PAIRS:
        e = st.encode([x, y, z], normalize_embeddings=True, convert_to_numpy=True).astype(np.float32)
        lines.append(f"pair\t{name}\t{b64(x)}\t{b64(y)}\t{b64(z)}\t{float(e[0] @ e[1])!r}\t{float(e[0] @ e[2])!r}")
    (OUT / "device_fixture.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("→", OUT / "device_fixture.tsv", len(ref["items"]), "items,", len(PAIRS), "pairs")


if __name__ == "__main__":
    main()
