# M5-2e 결과 · Qwen 2B conservative reject filter

> ⚠️ **dataset v1.1은 여러 실험에 이미 쓴 development benchmark다.** 이 결과는 구조 확인용이며 최종 일반화 성능으로 해석하지 않는다. prompt는 결과를 보기 전에 고정했고, 1회만 실행했다.

- filter: `reject-qwen3.5-2b-q4_K_M-reject_filter_v1.jsonl` · model `qwen3.5:2b-q4_K_M` · prompt `reject_filter_v1.txt` (de90ab9be00c412a) · options `{"temperature": 0, "seed": 7, "num_ctx": 2048, "num_predict": 192, "think": false, "format": "json-schema", "keep_alive": "10m"}` · e5 Top10
- 판정 154쌍 · ok 154 · 실패/미완료 0
- e5 Top5 / judge_v1은 저장된 결과로 다시 계산 (새 호출 없음).

## 1. 전체 지표 (최대 5개 노출)

| metric | `e5 Top5` | `judge_v1` | `reject_filter_v1` |
| --- | --- | --- | --- |
| Good@5 | 0.71 | 0.58 | 0.71 |
| nDCG@5 | 0.69 | 0.63 | 0.69 |
| Worth% | 0.39 | 0.39 | 0.39 |
| Bad% | 0.44 | 0.26 | 0.44 |
| Top1=0 | 0.41 | 0.38 | 0.41 |
| Quiet miss | 5.00 | 2.00 | 5.00 |
| 보여준 총 개수 | 85 | 66 | 85 |
| label 2 보여줌 (/47) | 33 | 27 | 33 |
| label 0 보여줌 | 37 | 17 | 37 |
| F7 0개 반환 | 0/4 | 1/4 | 0/4 |

## 2. case별 (보여준 수 / 전체)

| case | 목표 | `e5 Top5` | `judge_v1` | `reject_filter_v1` |
| --- | --- | --- | --- | --- |
| repeat_low_value | 유입↓ | 21/27 | 7/27 | 21/27 |
| lexical_trap | 유입↓ | 16/28 | 9/28 | 16/28 |
| resolve_action | 회수↑ | 4/9 | 7/9 | 4/9 |
| worry_outcome | 회수↑ | 6/7 | 6/7 | 6/7 |
| implicit_link | 회수↑ | 2/7 | 2/7 | 2/7 |
| recurring | 회수↑ | 6/7 | 3/7 | 6/7 |
| change | 회수↑ | 10/11 | 5/11 | 10/11 |
| reversal | 회수↑ | 5/6 | 4/6 | 5/6 |

## 3. F7 · label 2가 없는 query

| query | 현재 기록 | `e5 Top5` | `judge_v1` | `reject_filter_v1` |
| --- | --- | --- | --- | --- |
| q02 | 오늘 진짜 너무 피곤하다. 아무것도 하기 싫음 | 5개 [0,1,0,0,0] | 3개 [1,0,1] | 5개 [0,1,0,0,0] |
| q15 | 오늘 처음으로 클라이밍 해봤다. 팔 터질 것 같음 ㅋㅋ | 5개 [0,0,0,1,1] | 3개 [0,0,1] | 5개 [0,0,0,1,1] |
| q16 | 점심에 국밥 먹었는데 진짜 맛있었다 | 5개 [0,1,0,0,0] | 2개 [0,1] | 5개 [0,1,0,0,0] |
| q17 | 비 와서 하루 종일 집에 있었다. 그냥 그런 하루 | 5개 [0,0,0,1,0] | **0개 ✓** | 5개 [0,0,0,1,0] |

## 4. filter 동작 · 정답 label별 keep / reject


e5 Top10 전체 (154쌍)

| 정답 label | 전체 | keep | reject | 실패 | reject 비율 |
| --- | --- | --- | --- | --- | --- |
| **2** | 47 | 47 | 0 | 0 | 0.00 |
| **1** | 33 | 33 | 0 | 0 | 0.00 |
| **0** | 74 | 72 | 2 | 0 | 0.03 |

그중 e5 Top5 (85쌍)

| 정답 label | 전체 | keep | reject | 실패 | reject 비율 |
| --- | --- | --- | --- | --- | --- |
| **2** | 33 | 33 | 0 | 0 | 0.00 |
| **1** | 15 | 15 | 0 | 0 | 0.00 |
| **0** | 37 | 37 | 0 | 0 | 0.00 |

case별 reject (e5 Top10)

| case | 정답 | 전체 | reject |
| --- | --- | --- | --- |
| repeat_low_value | 0 | 27 | 0 |
| lexical_trap | 0 | 27 | 0 |
| resolve_action | 2 | 9 | 0 |
| worry_outcome | 2 | 7 | 0 |
| implicit_link | 2 | 7 | 0 |
| recurring | 2 | 7 | 0 |
| change | 2 | 11 | 0 |
| reversal | 2 | 6 | 0 |
| ambiguous | 1 | 33 | 0 |
| unrelated | 0 | 20 | 2 |

### reject된 label 2 (놓친 좋은 기록)

- 없음

## 5. 출력 진단 (점수에 쓰지 않음)

- keep 비율: 152/154
- keep 외 key를 함께 출력한 응답: 0
- prompt_eval_count: 최소 378 · 최대 410 (num_ctx 2048)
- 첫 호출 이후 모델 재로드: 0회
