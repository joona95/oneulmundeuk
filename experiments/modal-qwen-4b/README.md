# M5-4a · Modal Qwen3.5-4B judge_v1 (frozen 157쌍)

**CLOSED (2026-10-05)** · 결과: `results-m5-4a-modal-4b.md` · raw: `runs/modal-20261005-172733.jsonl` · 쌍별: `runs/modal-4b-judge_v1-pairs.csv`

## 결과 요약

| metric | e5 Top5 | Mac 2B | Android 2B | **Modal 4B** |
| --- | --- | --- | --- | --- |
| Good@5 | .71 | .58 | .64 | .71 |
| nDCG@5 | .69 | .63 | .68 | .72 |
| Worth | .39 | .39 | .42 | .39 |
| Bad | .44 | .26 | .25 | .30 |
| Top1=0 | .41 | .38 | .25 | .29 |
| Quiet miss | 5.00 | 2.00 | 1.50 | 3.75 |
| shown / label 2 / label 0 | 85 / 33 / 37 | 66 / 27 / 17 | 64 / 30 / 16 | 80 / 33 / 24 |
| F7 zero return | 0/4 | 1/4 | 1/4 | 0/4 |
| repeat_low_value 유입 | 21/27 | 7/27 | 6/27 | 15/27 |

- 4B는 label 2를 훨씬 넓게 준다 (정답 2의 45/47을 2로 판정하지만 정답 0의 31/77, 정답 1의 30/33도 2). 결과가 e5 Top5에 가까워져 recall은 e5 수준이지만,
  noise(Bad .30 · label 0 24개 · 같은 말 반복 15/27)와 정답 없는 query의 침묵(F7 0/4 · Quiet miss 3.75)이 2B보다 나쁘다.
  이 제품이 우선하는 지표(Bad · Top1=0 · F7 · "왜 이걸 보여주지?" 회피)에서는 **4B가 2B보다 낫지 않다.**
- latency (L4, Mac에서 호출): 1쌍 median 2.40 s (Android 3.12 s) · Top 30을 30쌍 동시 요청하면 6.9–7.7 s (Android 순차 약 98 s) · 콜드 스타트 487 s(첫 실행, 모델 다운로드 포함).
- 비용 (L4 GPU 하한): 저장 1회(Top 30 동시) 약 $0.0017 · 1,000회 약 $1.70 · 이번 실행 전체 약 $0.23.
- revision `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a` · vLLM 0.21.0 · BF16. 모델 크기와 함께 정밀도 · 엔진도 바뀐 비교이며, dataset v1.1은 development benchmark다.

결론: server inference는 속도 면에서 유리하지만, 품질은 2B judge_v1보다 낫지 않았고 기록 원문을 기기 밖으로 보내야 한다.
**production 방향은 Android local inference(Qwen3.5-2B Q4_K_M)로 확정했다** (`docs/m5-related-decision.md`). 이 실험 코드는 서버 judge를 다시 검토할 때의 기준으로 보존한다.

목적: Qwen3.5-4B를 GPU 서버(Modal, vLLM)에서 기존 judge_v1으로 돌려, Android Qwen3.5-2B 결과와 **품질 · latency · 추론 비용**을 비교한다.
stateless inference API 후보를 고르기 위한 실험이다. production API와 Android 코드는 만들지 않는다. 상시 endpoint를 남기지 않는다(`modal run`이 끝나면 app 종료).

## 고정 / 변경

| | Mac 2B | Android 2B | **Modal 4B (이번)** |
| --- | --- | --- | --- |
| 모델 | Qwen3.5-2B Q4_K_M (Ollama) | 같은 GGUF | `Qwen/Qwen3.5-4B` 공식 BF16 (revision은 실행 시 기록) |
| 엔진 | Ollama (Metal) | llama.cpp b10456 CPU | vLLM 0.21.0 · GPU L4 (24 GB) |
| prompt | judge_v1 (`553ab6176c0293f9`) | 같음 | 같음 |
| 요청 | temperature 0 · seed 7 · 출력 192 · JSON schema · thinking off | 같음 | 같음 (`chat_template_kwargs.enable_thinking=false`) |
| context | 2048 | 2048 | `--max-model-len 2048` |
| 판정 순서 · 검증 · 선택 규칙 | – | 같음 | 같음 (`judge_client.py`가 `llm_judge` · `strong_judge.validate` 재사용) |

text만 쓴다 (`--limit-mm-per-prompt {"image":0,"video":0}`). `--reasoning-parser qwen3`라서 thinking이 새면 content가 아니라 reasoning으로 분리되고 리포트에 건수가 남는다.

## 실행 (Mac)

```bash
cd experiments/modal-qwen-4b
python3 -m pip install modal && modal setup      # 처음 한 번: Modal 계정 연결
modal run modal_judge.py                         # 콜드 스타트 → 157쌍 순차 1회 → 30쌍 동시 ×3
python3 modal_eval.py                            # 리포트 (Modal 호출 없음)
```

- 157쌍은 Mac/Android와 같은 순서로 **순차 1회**만 판정한다 (품질 지표는 이것만 쓴다).
- 30쌍 동시 요청 3회(`plan` 1–30, 31–60, 61–90)는 "저장 1회 = Top 30 판정"의 latency · 비용 측정용이다. 그 label은 품질에 쓰지 않는다.
- 예상 비용: L4 $0.000222/s(≈ $0.80/h). 첫 실행은 모델 다운로드(≈ 9 GB) · vLLM 준비가 콜드 스타트에 포함된다. 전체 15–25분 ≈ GPU $0.2–0.35 + CPU · 메모리. Starter 무료 크레딧($30/월) 안.
- 결과: `runs/modal-<시각>.jsonl`(raw) · `runs/llm-modal-qwen3.5-4b-bf16-judge_v1.jsonl`(llm_judge 형식) · `runs/modal-4b-judge_v1-pairs.csv` · `results-m5-4a-modal-4b.md`.

## 측정 정의

| 항목 | 방법 |
| --- | --- |
| 콜드 스타트 | `modal run` 직후 `/health` 200까지 (컨테이너 배정 · 모델 로드 · vLLM 준비) |
| 1쌍 latency | Mac에서 호출한 end-to-end (네트워크 포함, production API와 같은 조건). streaming으로 TTFT와 생성 시간을 나눔 |
| 서버 내부 시간 | vLLM `/metrics`의 e2e · prefill · decode 누적값 차이 |
| Top 30 | 30쌍 동시 요청의 wall time |
| 비용 | GPU 초당 가격 × 시간 (CPU · 메모리 · scaledown 대기 제외 → 하한). 실제 청구액은 Modal dashboard |

## 테스트

```bash
python3 -m unittest test_judge_client -v        # fake vLLM 서버 · 가짜 run (Modal 불필요)
```
