# M5-0 · Related retrieval 평가셋

오늘문득의 "문득, 예전의 생각이 떠올랐어요"에 무엇을 보여줄지 고르는 방법(M5)을 비교하기 위한 작은 평가 환경.
Android 앱 코드, Room schema, `RelatedRecordFinder` 구현과는 분리되어 있다 (Gradle 빌드에 포함되지 않음, stdlib Python만 사용).

평가 질문은 "의미가 비슷한가?"가 아니라 **"현재 생각을 기록한 시점에 이 과거 기록을 다시 보여주는 것이 의미 있는가?"** 이다.

## 라벨

| label | 의미 |
| --- | --- |
| **2** 보여주고 싶음 | 다시 보면 연결·변화·반복·과거의 다짐/결과가 느껴짐 |
| **1** 애매함 | 관련은 있지만 굳이 지금 보여줄 정도는 아님 |
| **0** 보여주지 않음 | 무관하거나, 단어/주제만 비슷하거나, 다시 볼 가치가 낮은 반복 |

## 케이스 태그 (`case`)

| case | label | 설명 |
| --- | --- | --- |
| `change` | 2 | 같은 고민의 시간에 따른 변화 |
| `worry_outcome` | 2 | 과거의 걱정 → 현재의 결과 |
| `resolve_action` | 2 | 과거의 다짐 → 현재 행동 |
| `recurring` | 2 | 반복되는 관심사/패턴 (이직 고민, 그림, 소비, 영어 공부) |
| `reversal` | 2 | 생각이 반대로 변함 |
| `implicit_link` | 2 | 단어는 다르지만 의미 있는 연결 (예: "계단 3층에 숨참" ↔ "체력 좋아짐") |
| `ambiguous` | 1 | 관련은 있으나 resurfacing 가치가 애매 |
| `repeat_low_value` | 0 | **hard negative** — 의미는 매우 비슷하지만 다시 볼 가치가 낮은 반복 (대부분 최근: "어제도 피곤했다") |
| `lexical_trap` | 0 | **hard negative** — 단어/주제만 겹침 ("배달 떡볶이" ↔ "배달 안 시킴") |
| `unrelated` | 0 | 완전 무관 |

`case`는 라벨을 설명하기 위한 분석용 태그이고, 시스템 입력으로 쓰면 안 된다 (`label`, `case`, `note`는 정답지).

## 데이터 포맷 — `dataset.json`

```jsonc
{
  "version": 1,
  "labels": { "2": "...", "1": "...", "0": "..." },
  "cases":  { "change": "...", ... },
  "queries": [
    {
      "id": "q01",
      "text": "요즘은 회의에서 내 의견 말하는 게 예전보다 덜 무섭다. 오늘도 그냥 말함",
      "date": "2026-09-28",            // 현재 기록 날짜
      "emotion": "calm",               // stable key 또는 null (앱과 동일)
      "category": "커리어",             // 기본 카테고리 이름 또는 null
      "theme": "회의에서 말하기 — 걱정이 편안함으로",   // 사람이 읽는 설명
      "candidates": [
        {
          "id": "q01-a",
          "text": "새 회사 첫 주. 다들 너무 잘하는 것 같아서 내가 제대로 할 수 있을지 걱정됨",
          "date": "2025-11-03",          // 항상 query보다 과거
          "emotion": "anxious",
          "category": "커리어",
          "label": 2,                    // 정답
          "case": "worry_outcome",       // 정답 분석용
          "note": "입사 초 걱정 → 지금은 편해짐"
        }
      ]
    }
  ]
}
```

- 시스템이 볼 수 있는 입력: `text`, `date`, `emotion`, `category` (본문만 / +감정 / +카테고리 실험 가능하도록 함께 둠).
- 후보 풀은 **query별로 독립**이다 (실제 앱은 전체 기록에서 고르지만, 라벨링을 감당할 수 있게 query마다 8~10개로 제한). 시스템은 각 query의 후보 중 최대 5개를 순서대로 고른다.

## 분포 (v1)

- query 15개 · 쌍 131개 · query당 후보 8~10개
- label 2: 42 (32%) · 1: 30 (23%) · 0: 59 (45%)
- 2 내부: change 11 · worry_outcome 5 · resolve_action 7 · recurring 7 · reversal 5 · implicit_link 7
- 0 내부: repeat_low_value 17 · lexical_trap 24 · unrelated 18 (hard negative 41개)
- **q02(피곤), q15(처음 해본 클라이밍)는 label 2가 없다** — "아무것도 보여주지 않는 것"이 정답에 가까운 query. 앱 정책(결과 0개면 Related 화면 자체를 안 띄움)과 맞닿아 있다.

### v1.1 (M5-0.1 · 날짜 편향 보정)

v1에는 "좋은 연결(2)은 오래됐고, 가치 낮은 반복(repeat_low_value)은 최근"이라는 편향이 있었다
(repeat_low_value 15/17이 14일 미만, label 2는 42개 중 1개만 14일 미만). 날짜만으로 풀리는 평가셋이 되지 않도록
**기존 항목은 그대로 두고 반례만 추가**했다 (`added_in: "0.1"`, 26 pairs).

- 오래됐지만 가치 낮은 반복 9개 (78~286일 전): "퇴근하고 소파에 누워서 아무것도 못 함" 등
- 최근이지만 의미 있는 연결 5개 (6~22일 전): 걱정 → 결과 2, 다짐 → 행동 2, 반전 1
- label 2가 없는 query 2개(q16 국밥, q17 비 오는 하루) — 오래된 유사 기록 포함, 정답은 "아무것도 안 보여줌"

v1.1: queries 17 · pairs 157 · label 2: 47 / 1: 33 / 0: 77 · label 2 없는 query 4개 (q02, q15, q16, q17).
간격 분포와 14일 gate 비교는 `bias_report.py` → `results-m5-0.1.md`.

## 고정 benchmark 원칙

**M5-0.1(v1.1) 이후 이 평가셋은 M5 실험의 고정 benchmark다.**

- 실험 결과를 본 뒤 `text` / `label` / `date` / `case` / `emotion` / `category`를 수정·삭제·relabel하지 않는다.
  특정 방법이 틀린 걸 보고 정답이나 문장을 고치면 평가가 그 방법에 맞춰진다.
- 라벨이 정말 잘못됐다고 판단되면 고치지 말고 별도 버전(v2)으로 분리하고, 이전 버전 결과와 섞어 비교하지 않는다.
- 새 실패 유형을 넣고 싶으면 별도 hold-out 세트로 만든다.
- 날짜는 라벨의 판단 근거가 아니다. 모든 라벨은 `text`만 읽어도 납득할 수 있어야 한다.

`python3 eval.py --check`로 언제든 다시 확인할 수 있다 (id 중복, 라벨/케이스 일치, 후보 날짜 < query 날짜, emotion/category 값 검증 포함).

## 평가 방법

시스템 결과는 `{query id: [보여줄 candidate id, ...]}` JSON 하나. 앞의 최대 5개만 평가한다 (`RelatedRecordFinder.LIMIT`).
threshold로 "의미 있는 것만" 고르는 시스템은 5개보다 적게(0개 포함) 내도 된다.

```bash
python3 eval.py --rankings runs/v1_embedding.json -v
```

| 지표 | 정의 | 보는 이유 |
| --- | --- | --- |
| **Good@5** | Top5에 들어온 2의 수 / min(5, 그 query의 2 개수). 2가 있는 query만 평균 | 보여주고 싶은 걸 얼마나 건졌나 |
| **Top1=0** | 1위에 0을 올린 query 비율 | 가치 없는 걸 "자신 있게" 끌어올렸나 |
| **Bad%** | 보여준 전체 중 0의 비율 | 화면에 노이즈가 얼마나 섞이나 |
| **Worth%** | query별 (보여준 것 중 2 비율)의 평균 | 보여준 것의 질 |
| nDCG@5 | gain 2→3, 1→1, 0→0 | 순서까지 본 한 줄 요약 (보조) |
| Quiet miss | 2가 없는 query(q02, q15)에서 보여준 개수 평균 | 0이 이상적. 순위만 내는 시스템은 항상 5 → threshold 실험 때 의미 있음 |
| 유형별 회수 | 2의 case별 Top5 진입 수 (change, reversal, implicit_link …) | **어떤 종류의 연결을 놓치는지** |
| hard negative 유입 | repeat_low_value / lexical_trap 중 Top5에 들어온 수 | **유사도가 높아서 틀리는 경우** |

V1을 볼 때 핵심 질문은 두 개뿐이다.
1. 우리가 2라고 붙인 걸 Top5에 얼마나 넣었나 (Good@5, 유형별 회수)
2. 0, 특히 hard negative를 얼마나 자신 있게 올렸나 (Top1=0, hard negative 유입)

### 기준선 (eval.py 내장, 평가 코드 sanity check용)

| system | Good@5 | nDCG@5 | Worth% | Bad% | Top1=0 | Quiet miss |
| --- | --- | --- | --- | --- | --- | --- |
| `oracle` (라벨순 Top5) | 1.00 | 1.00 | 0.56 | 0.08 | 0.00 | 5.0 |
| `oracle_quiet` (2만, 없으면 0개) | 1.00 | 0.91 | 1.00 | 0.00 | 0.00 | 0.0 |
| `random` (seed 0~19 평균) | 0.59 | 0.54 | 0.33 | 0.45 | 0.46 | 5.0 |
| `recency` (최신순) | 0.08 | 0.11 | 0.04 | 0.73 | 0.93 | 5.0 |

- 후보가 8~10개뿐이라 **무작위로 5개만 골라도 Good@5 ≈ 0.6**이다. V1이 의미 있으려면 Good@5보다 Top1=0 / Bad% / hard negative 유입에서 random보다 확실히 좋아야 한다.
- recency가 최악인 건 의도된 것: hard negative 대부분이 "최근의 비슷한 기록"이다.

## M5-1 · embedding cosine baseline

`text`만 embedding → cosine 내림차순. category/emotion/date/heuristic/reranker/threshold 없음.
모델: `e5-small-ko` (dragonkue/multilingual-e5-small-ko-v2, 모바일 후보) vs `kure` (nlpai-lab/KURE-v1, 상한 비교용).

```bash
./run_m5_1.sh        # venv + 설치 + 두 모델 embedding + results.md 생성 (Hugging Face 접속 필요)
```

- `embed.py --model <e5-small-ko|kure>` → `runs/<model>.json` (query별 후보 전체의 cosine·순위, 모델 revision 기록)
- `report.py --small … --big … --out results.md` → 전체 지표, label 분리(모델 내부 순위 기준), case별 결과,
  대표 false positive/negative, 공통 실패, 큰 모델이 해결/악화한 사례, query별 ranking·score
- `eval.py --system lexical`: 모델 없는 글자 2-gram 겹침 참고선

## M5-2a · local LLM resurfacing-value 판정

`e5-small-ko 순서 → 후보 하나씩 LLM 판정(0/1/2) → 2만 e5 순서로 최대 5개, 없으면 0개`.
모델 입력은 현재 기록 text + 과거 기록 text + `prompts/judge_v1.txt`의 일반 기준뿐 (label/case/note/date/emotion/category/e5 순위/cosine 없음).
157 pairs × 2회 = 314회 호출. 판정은 전부(all) 한 번만 하고, Top10 / Top7 결과는 같은 판정으로 재집계한다.

- `llm_judge.py`: Ollama 호출 (stdlib만). `--dry-run` / `--smoke` / `--max-calls N`. 결과는 `runs/llm-<model>-<prompt>.jsonl`에
  한 줄씩 append되고, 다시 실행하면 끝난 pair는 건너뛴다. 실패는 label 0으로 바꾸지 않고 `status: error`로 남는다.
  첫 호출과 20회마다 `/api/ps`로 100% GPU 여부, macOS swap 증가(256MB)를 확인해 문제 있으면 스스로 멈춘다.
- `llm_report.py --out results-m5-2a.md`: 저장된 판정만으로 지표 · 정답 0개 query · F1~F7 · 판정 분포 · 2회 일치율 · 실패 사례 생성.

실행 (8GB Mac, 모델 1개만, context 2048, thinking off, temperature 0, seed 7):

```bash
# 터미널 1 — 메뉴바의 Ollama 앱은 종료한 뒤, 환경 변수를 준 서버를 직접 띄운다
OLLAMA_CONTEXT_LENGTH=2048 OLLAMA_FLASH_ATTENTION=1 OLLAMA_MAX_LOADED_MODELS=1 OLLAMA_NUM_PARALLEL=1 ollama serve

# 터미널 2
ollama pull qwen3.5:4b-q4_K_M          # 약 3.3GB
cd experiments/related
python3 llm_judge.py --dry-run         # 계획만
python3 llm_judge.py --smoke           # 1건 + GPU/swap 상태
ollama ps                              # PROCESSOR 100% GPU, CONTEXT 2048 확인
python3 llm_judge.py --max-calls 20    # 20건만
python3 llm_judge.py                   # 나머지 (Ctrl-C 후 다시 실행하면 이어서)
python3 llm_report.py --out results-m5-2a.md
ollama stop qwen3.5:4b-q4_K_M
```

## 다음 단계 (이번 단계에서는 하지 않음)

1. **V1**: 본문 embedding → cosine 내림차순 Top5. 모델/런타임은 아직 고르지 않았다. 결과를 `runs/<name>.json`으로 저장해 같은 eval로 비교.
2. V1 결과에 따라 V2 후보:
   - 반복(repeat_low_value)을 많이 올리면 → 시간 간격, novelty/change heuristic
   - change / reversal / implicit_link를 놓치면 → embedding Top10 후보 → 작은 LLM이 "지금 다시 보여줄 가치"로 rerank
   - 감정/카테고리: 본문만 / +카테고리 / +감정을 따로 돌려 비교. 카테고리가 같은 카테고리끼리만 뭉치게 하면 retrieval에서 제외, 감정 변화가 신호면 rerank 보조로만.
3. 그 다음에야 threshold (Quiet miss를 0에 가깝게, 결과 0~5개)와 Android 런타임을 정한다.

## 한계

- 한 사람(가상의 개발자) 시점의 synthetic 기록이고, 라벨은 한 명의 판단이다. 1 ↔ 2 경계는 주관적일 수 있다 (`note`에 판단 근거를 남김).
- 131쌍은 baseline 비교용으로 충분하지만 미세한 차이(±0.05)를 가르기에는 작다. 큰 차이와 실패 유형을 보는 용도.
