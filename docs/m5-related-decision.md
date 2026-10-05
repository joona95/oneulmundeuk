# M5 결정: "문득, 그때" related-record pipeline (MVP)

근거 실험: `experiments/related/` (dataset v1.1 · 17 query · 157쌍, frozen). 실험 파일과 결과는 기록으로 그대로 둔다.
v1.1은 여러 실험에 쓴 **development benchmark**라 아래 수치는 구조를 고르는 근거일 뿐, 실제 성능 추정치가 아니다.

## 실험에서 확인한 것

| 실험 | 결과 (최대 5개 노출 기준) | 판단 |
| --- | --- | --- |
| e5 Top5 (baseline) | label 2 33/47 · label 0 37 · Bad% 0.44 · 정답 없는 query 4개 모두 5개씩 노출 | 후보 검색(recall)에는 유용 |
| M5-3 e5 similarity threshold | label 2/0 score 분포가 거의 완전히 겹침(AUC 0.67). 같은 말을 반복한 기록(repeat_low_value)의 score가 오히려 높음. threshold를 올려도 Bad% 0.44 → 0.43 → 0.41 수준 | similarity만으로 "다시 보여줄 가치"를 가르는 데 구조적 한계 |
| M5-2a Qwen 2B judge_v1 (label 2만) | label 2 27/47 · label 0 17 · Bad% 0.26 · Quiet miss 5.0 → 2.0 | recall 일부를 잃지만 noise를 크게 줄임 |
| M5-2e conservative reject filter | 154쌍 중 152쌍 keep (label 0 74개 중 2개만 제외) → e5 Top5와 같은 결과 | 너무 많이 유지해서 효과 없음 |
| M5-2b selector 조합 · M5-2c listwise · judge_v2 | 어느 것도 judge_v1(label 2만)보다 나은 trade-off를 만들지 못함 | 채택하지 않음 |

## MVP 정책

```
과거 기록 전체 → e5 similarity Top 30 → judge_v1로 후보마다 0/1/2 → label 2만 → e5 similarity DESC → 최대 5개
```

- **e5** (`dragonkue/multilingual-e5-small-ko-v2`) = 후보 검색 + label 2 후보 사이의 순서. similarity로 가치를 판정하지 않는다(threshold 없음).
- **Qwen** (`qwen3.5:2b-q4_K_M`, judge_v1 prompt 그대로) = 다시 보여줄 가치 판정. label 2만 노출한다.
- label과 similarity를 가중합하지 않는다. 1·0으로 자리를 채우지 않는다. **결과가 0개여도 정상**이다(0개 → 일반 저장 흐름, 1~5개 → M4 Related Memories).
- 실패: 판정에 실패한 후보는 제외하고, 모델을 쓸 수 없으면 빈 목록이다. 저장은 related 검색과 무관하게 항상 성공한다.
- 정책 코드 초안: `related/SemanticRelatedRecordFinder.kt` + `SemanticRelatedRecordFinderTest` / `RecordEditorRelatedFlowTest`. production wiring은 NoOp 그대로이고, Gradle 테스트 확인 전이라 working tree에만 두었다.

## 다시 볼 것

- 실제 사용자 기록이 쌓이면 이 정책(특히 label 2만 노출 · Top 30 · 최대 5개)을 다시 평가한다. dataset v1.1로 더 튜닝하지 않는다.
- 더 강한 on-device 모델이나 privacy-compatible strong LLM을 쓸 수 있게 되면 judge 교체를 검토한다. 교체해도 역할 분담(e5 = 검색 · 순서, judge = 가치 판정)은 유지한다.

## Android on-device PoC 결과 (2026-10-05 · CLOSED)

자세한 내용: `experiments/android-qwen-poc/README.md`, `experiments/android-qwen-poc/results-android-judge_v1.md`.

- SM-S948N (SM8850, Android 16)에서 llama.cpp b10456 CPU로 Ollama와 같은 Qwen3.5 2B Q4_K_M GGUF(1,274,396,992 bytes)를 서버 없이 실행.
  모델 로드 2.6 s · frozen 157쌍 157/157 · crash/OOM 없음 · 전체 511.7 s · pair median 3.12 s / p90 4.31 s / max 6.75 s · peak RSS 2,737 MB.
- 제품 지표 (e5 Top5 → Mac judge_v1 → Android judge_v1): Good@5 .71 → .58 → .64 · Bad .44 → .26 → .25 · Top1=0 .41 → .38 → .25 ·
  label 2 shown 33 → 27 → 30 · label 0 shown 37 → 17 → 16 · F7 0/4 → 1/4 → 1/4.
- Mac/Android label 일치는 107/157 (68%), κ .44지만 제품 지표가 유지되거나 일부 개선됐으므로 runtime parity 자체는 blocker가 아니다.

결론:
1. Qwen3.5 2B의 Android 완전 로컬 추론은 기술적으로 가능하다.
2. e5 candidate retrieval → LLM resurfacing-value judge → label 2 only → e5 ordering → max 5 구조는 Android runtime에서도 유효하다.
3. 모델 약 1.27 GB, peak RSS 약 2.74 GB, Top 30 판정 약 1분 이상, CPU 부하와 기기별 편차 때문에 현재 production integration은 보류한다.
4. 완전 로컬은 제품의 필수 가치가 아니므로, 다음 단계에서는 local storage + local e5 + stateless inference API를 우선 검토한다.
5. PoC는 폐기하지 않고, 향후 완전 로컬 inference를 다시 선택할 때의 feasibility baseline으로 보존한다.

production은 그대로다: `app/`에 llama.cpp / Qwen runtime, JNI/NDK, Room migration, WorkManager, model downloader를 넣지 않았고 RelatedRecordFinder는 NoOp이다.

## (참고) PoC 전 Android runtime 조사

Mac 실험은 Ollama로 돌렸다. Android 앱은 Ollama를 쓸 수 없다.

| 항목 | 확인한 사실 | 문제 |
| --- | --- | --- |
| Qwen runtime | Qwen3.5는 새 아키텍처(`qwen35`)라 최신 llama.cpp(GGUF)가 현실적 경로. LiteRT-LM 공식 지원 목록에는 Qwen3.5가 없음(Qwen2.5 · Qwen3-0.6B까지) | NDK/CMake로 llama.cpp를 빌드해 JNI로 붙여야 함 (새 native dependency) |
| Qwen 모델 크기 | Qwen3.5-2B Q4_K_M GGUF 1.40 GB | `INTERNET` 권한이 없어 다운로드 불가 → APK/AAB에 포함해야 함. Play는 asset pack당 1.5 GB · install-time 합계 4 GB |
| 메모리 | 2B Q4: 실행 시 약 1.5~2 GB (6~8 GB RAM 기기 권장) | 저사양 기기에서 프로세스 종료 위험 |
| 속도 | Mac 실험의 판정 1건 = 입력 약 400 토큰 + 출력 약 50 토큰. 폰 decode는 Snapdragon 8 Gen 3에서 약 18 tok/s(외부 보고) → 1건 수 초 | **30건 판정 = 플래그십에서도 수 분 단위로 예상**. M4는 저장 피드백 동안 finder 결과를 기다림 → 그대로 붙이면 "저장했어요"가 수 분간 멈춤 |
| 판정 동일성 | Ollama와 llama.cpp의 chat template · `think=false` 처리 · JSON schema(grammar)가 달라질 수 있음 | 앱 runtime으로 judge_v1 157쌍을 다시 돌려 Mac 결과와 label 일치를 확인해야 함 (benchmark가 아니라 runtime 동등성 확인) |
| e5 runtime | ONNX Runtime Android + XLM-R SentencePiece tokenizer 필요. 모델은 ONNX 변환 필요(fp32 약 470 MB / int8 약 120 MB, 양자화하면 score가 조금 바뀜) | 새 dependency 2개(ONNX Runtime, tokenizer) |
| embedding 저장 | 매 저장마다 과거 기록 전체를 다시 embedding하면 기록 수에 비례해 느려짐 | embedding cache 테이블 = Room Migration(1, 2) (DB 변경) |

이 항목들이 결정되기 전까지 release/debug의 production finder는 `NoOpRelatedRecordFinder`로 둔다.
