# M5-4a 결과 · Modal Qwen3.5-4B judge_v1 vs Qwen3.5-2B (Mac · Android)

> ⚠️ dataset v1.1은 여러 실험에 이미 쓴 development benchmark다. 이 비교는 judge 후보(모델 크기 · 실행 위치)를 고르는 근거이며 일반화 성능 추정이 아니다. Modal 4B는 모델 크기(2B → 4B)와 함께 정밀도(Q4_K_M → BF16) · 엔진(Ollama/llama.cpp → vLLM GPU)도 바뀌었다. judge_v1 prompt · 요청 조건 · 선택 규칙은 같다.

- Modal: `Qwen/Qwen3.5-4B` revision `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a` · bf16 · vllm==0.21.0 · GPU L4 ($0.000222/s)
- vLLM: `--revision main --served-model-name judge --host 0.0.0.0 --port 8000 --dtype bfloat16 --max-model-len 2048 --seed 7 --generation-config vllm --limit-mm-per-prompt {"image": 0, "video": 0} --reasoning-parser qwen3 --max-num-seqs 32`
- 요청: temperature 0 · seed 7 · max_tokens 192 · json_schema · enable_thinking=false · prompt sha 553ab6176c0293f9 · run `modal-20261005-172733.jsonl`

## 1. 품질 (frozen 157쌍, label 2만 · e5 순서 · 최대 5개)

| metric | `e5 Top5` | `Mac 2B (Ollama Q4_K_M)` | `Android 2B (llama.cpp Q4_K_M)` | `Modal 4B (vLLM BF16)` |
| --- | --- | --- | --- | --- |
| Good@5 | 0.71 | 0.58 | 0.64 | 0.71 |
| nDCG@5 | 0.69 | 0.63 | 0.68 | 0.72 |
| Worth% | 0.39 | 0.39 | 0.42 | 0.39 |
| Bad% | 0.44 | 0.26 | 0.25 | 0.30 |
| Top1=0 | 0.41 | 0.38 | 0.25 | 0.29 |
| Quiet miss | 5.00 | 2.00 | 1.50 | 3.75 |
| 보여준 총 개수 | 85 | 66 | 64 | 80 |
| label 2 보여줌 (/47) | 33 | 27 | 30 | 33 |
| label 0 보여줌 | 37 | 17 | 16 | 24 |
| F7 0개 반환 | 0/4 | 1/4 | 1/4 | 0/4 |

Modal 4B 판정: ok 157/157 · thinking 출력 0건

## 2. failure case별 (보여준 수 / 전체)

| case | 목표 | `e5 Top5` | `Mac 2B (Ollama Q4_K_M)` | `Android 2B (llama.cpp Q4_K_M)` | `Modal 4B (vLLM BF16)` |
| --- | --- | --- | --- | --- | --- |
| repeat_low_value | 유입↓ | 21/27 | 7/27 | 6/27 | 15/27 |
| lexical_trap | 유입↓ | 16/28 | 9/28 | 10/28 | 9/28 |
| resolve_action | 회수↑ | 4/9 | 7/9 | 5/9 | 5/9 |
| worry_outcome | 회수↑ | 6/7 | 6/7 | 5/7 | 6/7 |
| implicit_link | 회수↑ | 2/7 | 2/7 | 3/7 | 2/7 |
| recurring | 회수↑ | 6/7 | 3/7 | 5/7 | 6/7 |
| change | 회수 유지 | 10/11 | 5/11 | 7/11 | 9/11 |
| reversal | 회수 유지 | 5/6 | 4/6 | 5/6 | 5/6 |

F7 · label 2가 없는 query의 노출 개수

| query | `e5 Top5` | `Mac 2B (Ollama Q4_K_M)` | `Android 2B (llama.cpp Q4_K_M)` | `Modal 4B (vLLM BF16)` |
| --- | --- | --- | --- | --- |
| q02 | 5 [0,1,0,0,0] | 3 [1,0,1] | 1 [1] | 5 [1,0,1,1,0] |
| q15 | 5 [0,0,0,1,1] | 3 [0,0,1] | 2 [0,1] | 4 [0,0,1,1] |
| q16 | 5 [0,1,0,0,0] | 2 [0,1] | 3 [0,0,1] | 4 [1,0,0,1] |
| q17 | 5 [0,0,0,1,0] | **0 ✓** | **0 ✓** | 2 [0,0] |

## 3. confusion matrix (정답 × 판정)

`Mac 2B (Ollama Q4_K_M)`

| 정답 \ 판정 | 2 | 1 | 0 | 실패 |
| --- | --- | --- | --- | --- |
| **2** | 31 | 5 | 11 | 0 |
| **1** | 23 | 1 | 9 | 0 |
| **0** | 18 | 8 | 51 | 0 |

`Android 2B (llama.cpp Q4_K_M)`

| 정답 \ 판정 | 2 | 1 | 0 | 실패 |
| --- | --- | --- | --- | --- |
| **2** | 36 | 4 | 7 | 0 |
| **1** | 20 | 3 | 10 | 0 |
| **0** | 18 | 4 | 55 | 0 |

`Modal 4B (vLLM BF16)`

| 정답 \ 판정 | 2 | 1 | 0 | 실패 |
| --- | --- | --- | --- | --- |
| **2** | 45 | 2 | 0 | 0 |
| **1** | 30 | 0 | 3 | 0 |
| **0** | 31 | 6 | 40 | 0 |

label 일치 (참고, 판단 기준은 1·2절의 제품 지표)

| 비교 | 일치 | κ |
| --- | --- | --- |
| Modal 4B (vLLM BF16) vs Mac 2B (Ollama Q4_K_M) | 98/157 | 0.33 |
| Modal 4B (vLLM BF16) vs Android 2B (llama.cpp Q4_K_M) | 104/157 | 0.39 |
| Mac 2B (Ollama Q4_K_M) vs Android 2B (llama.cpp Q4_K_M) | 107/157 | 0.44 |

## 4. latency

| 항목 | Android 2B (기기 CPU) | Modal 4B (L4, Mac에서 호출) |
| --- | --- | --- |
| 시작 | model load 2.6 s | 콜드 스타트 487.0 s (컨테이너 · 모델 로드 · vLLM 준비, 첫 실행은 다운로드 포함) |
| 1쌍 순차 median / p90 / max | 3.12 / 4.31 / 6.75 s | 2.40 / 2.82 / 12.59 s |
| 1쌍 구성 (Modal) | – | TTFT median 821 ms (네트워크 + 대기 + prompt) · 생성 median 1559 ms · 출력 평균 46 tok |
| 157쌍 순차 합계 | 511.7 s | 391.8 s |
| 저장 1회 = Top 30 판정 | 순차만 가능: 약 98 s (평균 × 30) | 7.7 s · 7.7 s · 6.9 s (30쌍 동시, 회차별) |
| peak 메모리 | 기기 RSS 2737 MB | 서버 GPU (기기 부담 없음) |
| 서버 내부 (vLLM metrics, 순차 평균) | – | e2e 1.80 s · prefill 0.178 s · decode 1.58 s |

## 5. 추론 비용 (Modal GPU 요금 기준 하한)

GPU L4 $0.000222/s ($0.7992/h). CPU · 메모리 요금과 컨테이너 대기(scaledown 120 s)는 별도라 실제 청구액은 이보다 크다.

| 항목 | 계산 | 비용 |
| --- | --- | --- |
| 1쌍 (순차, 서버 독점) | median 2.40 s × $0.000222 | $0.00053 |
| 저장 1회 = Top 30 (동시) | burst median 7.7 s × $0.000222 | $0.0017 |
| 저장 1,000회 | 위 × 1,000 (요청이 몰리지 않아 매번 GPU를 혼자 쓴다고 가정) | $1.70 |
| 콜드 스타트 1회 | 487.0 s × $0.000222 | $0.1081 |
| 이번 실행 전체 (GPU 점유 추정) | (910.4 s + scaledown 120 s) × $0.000222 | $0.229 |
| Android 2B | 서버 비용 없음 (기기 CPU · 배터리 · 1.27 GB 모델 저장 · RSS 2.7 GB) | $0 |
