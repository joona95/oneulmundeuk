# Android judge_v1 · frozen M5-0.1 benchmark 157쌍 (Mac/Ollama vs Android/llama.cpp)

> ⚠️ dataset v1.1은 여러 실험에 이미 쓴 development benchmark다. 아래는 runtime(Mac/Ollama → Android/llama.cpp)이 바뀌었을 때 제품 지표가 유지되는지 보는 비교이며, 일반화 성능 추정이 아니다. 모델 파일 · judge_v1 prompt · 선택 규칙은 Mac 실행과 같다.

- 기기: SM-S948N · SoC SM8850 · Android 16 · MemTotal: 11389624 kB MemAvailable: 3061172 kB
- runtime: llama.cpp ref: b10456 (f275595) · march: armv8.6-a+dotprod+i8mm · CPU only
- 모델 파일: `/data/local/tmp/qwen-poc/models/qwen3.5-2b-q4_K_M.gguf` (1274396992 bytes, Ollama blob 그대로)
- llama-server: `-m /data/local/tmp/qwen-poc/models/qwen3.5-2b-q4_K_M.gguf -c 2048 -n 192 --temp 0 --seed 7 -np 1 --jinja --host 127.0.0.1 --port 8089` · 요청: temperature 0 · seed 7 · max_tokens 192 · json_schema · enable_thinking=false
- prompt `judge_v1.txt` sha 553ab6176c0293f9 · session `session-20261005-164627.jsonl`

## 1. 실행 · 성능 (157쌍 연속, 모델 reload 없음)

| 항목 | 값 |
| --- | --- |
| 판정 완료 | ok 157/157 · 실패 0 |
| crash / OOM | 없음 |
| model load | 2.6s |
| 전체 실행 시간 (load 제외 / 포함) | 511.7s / 514.3s |
| 1쌍 latency | median 3.12s · mean 3.26s · p90 4.31s · max 6.75s |
| prompt processing | 평균 698ms/쌍 · 평균 59 tok 처리 (prefix cache 제외분) · 85.1 tok/s |
| generation | 평균 2527ms/쌍 · 평균 52 tok · 20.6 tok/s |
| peak RSS (VmHWM) | 2737 MB |
| thinking 출력 | 0건 |

## 2. 제품 지표 (label 2만, e5 순서, 최대 5개)

| metric | `e5 Top5` | `Mac judge_v1` | `Android judge_v1` |
| --- | --- | --- | --- |
| Good@5 | 0.71 | 0.58 | 0.64 |
| nDCG@5 | 0.69 | 0.63 | 0.68 |
| Worth% | 0.39 | 0.39 | 0.42 |
| Bad% | 0.44 | 0.26 | 0.25 |
| Top1=0 | 0.41 | 0.38 | 0.25 |
| Quiet miss | 5.00 | 2.00 | 1.50 |
| 보여준 총 개수 | 85 | 66 | 64 |
| label 2 보여줌 (/47) | 33 | 27 | 30 |
| label 0 보여줌 | 37 | 17 | 16 |
| F7 0개 반환 | 0/4 | 1/4 | 1/4 |

## 3. failure case별 (보여준 수 / 전체)

| case | 목표 | `e5 Top5` | `Mac judge_v1` | `Android judge_v1` |
| --- | --- | --- | --- | --- |
| repeat_low_value | 유입↓ | 21/27 | 7/27 | 6/27 |
| lexical_trap | 유입↓ | 16/28 | 9/28 | 10/28 |
| resolve_action | 회수↑ | 4/9 | 7/9 | 5/9 |
| worry_outcome | 회수↑ | 6/7 | 6/7 | 5/7 |
| implicit_link | 회수↑ | 2/7 | 2/7 | 3/7 |
| recurring | 회수↑ | 6/7 | 3/7 | 5/7 |
| change | 회수 유지 | 10/11 | 5/11 | 7/11 |
| reversal | 회수 유지 | 5/6 | 4/6 | 5/6 |

F7 · label 2가 없는 query의 노출 개수

| query | `e5 Top5` | `Mac judge_v1` | `Android judge_v1` |
| --- | --- | --- | --- |
| q02 | 5 [0,1,0,0,0] | 3 [1,0,1] | 1 [1] |
| q15 | 5 [0,0,0,1,1] | 3 [0,0,1] | 2 [0,1] |
| q16 | 5 [0,1,0,0,0] | 2 [0,1] | 3 [0,0,1] |
| q17 | 5 [0,0,0,1,0] | **0 ✓** | **0 ✓** |

## 4. confusion matrix (정답 × 판정, 157쌍)

`Mac judge_v1`

| 정답 \ 판정 | 2 | 1 | 0 | 실패 |
| --- | --- | --- | --- | --- |
| **2** | 31 | 5 | 11 | 0 |
| **1** | 23 | 1 | 9 | 0 |
| **0** | 18 | 8 | 51 | 0 |

`Android judge_v1`

| 정답 \ 판정 | 2 | 1 | 0 | 실패 |
| --- | --- | --- | --- | --- |
| **2** | 36 | 4 | 7 | 0 |
| **1** | 20 | 3 | 10 | 0 |
| **0** | 18 | 4 | 55 | 0 |

## 5. Mac vs Android label 일치 (157쌍)

- 일치 107/157 (68%) · Cohen's κ 0.44
- label 2 판정 일치 (둘 중 하나라도 2인 93쌍 중 둘 다 2): 53

| Mac \ Android | 2 | 1 | 0 | 실패 |
| --- | --- | --- | --- | --- |
| **2** | 53 | 6 | 13 | 0 |
| **1** | 5 | 2 | 7 | 0 |
| **0** | 16 | 3 | 52 | 0 |

정답 label · case별 일치

| 구분 | 쌍 | 일치 |
| --- | --- | --- |
| 정답 2 | 47 | 27 |
| 정답 1 | 33 | 24 |
| 정답 0 | 77 | 56 |
| repeat_low_value | 27 | 21 |
| lexical_trap | 28 | 16 |
| resolve_action | 9 | 7 |
| worry_outcome | 7 | 5 |
| implicit_link | 7 | 2 |
| recurring | 7 | 4 |
| change | 11 | 6 |
| reversal | 6 | 3 |
| ambiguous | 33 | 24 |
| unrelated | 22 | 19 |

## 6. Mac judge_v1 vs Android judge_v1

| | Mac/Ollama | Android/llama.cpp | 변화 |
| --- | --- | --- | --- |
| Good@5 | 0.58 | 0.64 | +0.06 |
| nDCG@5 | 0.63 | 0.68 | +0.05 |
| Worth% | 0.39 | 0.42 | +0.04 |
| Bad% | 0.26 | 0.25 | -0.01 |
| Top1=0 | 0.38 | 0.25 | -0.12 |
| Quiet miss | 2.00 | 1.50 | -0.50 |
| 보여준 총 개수 | 66 | 64 | -2 |
| label 2 보여줌 | 27 | 30 | +3 |
| label 0 보여줌 | 17 | 16 | -1 |
| F7 0개 반환 | 1 | 1 | +0 |
| repeat_low_value | 7/27 | 6/27 | -1 |
| lexical_trap | 9/28 | 10/28 | +1 |
| resolve_action | 7/9 | 5/9 | -2 |
| worry_outcome | 6/7 | 5/7 | -1 |
| implicit_link | 2/7 | 3/7 | +1 |
| recurring | 3/7 | 5/7 | +2 |
| change | 5/11 | 7/11 | +2 |
| reversal | 4/6 | 5/6 | +1 |
| label 일치 | – | 107/157 | – |
| 1쌍 latency (median) | Mac 측정은 별도 | 3.12s | – |
| peak RSS | – | 2737 MB | – |

(e5 Top5: Good@5 0.71 · Bad% 0.44 · label 2 33 · label 0 37 · F7 0/4)
