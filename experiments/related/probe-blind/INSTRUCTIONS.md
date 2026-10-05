# 판정 작업 지시 (blind)

이 폴더의 파일만 사용해서, 157개의 (현재 기록, 과거 기록) 쌍을 하나씩 판정한다.

## 사용할 수 있는 파일 (이 폴더 안의 이것들뿐)

- `INSTRUCTIONS.md` — 이 문서
- `judge_v1.txt` — 판정 기준. `### SYSTEM` 부분이 너의 판정 지시이고, `### USER` 부분이 각 쌍이 주어지는 형식이다.
- `blind_input.jsonl` — 한 줄 = `{"pair_id", "current", "past"}`
- `check_results.py` — 결과 형식 검사
- `claude-code-feasibility-judge_v1.jsonl` — 네가 쓰는 결과 파일

## 하지 말 것

- 이 폴더 밖의 파일·폴더를 열거나 검색하지 않는다 (상위 폴더, 홈 폴더, git 저장소, 다른 프로젝트 포함). 웹도 쓰지 않는다.
- 판정에 쓰는 정보는 `judge_v1.txt`, 그 쌍의 `current`, 그 쌍의 `past` 셋뿐이다.
- 판정을 코드로 하지 않는다. 키워드 규칙, 유사도 계산, 다른 모델/API 호출, subagent 사용 금지. 네가 직접 읽고 판단한다.
  (파일을 읽고, 결과 줄을 덧붙이고, `check_results.py`를 실행하는 데에만 명령을 쓴다.)
- `judge_v1.txt`와 `blind_input.jsonl`을 수정하지 않는다. 판정 기준을 바꾸거나 보태지 않는다.
- 앞에서 쓴 결과를 고치지 않는다. 결과 파일은 덧붙이기만 한다.
- 앞의 판정을 참고해 뒤의 판정 기준을 조정하지 않는다. label 비율을 맞추려고 하지 않는다. 같은 `current`가 여러 번 나와도 각 쌍은 독립적으로 판정한다.

## 순서

1. `python3 check_results.py`를 실행한다. 첫 줄의 sha가 `553ab6176c0293f9`인지 확인한다. 다르면 멈추고 알린다.
2. `judge_v1.txt` 전체를 읽는다.
3. `blind_input.jsonl`을 앞에서부터 20줄씩 읽는다 (이미 판정한 것이 있으면 `check_results.py`가 알려주는 "다음" pair부터).
4. 각 쌍마다 `judge_v1.txt`의 SYSTEM 지시를 따르고, USER 형식의 `{current}`, `{past}` 자리에 그 쌍의 텍스트가 들어간 것으로 보고 판정한다.
   - 결과: `{"pair_id": "p001", "reason": "판정 근거 한국어 한 문장", "label": 0}` (label은 정수 0, 1, 2 중 하나)
   - reason을 먼저 정하고 label을 정한다. reason은 짧게 한 문장.
5. 20개를 판정할 때마다 결과 파일 끝에 한 줄씩(JSON Lines, UTF-8) 덧붙이고 `python3 check_results.py`를 실행한다. ERROR가 나오면 형식만 고쳐서 다시 확인한다 (이미 쓴 판정 내용은 바꾸지 않는다).
6. `완료 157/157 · 전부 완료`가 나오면 끝낸다. 결과를 요약하거나 분석하지 않는다.

중간에 session이 끊기면, 새 session에서 이 문서부터 다시 읽고 `check_results.py`가 알려주는 "다음" pair부터 이어서 한다.
