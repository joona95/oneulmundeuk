#!/usr/bin/env python3
"""
Fixture for the app's QwenDeviceCheckTest (M6-7): the 157 frozen pairs in the exact order of the final PoC session
(runs/session-20261005-164627.jsonl — same current record runs consecutively, so the prompt cache behaves the same),
with the PoC's Android label and raw output for comparison. Reads only; writes out/qwen_device_fixture.tsv
(git-ignored): pair_id \t b64(current) \t b64(past) \t android_label \t b64(android_raw)

  python3 make_device_fixture.py
"""
import base64
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
SESSION = HERE / "runs" / "session-20261005-164627.jsonl"
DATASET = HERE.parent / "related" / "dataset.json"
OUT = HERE / "out" / "qwen_device_fixture.tsv"


def b64(s):
    return base64.b64encode(s.encode("utf-8")).decode("ascii")


def main():
    ds = json.loads(DATASET.read_text(encoding="utf-8"))
    current, past = {}, {}
    for q in ds["queries"]:
        for c in q["candidates"]:
            current[c["id"]] = q["text"]
            past[c["id"]] = c["text"]
    rows = []
    for line in SESSION.read_text(encoding="utf-8").splitlines():
        r = json.loads(line)
        if r.get("type") != "judgment":
            continue
        cid = r["cid"]
        label = "" if r.get("label") is None else str(r["label"])
        rows.append("\t".join([cid, b64(current[cid]), b64(past[cid]), label, b64(r.get("raw") or "")]))
    assert len(rows) == 157, len(rows)
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(OUT, len(rows))


if __name__ == "__main__":
    main()
