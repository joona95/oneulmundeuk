# M5-2d 결과 · strong-model feasibility probe (e5 Top5 vs Qwen 2B judge_v1 vs Claude Code judge_v1)

> ⚠️ **이 결과는 feasibility probe이며, 공정한 model benchmark가 아니다.** Claude 계열 모델이 이 benchmark(dataset v1.1의 문장 · label · note)의 설계에 참여했으므로 같은 계열의 판단 습관이 점수를 부풀릴 수 있다(contamination). 또 Claude Code session에서의 판정은 temperature · 출력 길이 · 판정 간 독립성 등을 API 실행처럼 통제할 수 없다. dataset v1.1은 이미 여러 실험에 쓴 development benchmark이기도 하다. **이 결과는 "더 강한 모델에서 질적으로 큰 개선이 보이는가"를 확인하는 데만 쓰고, 수치 차이를 모델 성능이나 production 판단 근거로 해석하지 않는다.**

- `qwen2b_judge_v1`: `llm-qwen3.5-2b-q4_K_M-judge_v1.jsonl` · model `qwen3.5:2b-q4_K_M` · prompt `judge_v1.txt` (553ab6176c0293f9) · options `{"temperature": 0, "seed": 7, "num_ctx": 2048, "num_predict": 192, "think": false, "format": "json-schema", "keep_alive": "10m"}` · 1회차 ok 157/157
- `claude_code_probe`: `llm-claude-code-feasibility-judge_v1.jsonl` · model `claude-code-session (feasibility probe, 통제되지 않은 실행)` · prompt `judge_v1.txt` (553ab6176c0293f9) · options `{"mode": "blind manual judging in a fresh Claude Code session", "attempts": 1}` · 1회차 ok 157/157
- 입력: 현재 기록 text + 과거 기록 text만. dataset v1.1 · e5-small-ko.v1.1 순서 · 선택/평가 로직은 judge_v1과 동일.

## 1. 전체 지표

| metric | `e5 Top5` | `qwen2b_judge_v1 all` | `claude_code_probe all` | `qwen2b_judge_v1 Top10` | `claude_code_probe Top10` | `qwen2b_judge_v1 Top7` | `claude_code_probe Top7` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Good@5 (2 회수율, query 평균) | 0.71 | 0.58 | 0.90 | 0.58 | 0.90 | 0.47 | 0.77 |
| nDCG@5 (gain 2→3, 1→1) | 0.69 | 0.63 | 0.88 | 0.63 | 0.88 | 0.55 | 0.80 |
| Worth% (보여준 것 중 2 비율) | 0.39 | 0.39 | 0.52 | 0.39 | 0.52 | 0.39 | 0.53 |
| Bad% (보여준 것 중 0 비율, 전체) | 0.44 | 0.26 | 0.07 | 0.26 | 0.07 | 0.29 | 0.08 |
| Top1=0 (1위에 0을 올린 query 비율) | 0.41 | 0.38 | 0.12 | 0.38 | 0.12 | 0.38 | 0.12 |
| Quiet miss (2 없는 query에서 보여준 개수 평균) | 5.00 | 2.00 | 1.50 | 2.00 | 1.50 | 2.00 | 1.50 |
| 보여준 총 개수 | 85 | 66 | 68 | 66 | 68 | 58 | 59 |

## 2. 실패 유형별 (Top5 결과 기준: 유입 = 들어온 수/전체, 회수 = 들어온 label 2/전체)

| | 유형 | case | 목표 | `e5 Top5` | `qwen2b_judge_v1 all` | `claude_code_probe all` | `qwen2b_judge_v1 Top10` | `claude_code_probe Top10` | `qwen2b_judge_v1 Top7` | `claude_code_probe Top7` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F1 | 같은 상태·사건 반복 | repeat_low_value | 유입↓ | 21/27 | 7/27 | 3/27 | 7/27 | 3/27 | 7/27 | 3/27 |
| F2 | 주제·단어만 공유 | lexical_trap | 유입↓ | 16/28 | 9/28 | 2/28 | 9/28 | 2/28 | 9/28 | 2/28 |
| F3 | 다짐 → 행동 | resolve_action | 회수↑ | 4/9 | 7/9 | 7/9 | 7/9 | 7/9 | 4/9 | 6/9 |
| F4 | 걱정 → 결과 | worry_outcome | 회수↑ | 6/7 | 6/7 | 7/7 | 6/7 | 7/7 | 5/7 | 6/7 |
| F5 | 단어 없는 연결 | implicit_link | 회수↑ | 2/7 | 2/7 | 5/7 | 2/7 | 5/7 | 1/7 | 3/7 |
| F6 | 반복 패턴 | recurring | 회수↑ | 6/7 | 3/7 | 7/7 | 3/7 | 7/7 | 3/7 | 6/7 |
| – | 유지 확인 | change | 회수↑ | 10/11 | 5/11 | 11/11 | 5/11 | 11/11 | 5/11 | 10/11 |
| – | 유지 확인 | reversal | 회수↑ | 5/6 | 4/6 | 5/6 | 4/6 | 5/6 | 4/6 | 5/6 |
| F7 | 보여줄 게 없음 | 4 queries | 0개 반환 | 0/4 | 1/4 | 0/4 | 1/4 | 0/4 | 1/4 | 0/4 |

정답 0개 query별 반환 개수 (all)

| query | 현재 기록 | `qwen2b_judge_v1 all` | `claude_code_probe all` |
| --- | --- | --- | --- |
| q02 | 오늘 진짜 너무 피곤하다. 아무것도 하기 싫음 | 3개 (f, d, c) | 2개 (f, c) |
| q15 | 오늘 처음으로 클라이밍 해봤다. 팔 터질 것 같음 ㅋㅋ | 3개 (b, c, e) | 2개 (a, e) |
| q16 | 점심에 국밥 먹었는데 진짜 맛있었다 | 2개 (c, e) | 1개 (a) |
| q17 | 비 와서 하루 종일 집에 있었다. 그냥 그런 하루 | **0개 ✓** | 1개 (a) |

## 3. pair-level confusion (1회차, 157쌍)


`qwen2b_judge_v1`

| 정답 \ 판정 | 2 | 1 | 0 | 실패 |
| --- | --- | --- | --- | --- |
| **2** | 31 | 5 | 11 | 0 |
| **1** | 23 | 1 | 9 | 0 |
| **0** | 18 | 8 | 51 | 0 |

`claude_code_probe`

| 정답 \ 판정 | 2 | 1 | 0 | 실패 |
| --- | --- | --- | --- | --- |
| **2** | 47 | 0 | 0 | 0 |
| **1** | 23 | 9 | 1 | 0 |
| **0** | 7 | 16 | 54 | 0 |

`qwen2b_judge_v1` → `claude_code_probe` 판정이 바뀐 pair

| 정답 | qwen2b_judge_v1 → claude_code_probe | 건수 |
| --- | --- | --- |
| 2 | 0 → 2 | 11 |
| 2 | 1 → 2 | 5 |
| 1 | 0 → 1 | 5 |
| 1 | 2 → 1 | 4 |
| 1 | 0 → 2 | 4 |
| 1 | 1 → 2 | 1 |
| 1 | 2 → 0 | 1 |
| 0 | 2 → 0 | 9 |
| 0 | 0 → 1 | 8 |
| 0 | 2 → 1 | 7 |
| 0 | 1 → 0 | 6 |
| 0 | 0 → 2 | 4 |
| 0 | 1 → 2 | 1 |

case별 label 2 판정 수 (`qwen2b_judge_v1` → `claude_code_probe`)

| case | 정답 | 전체 | `qwen2b_judge_v1` 2 | `claude_code_probe` 2 |
| --- | --- | --- | --- | --- |
| change | 2 | 11 | 6 | 11 |
| worry_outcome | 2 | 7 | 6 | 7 |
| resolve_action | 2 | 9 | 8 | 9 |
| recurring | 2 | 7 | 3 | 7 |
| reversal | 2 | 6 | 4 | 6 |
| implicit_link | 2 | 7 | 4 | 7 |
| ambiguous | 1 | 33 | 23 | 23 |
| repeat_low_value | 0 | 27 | 7 | 5 |
| lexical_trap | 0 | 28 | 10 | 2 |
| unrelated | 0 | 22 | 1 | 0 |
