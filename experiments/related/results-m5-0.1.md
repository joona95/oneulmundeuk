# M5-0.1 결과 · 날짜 편향 보정 후 재집계 (dataset 1.1)

평가셋 보정 단계다. 성능 개선이 아니다. 기존 15 queries / 131 pairs는 그대로 두고 반례만 추가했다 (`added_in: "0.1"`).

## 1. 전체 분포

- queries 17 (기존 15 + 신규 2) · pairs 157 (기존 131 + 신규 26) · query당 후보 8~11개
- label 2: 47 (30%) · 1: 33 (21%) · 0: 77 (49%)
- label 2가 없는 query (정답 = 0개): q02, q15, q16, q17
- 신규 항목: q02-i(0·repeat_low_value·191일), q04-k(0·repeat_low_value·98일), q05-j(2·worry_outcome·8일), q06-i(2·reversal·15일), q08-k(0·repeat_low_value·191일), q09-i(2·resolve_action·22일), q11-i(0·repeat_low_value·113일), q12-j(0·repeat_low_value·253일), q12-k(2·resolve_action·13일), q14-i(2·worry_outcome·6일), q16-a(1·ambiguous·14일), q16-b(0·repeat_low_value·203일), q16-c(0·repeat_low_value·286일), q16-d(0·lexical_trap·86일), q16-e(1·ambiguous·29일), q16-f(0·lexical_trap·228일), q16-g(0·unrelated·4일), q16-h(0·unrelated·52일), q17-a(0·repeat_low_value·78일), q17-b(0·repeat_low_value·161일), q17-c(0·repeat_low_value·2일), q17-d(0·lexical_trap·22일), q17-e(0·lexical_trap·93일), q17-f(1·ambiguous·133일), q17-g(0·unrelated·25일), q17-h(0·unrelated·46일)

## 2. label별 날짜 간격 (현재 기록 − 과거 기록, 일)

| 그룹 | 분포 | ≤7일 | 8–14일 | 15–30일 | 31–90일 | 91–180일 | >180일 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| label 2 | n=47 · min 6 · 25% 143 · 중앙 214 · 75% 294 · max 561 | 1 | 3 | 2 | 2 | 10 | 29 |
| label 1 | n=33 · min 3 · 25% 37 · 중앙 85 · 75% 133 · max 264 | 2 | 2 | 3 | 11 | 12 | 3 |
| label 0 | n=77 · min 1 · 25% 5 · 중앙 18 · 75% 67 · max 286 | 25 | 11 | 11 | 17 | 7 | 6 |
| — repeat_low_value | n=27 · min 1 · 25% 4 · 중앙 8 · 75% 113 · max 286 | 13 | 3 | 1 | 2 | 3 | 5 |
| — lexical_trap | n=28 · min 3 · 25% 17 · 중앙 37 · 75% 76 · max 228 | 3 | 4 | 7 | 9 | 4 | 1 |
| — unrelated | n=22 · min 1 · 25% 5 · 중앙 13 · 75% 37 · max 67 | 9 | 4 | 3 | 6 | 0 | 0 |

## 3. repeat_low_value 날짜 간격 (v1 → v1.1)

| | 분포 | 14일 미만 | 14일 이상 |
| --- | --- | --- | --- |
| v1 | n=17 · min 1 · 25% 2 · 중앙 5 · 75% 8 · max 74 | 15 | 2 |
| v1.1 | n=27 · min 1 · 25% 4 · 중앙 8 · 75% 113 · max 286 | 16 | 11 |

label 2 중 14일 미만 (gate가 지우는 좋은 연결): v1 1/42 → v1.1 4/47

## 4–5. e5-small-ko 재평가 · 14일 gate 비교

> **`14일 gate`는 production 전략이 아니라 dataset bias 확인용 baseline이다.** e5 순위에서 14일 미만 기록을 지운 뒤 Top5. gate가 좋아 보일수록 평가셋이 날짜로 풀린다는 뜻이다.

run: `dragonkue/multilingual-e5-small-ko-v2` @ `fcfc26bf3558` · text only · cosine

| metric | `e5-small-ko` | `e5-small-ko + 14일 gate` |
| --- | --- | --- |
| Good@5 (2 회수율, query 평균) | 0.71 | 0.75 |
| nDCG@5 (gain 2→3, 1→1) | 0.69 | 0.75 |
| Worth% (보여준 것 중 2 비율) | 0.39 | 0.42 |
| Bad% (보여준 것 중 0 비율, 전체) | 0.44 | 0.35 |
| Top1=0 (1위에 0을 올린 query 비율) | 0.41 | 0.24 |
| Quiet miss (2 없는 query에서 보여준 개수 평균) | 5.00 | 5.00 |
| repeat_low_value 유입 (Top5/전체) | 21/27 | 11/27 |
| lexical_trap 유입 (Top5/전체) | 16/28 | 16/28 |
| label 2 candidate recall@5 | 0.70 | 0.74 |
| label 2 candidate recall@7 | 0.81 | 0.91 |
| label 2 candidate recall@10 | 1.00 | 0.91 |

### 신규 항목의 e5 순위

| id | label | case | 간격 | e5 순위 | gate 후 | 과거 기록 |
| --- | --- | --- | --- | --- | --- | --- |
| q02-i | 0 | repeat_low_value | 191일 | 4 | 2 | 퇴근하고 소파에 누워서 아무것도 못 함. 피곤 |
| q04-k | 0 | repeat_low_value | 98일 | 7 | 5 | 헬스 갔다 옴. 하체 하는 날 |
| q05-j | 2 | worry_outcome | 8일 | 3 | 제거 | 출시 버튼 누르기 너무 무섭다. 아무도 안 쓰면 어떡하지 |
| q06-i | 2 | reversal | 15일 | 7 | 6 | PM이 기획 회의 같이 들어가자고 했는데 솔직히 귀찮다 |
| q08-k | 0 | repeat_low_value | 191일 | 3 | 3 | 월요일 출근 진짜 싫다 |
| q09-i | 2 | resolve_action | 22일 | 4 | 4 | 배달앱 지웠다. 이번엔 진짜로 |
| q11-i | 0 | repeat_low_value | 113일 | 3 | 2 | 오늘 주간 공유 발표함. 무난 |
| q12-j | 0 | repeat_low_value | 253일 | 2 | 2 | 오늘 아침은 컨디션 괜찮았다 |
| q12-k | 2 | resolve_action | 13일 | 9 | 제거 | 오늘부터 폰 침대에 안 들고 가기 |
| q14-i | 2 | worry_outcome | 6일 | 1 | 제거 | 팀장님한테 업무 너무 많다고 말해볼까. 괜히 불평처럼 들릴까 봐 망설여짐 |
| q16-a | 1 | ambiguous | 14일 | 2 | 2 | 국밥집 새로 생겼네. 다음에 가봐야지 |
| q16-b | 0 | repeat_low_value | 203일 | 3 | 3 | 회사 앞 국밥 맛집. 또 먹고 싶다 |
| q16-c | 0 | repeat_low_value | 286일 | 1 | 1 | 국밥 먹었다 |
| q16-d | 0 | lexical_trap | 86일 | 5 | 5 | 점심 뭐 먹을지 고르는 게 하루 중 제일 어려움 ㅋㅋ |
| q16-e | 1 | ambiguous | 29일 | 6 | 6 | 요즘 밖에서 사먹는 거 줄이는 중 |
| q16-f | 0 | lexical_trap | 228일 | 4 | 4 | 뜨끈한 거 먹으니까 감기 기운 좀 나아짐 |
| q16-g | 0 | unrelated | 4일 | 8 | 제거 | PR 리뷰 코멘트 반영 |
| q16-h | 0 | unrelated | 52일 | 7 | 7 | 동생이랑 영상통화 |
| q17-a | 0 | repeat_low_value | 78일 | 1 | 1 | 비 오는 날은 집에 있는 게 최고 |
| q17-b | 0 | repeat_low_value | 161일 | 3 | 2 | 하루 종일 누워서 넷플릭스 |
| q17-c | 0 | repeat_low_value | 2일 | 2 | 제거 | 이번 주 내내 집-회사만 왔다 갔다 함 |
| q17-d | 0 | lexical_trap | 22일 | 6 | 5 | 우산 또 잃어버림 |
| q17-e | 0 | lexical_trap | 93일 | 5 | 4 | 장마 시작. 빨래 안 마름 |
| q17-f | 1 | ambiguous | 133일 | 4 | 3 | 집에 있는 게 제일 편한 거 보면 집순이 체질인 듯 |
| q17-g | 0 | unrelated | 25일 | 8 | 7 | 회사 워크샵 장소 공지 |
| q17-h | 0 | unrelated | 46일 | 7 | 6 | 택시비 너무 많이 나옴 |
