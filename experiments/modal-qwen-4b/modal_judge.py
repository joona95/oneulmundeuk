"""
M5-4a: Modal GPU(L4)에서 vLLM으로 Qwen/Qwen3.5-4B(공식 BF16)를 띄우고, frozen 157쌍을 judge_v1으로 판정한다.
production API가 아니다. `modal run`이 끝나면 app도 내려간다 (상시 endpoint 없음).

  pip install modal && modal setup            # 처음 한 번 (Modal 계정 · 토큰)
  modal run modal_judge.py                    # 콜드 스타트 → 157쌍 순차 → 30쌍 동시 ×3 → runs/modal-<시각>.jsonl
  python3 modal_eval.py                       # 리포트 (Modal 호출 없음)

judge 조건은 Mac/Android judge_v1과 같다 (judge_client.py). 바뀌는 것: 모델 크기(2B → 4B) · 정밀도(Q4_K_M → BF16) · 엔진(vLLM, GPU).
"""
import datetime as dt
import json
import subprocess
import sys
import time
from pathlib import Path

import modal

MINUTES = 60
MODEL_NAME = "Qwen/Qwen3.5-4B"
MODEL_REVISION = "main"          # 실행 시 실제 commit sha를 기록한다 (resolved_revision)
VLLM_VERSION = "0.21.0"          # Modal 공식 vLLM 예제와 같은 pin
GPU = "L4"
PORT = 8000
GPU_PRICE_PER_S = {"L4": 0.000222}  # modal.com/pricing (2026-10)
VLLM_ARGS = [
    "--revision", MODEL_REVISION,
    "--served-model-name", "judge",
    "--host", "0.0.0.0", "--port", str(PORT),
    "--dtype", "bfloat16",
    "--max-model-len", "2048",                       # Mac num_ctx · Android -c와 같음
    "--seed", "7",
    "--generation-config", "vllm",                   # HF generation_config의 sampling 기본값을 쓰지 않음 (요청값만)
    "--limit-mm-per-prompt", json.dumps({"image": 0, "video": 0}),  # text만
    "--reasoning-parser", "qwen3",                   # thinking이 새면 content가 아니라 reasoning으로 분리돼 기록됨
    "--max-num-seqs", "32",
]

app = modal.App("oneul-m5-4a-qwen35-4b")
image = (
    modal.Image.from_registry("nvidia/cuda:12.9.0-devel-ubuntu22.04", add_python="3.12")
    .entrypoint([])
    .uv_pip_install(f"vllm=={VLLM_VERSION}")
    .env({"HF_XET_HIGH_PERFORMANCE": "1"})
)
hf_cache = modal.Volume.from_name("huggingface-cache", create_if_missing=True)
vllm_cache = modal.Volume.from_name("vllm-cache", create_if_missing=True)


@app.server(
    image=image,
    gpu=GPU,
    scaledown_window=2 * MINUTES,
    startup_timeout=30 * MINUTES,
    volumes={"/root/.cache/huggingface": hf_cache, "/root/.cache/vllm": vllm_cache},
    port=PORT,
    target_concurrency=32,
    unauthenticated=True,  # `modal run` 동안만 존재하는 임시 URL
)
class Server:
    @modal.enter()
    def start(self):
        self.proc = subprocess.Popen(["vllm", "serve", MODEL_NAME, *VLLM_ARGS])

    @modal.exit()
    def stop(self):
        self.proc.terminate()


@app.function(volumes={"/root/.cache/huggingface": hf_cache})
def resolved_revision():
    ref = Path("/root/.cache/huggingface/hub/models--Qwen--Qwen3.5-4B/refs/main")
    return ref.read_text().strip() if ref.exists() else None


@app.local_entrypoint()
def main(bursts: int = 3, burst_size: int = 30, skip_seq: bool = False):
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import judge_client as C

    system, user_tpl, sha = C.load_prompt()
    ds, plan = C.load_plan()
    out_dir = Path(__file__).resolve().parent / "runs"
    out_dir.mkdir(exist_ok=True)
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    out = out_dir / f"modal-{stamp}.jsonl"

    def write(rec):
        with out.open("a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

    url = Server.get_url()
    t_launch = time.time()
    write({"type": "meta", "model": MODEL_NAME, "model_revision": MODEL_REVISION, "precision": "bf16", "engine": f"vllm=={VLLM_VERSION}",
           "gpu": GPU, "gpu_price_per_s": GPU_PRICE_PER_S[GPU], "vllm_args": VLLM_ARGS, "prompt_sha": sha,
           "request_example": {k: v for k, v in C.request_body(system, user_tpl, "{current}", "{past}").items() if k != "messages"},
           "created": stamp})
    print(f"server {url} — 콜드 스타트 대기 (첫 실행은 모델 다운로드 포함)", flush=True)
    ready = C.wait_ready(url, 30 * MINUTES)
    if ready is None:
        raise SystemExit("✗ 30분 안에 서버가 준비되지 않음")
    write({"type": "cold_start", "ready_s": round(ready, 1)})
    print(f"ready {ready:.1f}s", flush=True)

    if not skip_seq:
        m0, t0 = C.metrics_snapshot(url), time.time()
        for i, (q, c, rank) in enumerate(plan, 1):  # 157쌍 순차 1회 (품질 평가용, Mac/Android와 같은 순서)
            rec = C.judge(url, system, user_tpl, q["text"], c["text"])
            rec.update(type="judgment", task="seq", idx=i, qid=q["id"], cid=c["id"], e5_rank=rank)
            write(rec)
            shown = f"label {rec['label']}" if rec["status"] == "ok" else f"ERROR {rec['error']}"
            print(f"[seq] {i}/{len(plan)} {c['id']} {rec['wall_ms']}ms (ttft {rec.get('ttft_ms')} · gen {rec.get('gen_ms')}) {shown}", flush=True)
        write({"type": "seq_done", "wall_s": round(time.time() - t0, 1), "server_metrics": C.diff(C.metrics_snapshot(url), m0)})

    for b in range(bursts):  # 저장 1회 = Top30 판정을 동시에 보내는 경우 (latency 측정용, 품질 평가에 쓰지 않음)
        pairs = [(c["id"], q["text"], c["text"]) for q, c, _ in plan[b * burst_size:(b + 1) * burst_size]]
        m0 = C.metrics_snapshot(url)
        wall, recs = C.burst(url, system, user_tpl, pairs, workers=burst_size)
        write({"type": "burst", "idx": b + 1, "size": len(pairs), "wall_s": round(wall, 2),
               "ok": sum(r["status"] == "ok" for r in recs), "pair_wall_ms": [r["wall_ms"] for r in recs],
               "labels": {cid: r["label"] for (cid, _, _), r in zip(pairs, recs)}, "server_metrics": C.diff(C.metrics_snapshot(url), m0)})
        print(f"[burst] {b + 1}/{bursts} {len(pairs)}쌍 동시 · {wall:.1f}s", flush=True)

    end = time.time()
    rev = resolved_revision.remote()
    write({"type": "end", "client_wall_s": round(end - t_launch, 1), "scaledown_s": 2 * MINUTES, "resolved_revision": rev})
    print(f"\n기록: {out}\nmodel revision {rev} · 다음: python3 modal_eval.py")
