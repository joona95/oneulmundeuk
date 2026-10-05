#!/usr/bin/env python3
"""
오늘문득 M5-1: 본문 embedding + cosine similarity baseline.

  python3 embed.py --model e5-small-ko     # → runs/e5-small-ko.json
  python3 embed.py --model kure            # → runs/kure.json

규칙 (M5-1 범위):
  - text만 사용. category / emotion / date / heuristic / reranker / threshold 없음.
  - 모든 기록이 같은 종류(개인 메모)라 질문/문서를 구분하지 않고 양쪽을 같은 방식으로 encode한다
    (각 모델 카드가 권하는 "symmetric similarity" 방식).
  - 벡터는 L2 정규화, float32 → cosine = 내적.
  - query마다 후보 "전부"의 점수와 순위를 저장한다 (eval.py는 앞 5개만 평가).
결과 파일에는 실제로 받은 모델 revision(commit hash)을 기록한다.
"""
import argparse
import json
import platform
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent

MODELS = {
    # ① 작은 모바일 후보: multilingual-e5-small(118M)을 한국어로 fine-tune. 대칭 유사도는 양쪽 모두 "query: ".
    "e5-small-ko": {"id": "dragonkue/multilingual-e5-small-ko-v2", "prefix": "query: "},
    # ③ 상한 비교용: bge-m3(568M) 기반 한국어 retrieval 모델. 접두어 없음.
    "kure": {"id": "nlpai-lab/KURE-v1", "prefix": ""},
}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", choices=sorted(MODELS), required=True)
    ap.add_argument("--dataset", default=HERE / "dataset.json")
    ap.add_argument("--out", default=None, help="기본: runs/<model>.json")
    ap.add_argument("--batch-size", type=int, default=16)
    a = ap.parse_args()

    import numpy as np
    from huggingface_hub import snapshot_download
    from sentence_transformers import SentenceTransformer

    spec = MODELS[a.model]
    path = Path(snapshot_download(spec["id"]))  # .../snapshots/<commit hash>
    model = SentenceTransformer(str(path), device="cpu")

    ds = json.loads(Path(a.dataset).read_text(encoding="utf-8"))
    texts = []
    for q in ds["queries"]:
        texts.append(q["text"])
        texts.extend(c["text"] for c in q["candidates"])
    unique = sorted(set(texts))

    t0 = time.time()
    vecs = model.encode(
        [spec["prefix"] + t for t in unique],
        batch_size=a.batch_size,
        normalize_embeddings=True,
        convert_to_numpy=True,
        show_progress_bar=False,
    ).astype(np.float32)
    seconds = time.time() - t0
    vec = dict(zip(unique, vecs))

    rankings = {}
    for q in ds["queries"]:
        qv = vec[q["text"]]
        scored = [{"id": c["id"], "score": round(float(np.dot(qv, vec[c["text"]])), 4)} for c in q["candidates"]]
        scored.sort(key=lambda x: (-x["score"], x["id"]))  # 동점이면 id 순 (결정적)
        rankings[q["id"]] = scored

    out = Path(a.out) if a.out else HERE / "runs" / f"{a.model}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({
        "system": f"{a.model} · text only · cosine",
        "model": spec["id"],
        "revision": path.name,
        "prefix": spec["prefix"],
        "dim": int(vecs.shape[1]),
        "texts": len(unique),
        "encode_seconds": round(seconds, 2),
        "env": {"python": platform.python_version(), "machine": platform.machine()},
        "rankings": rankings,
    }, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{a.model}: {len(unique)} texts, dim {vecs.shape[1]}, {seconds:.1f}s → {out}")


if __name__ == "__main__":
    main()
