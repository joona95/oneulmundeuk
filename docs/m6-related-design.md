# M6-0 설계: 관련된 생각 찾기 (local semantic pipeline)

상태: **설계 승인 (2026-10-05) · M6-1 완료 (저장 흐름 분리; unit test · debug/release build 통과, instrumentation test 미실행) · M6-2 완료 (schema v2 · 무효화; unit test · debug/release build · instrumentation 16개 통과, SM-S948N).** source of truth: `docs/m5-related-decision.md`의 "Production 결정 (M5-4)".

```
Room + local e5 + local Qwen3.5-2B Q4_K_M
저장 → (즉시 완료) ··· background: e5 Top 30 → judge_v1 → label 2만 → e5 similarity DESC → 최대 5개 → 결과 저장 ··· 이후 Home / Record Detail에서 노출
```

판정 정책(Top 30 · judge_v1 · label 2만 · similarity 순 · ≤ 5 · 0개도 정상)은 M5 그대로이며 여기서 바꾸지 않는다.
UI · 제품 결정은 아래 "확정된 UI · 제품 결정" 표가 기준이다. 카드 모양 · 제안 sheet 문구 · 디자인만 해당 구현 단계(M6-4 · M6-5) 전에 확인한다.

### 확정된 UI · 제품 결정 (2026-10-05)

| 항목 | 결정 |
| --- | --- |
| Home 의미 기반 관련 카드 위치 | 작성 영역 아래, 시간 기반 `다시 만난 생각` 위 |
| Record Detail 섹션 제목 | `이어지는 기록` |
| Related Memories의 `방금 남긴 생각` 라벨 | `이 생각에서` |
| 최초 활성화 시 분석 대상 | 최신 기록 10개 |
| background 분석의 충전 조건 | 두지 않음. 실제 기기 검증 후 필요하면 추가 |
| `나중에` 선택 후 | 자동으로 다시 제안하지 않음 (Settings에서 켤 수 있음) |
| 모델 배포 | e5와 Qwen을 하나의 선택형 모델 다운로드 흐름으로 제공 |
| AI 결과 일괄 삭제 | MVP에서 제공하지 않음 |

---

## 1. M4 저장 직후 Related Memories → **흐름은 제거, 화면은 유지 · 용도 변경**

| 대상 | 결정 | 이유 |
| --- | --- | --- |
| 저장 직후 Related Memories로 자동 이동 | **제거** | 판정 1쌍 ≈ 3.3 s × Top 30 ≈ 100 s (PoC). 저장 피드백 동안 결과가 나올 수 없고, 저장이 inference를 기다리지 않는다는 결정과 정면으로 충돌 |
| 저장 피드백(jelly · "저장했어요") | 유지. related 대기 · `HOLD_RELATED`는 제거 | 저장 UX는 항상 같은 길이로 끝난다 |
| Related Memories 화면 (Thread B v3) | **유지 · 진입점 변경** | Design Freeze 화면. Home 카드에서 연다. 데이터는 route로 넘긴 id가 아니라 저장된 결과에서 읽는다 |
| `RelatedMemoriesRoute(recordId, relatedIds)` | `RelatedMemoriesRoute(recordId)`로 축소 | 결과는 store가 소유. 과거 기록이 수정 · 삭제되면 자연히 반영 |
| `DebugRelatedRecordFinder` (flag 파일) | 제거 → debug 전용 "가짜 결과 쓰기"로 대체 | 저장 직후 흐름이 없어지므로 UI 확인은 저장된 결과로 한다 |
| 자동 알림 · 자동 화면 이동 | 하지 않음 | 결과는 사용자가 다음에 Home · Detail을 볼 때 조용히 나타난다 |

노출 위치 (새 UI라 **디자인 확인 필요**, 기존 토큰 · Design Freeze 규칙 안에서):
- **Home**: 결과가 생긴 가장 최근 기록 1건을 "문득, 예전의 생각이 떠올랐어요" 카드로 보여주고, 탭하면 Related Memories. 사용자가 열었거나 더 새 결과가 생기면 교체. 최근 N일(기본 7일) 안의 결과만. 결과가 없으면 섹션 없음.
  위치: 작성 영역 아래, "다시 만난 생각" 위 (확정). "다시 만난 생각"은 날짜 기반이라 그대로 둔다. 카드 모양은 M6-4에서 디자인 확인.
- **Record Detail**: 그 기록의 결과(최대 5개, 14px 마커 규칙)를 본문 아래 섹션으로. 0개 · 미분석이면 섹션 없음. 제목 `이어지는 기록` (확정)
- Related Memories의 "방금 남긴 생각" 라벨은 며칠 뒤 열 수도 있으므로 `이 생각에서`로 바꾼다 (확정, M6-4에서 반영).
- 빈 상태 · 진행 중 표시 · spinner · 점수 · AI 설명은 보여주지 않는다 (M4 원칙 유지).

## 2. production component 경계 (패키지 `related/`, UseCase/Mapper 층 없음)

| component | 책임 | 하지 않는 것 |
| --- | --- | --- |
| `RecordChangeListener` | `RecordRepository`가 create / update / delete 커밋 **후** 호출. 분석 대기열만 갱신 (DB 쓰기 1회) | 모델 로드 · inference. 예외를 저장으로 전파하지 않음 (내부에서 삼킴) |
| `TextEmbedder` (e5) | text → 384차원 벡터. `"query: "` prefix · 정규화 등 M5 실험과 같은 전처리 · `modelId` 제공 | 순위 · 판정 |
| `EmbeddingStore` (Room) | 기록별 embedding 저장 · text hash / model id로 최신 여부 판단 | 계산 |
| `CandidateRetriever` | 저장된 embedding으로 brute-force cosine → **대상보다 먼저 쓴 기록**(createdAt < 대상) 중 Top 30 | 가치 판정 |
| `RelatedValueJudge` (Qwen) | (현재 text, 과거 text) → `Judgment(label 0/1/2 \| Failed(reason))`. judge_v1 prompt(asset, sha `553ab6176c0293f9` 검사) · temperature 0 · JSON schema · thinking off | 후보 선택 · 순서 |
| `selectWorthShowing` (순수 함수) | label 2만 → similarity DESC(동률 id) → ≤ 5 | – |
| `RelatedAnalyzer` | 대상 1건 분석: embedding 확보 → 후보 → 판정(캐시 재사용) → 완료 기록. 매 판정 전 취소 · 대상 삭제 확인 | 스케줄링 · 모델 다운로드 |
| `RelatedStore` (Room) | 분석 상태 · 판정 캐시 · 결과 조회(Flow). 결과는 판정에서 **파생**(별도 결과 테이블 없음) | – |
| `RelatedWorker` (WorkManager) | 대기열(DB)을 오래된 순으로 직렬 처리. 실행당 모델 1회 로드 · 끝나면 해제 | UI |
| `RelatedFeature` (설정 + 상태) | 기능 ON/OFF · 제안 상태 · 모델 상태를 하나의 상태로 노출 | – |
| `ModelManager` + `ModelSource` | 모델 artifact 정의 · 다운로드 · 검증 · 삭제 (5 · 8절) | 호스팅 제공자 결정 |

- 기존 `RelatedRecordFinder` 인터페이스(저장 직후 요청-응답)는 쓰지 않는다 → 제거.
- 판정은 **기록 text만** 입력으로 쓴다 (감정 · 카테고리 · 날짜 없음, M5와 같음).
- llama.cpp(JNI) · ONNX Runtime은 `TextEmbedder` · `RelatedValueJudge` 구현 뒤에만 있다. 나머지는 fake로 단위 테스트한다.

## 3. create / edit / delete 무효화 규칙

Room (Migration 1 → 2, 모두 `records.id` FK **ON DELETE CASCADE**):

| table | 핵심 열 |
| --- | --- |
| `record_embedding` | record_id (PK) · model_id · text_hash · vector BLOB · updated_at |
| `related_analysis` | record_id (PK) · status(PENDING · RUNNING · DONE · FAILED) · pipeline_version · text_hash · attempts · error · seen_at · updated_at |
| `related_judgment` | (target_id, candidate_id) PK · pipeline_version · target_hash · candidate_hash · similarity · label(nullable) · status(OK · FAILED) · created_at |

- `text_hash` = 정규화한 text(trim)의 hash. **text가 바뀔 때만** 무효화한다 (감정 · 카테고리 수정은 판정 입력이 아니므로 무관).
- `pipeline_version` = e5 model id + Qwen 모델 sha256 + prompt sha + 정책 버전. 다르면 그 embedding · 판정은 쓰지 않는다.
- 결과 = `related_judgment`에서 target = X · status OK · label 2 · 두 hash가 현재 text와 같음 · analysis DONE → similarity DESC, id ASC, 최대 5개. 분석이 PENDING · RUNNING이면 0개 (확정).

| 사건 | 규칙 |
| --- | --- |
| **create** | 기능이 켜져 있고 모델이 준비됐으면 `related_analysis(PENDING)` 1행 추가 + worker 예약. embedding도 worker가 계산 (저장 경로에서 e5 실행 안 함). 꺼져 있으면 아무것도 하지 않음 |
| 다른 기록이 새로 생김 | 기존 기록의 결과는 **바꾸지 않는다** (후보는 대상보다 먼저 쓴 기록뿐이라 새 기록은 후보가 아님) |
| **edit (text 변경)** | ① 그 기록의 embedding 무효 · 자신의 분석 → PENDING(자신이 target인 판정 삭제) ② 그 기록이 candidate인 판정 삭제 → 해당 target들의 분석 → PENDING. 재분석 때 바뀐 쌍만 다시 판정(나머지는 캐시) |
| edit (text 그대로) | 아무것도 하지 않음 |
| **delete** | CASCADE로 embedding · 분석 · 그 기록이 target/candidate인 판정이 모두 삭제. 다른 기록의 결과는 남은 label 2에서 다시 파생(재분석 없음, 빈 자리를 1·0으로 채우지 않음) |
| 모델 · prompt · 정책 버전 변경 | 기존 판정은 버전 불일치로 무시. 재분석은 아래 backlog 규칙으로 |
| 기능 OFF / 모델 삭제 | 저장된 결과는 **그대로 보여준다** (분석만 멈춤). 결과 일괄 삭제는 MVP에서 제공하지 않는다 (확정) |

### M6-2 구현 메모 (schema v2)

- 열 추가 (위 "핵심 열"에 더해): `record_embedding.dim` · `related_analysis.queued_at`(FIFO 기준) · `related_analysis.completed_at`. 상태 값은 문자열 key(`PENDING` 등)로 저장.
- PK / FK / index: `record_embedding`(PK record_id) · `related_analysis`(PK record_id, index (status, queued_at)) · `related_judgment`(PK (target_id, candidate_id), index candidate_id).
  FK는 모두 **자식 → `records.id`, ON DELETE CASCADE** 한 방향뿐이다. 기록을 지우면 그 기록의 embedding · 분석 · 그 기록이 target 또는 candidate인 판정이 지워지고, related 테이블에서 records 쪽으로 지워지는 것은 없다. FK 강제는 `AppDatabase.ENFORCE_FOREIGN_KEYS`(PRAGMA foreign_keys = ON).
- **text version** (`RelatedText`): NFC · 줄바꿈(CRLF/CR → LF) · 앞뒤 공백 trim만 정규화하고 `t1:` + SHA-256(hex). 대소문자 · 내부 공백 · 문장부호 · 이모지는 그대로라 바뀌면 새 버전이다. 규칙을 바꾸면 prefix를 올려 모든 캐시를 무효화한다.
- **결과 조회**: SQL이 label 2 · status OK · analysis DONE · pipeline_version 일치 · similarity DESC를 고르고, `RelatedStore`가 target · candidate · analysis hash를 현재 text와 비교해 남은 것 중 앞에서 5개만 쓴다 (SQLite에서 현재 text의 hash를 계산할 수 없어서). 오래된 쌍은 빠질 뿐 label 1 · 0으로 대체되지 않는다.
- **edit**: repository는 trim한 원문이 달라졌는지만 알려 주고, `RelatedInvalidator`가 hash로 다시 판단한다. 현재 hash와 다른 것만 지운다 → 정규화로 같아지는 수정(앞 공백 등)은 아무것도 바꾸지 않는다.
- **기능이 꺼져 있을 때 edit**: 이미 분석 행이 있으면 PENDING으로 되돌리고, 없으면 새로 만들지 않는다 (꺼진 동안 backlog가 쌓이지 않게). 앱은 M6-2에서 `analysisEnabled = { false }`로 연결돼 있고 대기열을 소비하는 worker가 없으므로 inference는 실행되지 않는다.
- **결과 노출 정책 (확정, 2026-10-05)**: target 분석이 PENDING · RUNNING이면 기존 결과를 **노출하지 않는다**. 새 분석이 DONE이 된 뒤에만 다시 노출한다.
  stale 결과를 계속 보여주는 것보다 일시적으로 결과가 없는 상태를 택한다 (candidate 수정으로 affected target이 재분석되는 동안에도 마찬가지).
- migration: `MIGRATION_1_2`는 테이블 3개 · index 2개 생성만 하고 기존 테이블은 건드리지 않는다. destructive fallback 없음. schema JSON: `app/schemas/.../1.json` 유지, `2.json`(Room 생성, migration SQL과 일치)을 함께 커밋.

backlog (기능을 처음 켰을 때 · 버전이 바뀌었을 때): **최신 기록 10개**를 최신순으로 PENDING에 넣는다. 그보다 오래된 기록은 자동 분석하지 않는다 (10개 × ≈ 100 s ≈ 17분, 그 이상은 배터리 · 발열 부담). 이후 새 기록은 하나씩. (10개 확정)

## 4. background inference lifecycle

| 항목 | 원칙 |
| --- | --- |
| 실행 | WorkManager unique work `related-analysis` (enqueue 정책 APPEND_OR_REPLACE). **DB의 PENDING 행이 대기열**이라 프로세스가 죽어도 남는다 |
| 순서 · 동시성 | 대상 1건씩 직렬, 먼저 들어온 PENDING부터 (FIFO · backlog는 최신 기록부터 넣음). 모델 instance 1개 · 동시 inference 없음 |
| 모델 메모리 | worker 실행 시 로드, 대기열이 비거나 실행 예산이 끝나면 **즉시 해제** (peak RSS ≈ 2.7 GB를 상주시키지 않음) |
| 실행 조건 | battery not low · storage not low. 충전 · 유휴는 요구하지 않음 (결과가 너무 늦어지지 않게). 발열 상태(PowerManager thermal) SEVERE 이상이면 중단 → 나중에 재시도. 충전 조건은 두지 않는다 (확정, 실제 기기 검증 후 필요하면 추가) |
| 실행 예산 | 1회 실행 ≈ 8분 이내에서 끊고 남은 대기열은 다음 실행으로 (WorkManager 10분 제한, foreground service 쓰지 않음) |
| 판정 실패 (형식 오류 · 잘림) | 그 쌍만 FAILED로 저장, **같은 버전에서 재시도하지 않음** (greedy라 결과가 같음, M5 "1 attempt"와 같음). 분석은 나머지로 DONE |
| runtime 오류 (모델 로드 실패 · OOM · JNI 오류) | 대상 attempts +1 → WorkManager backoff 재시도, 최대 3회 → FAILED(error). 다음 버전 변경 · 기능 재활성화 때 다시 PENDING |
| 모델 파일 손상 · sha 불일치 | 모델 상태 Broken → 분석 중단, Settings에서 다시 받기 |
| 취소 | 기능 OFF · 모델 삭제 → work 취소. 진행 중 판정은 llama.cpp abort로 끊고, 이미 저장된 판정은 캐시로 남김. 대상이 삭제되면 다음 판정 전에 확인하고 그 대상만 버림 |
| 재개 | RUNNING으로 남은 행(프로세스 종료)은 다음 실행에서 PENDING으로 되돌리고 캐시된 판정부터 이어서 |
| 저장 경로와의 관계 | 저장은 기록 insert + (조건 충족 시) PENDING 1행. listener 실패 · worker 실패는 저장에 영향 없음 |

## 5. 상태 모델

**기능 설정** (`enabled`) × **모델 상태** (파일 · 다운로드 작업에서 파생):

| 모델 상태 | 의미 |
| --- | --- |
| `Absent` | 모델 없음 (기본) |
| `Downloading(bytes, total)` | 받는 중 (일시정지 · 네트워크 대기 포함) |
| `Verifying` | sha256 · 크기 확인 중 |
| `Ready` | 검증된 모델 묶음(Qwen + e5)이 앱 내부 저장소에 있음 |
| `Failed(reason)` | 다운로드 · 검증 실패 (공간 부족 · 네트워크 · 불일치). 재시도 가능 |

| enabled | 모델 | 분석 | Settings 표시 |
| --- | --- | --- | --- |
| OFF | Absent | 없음 | `관련된 생각 찾기` OFF · 켜면 다운로드 동의부터 |
| OFF | Ready | 없음 (모델 유지) | OFF · `AI 모델 삭제` 가능 |
| ON | Downloading / Verifying | 대기 | 진행률 · 취소 |
| ON | Ready | **실행** | ON · `AI 모델 삭제` 가능 |
| ON | Failed | 없음 | 다시 받기 |

- ON → OFF: inference만 멈춤 (모델 · 결과 유지).
- OFF → ON: 모델이 Ready면 바로 backlog, Absent면 다운로드 동의 화면.
- `AI 모델 삭제`: 파일 삭제 + **기능도 OFF**로 바꾼다 ("ON인데 아무 일도 안 하는" 상태를 만들지 않기 위해). 결과는 유지.
- 저장: 설정(enabled · 제안 상태)은 DataStore, 모델 파일은 `noBackupFilesDir/models/`.

## 6. 기록 10개 도달 시 제안

1. 조건: 기록 수 ≥ 10 · 제안을 아직 보여주지 않음 · 기능 OFF · 모델 Absent.
2. 시점: 10번째 저장의 저장 피드백이 끝나고 Home으로 돌아왔을 때 **한 번**. 저장 흐름 안에서는 띄우지 않는다.
3. 형태 기본안: Home 위 bottom sheet. 내용 — 무엇을 하는지(예전 기록 중 이어지는 생각을 찾아 줌), 기기 안에서만 분석하고 기록을 보내지 않음, 약 1.4 GB (Qwen + e5) 다운로드 · Wi-Fi 권장, 나중에 설정에서 끄거나 모델을 지울 수 있음. 버튼 `켜기` / `나중에`. 문구 · 디자인은 M6-5에서 확인.
4. `켜기` → 저장 공간 확인(모델 크기 + 여유) → 모바일 데이터면 "Wi-Fi에서 받기"(기본) / "지금 받기" 선택 → 다운로드 → Ready → backlog 분석.
5. `나중에` → 제안 상태 `Declined` 저장. 기록 기능은 그대로. **자동으로 다시 제안하지 않는다** (확정, Settings에서 언제든 켤 수 있음).
6. 이미 Settings에서 켰다면 제안하지 않는다.

## 7. 기존 production 초안 처리

| 파일 (working tree, 미커밋) | 재사용 | 폐기 |
| --- | --- | --- |
| `related/SemanticRelatedRecordFinder.kt` | `selectWorthShowing` · `cosine` · `RelatedCandidate` · label 0/1/2 검증 · `RelatedValueJudge` / `TextEmbedder` 개념 · "모델 없음 → 전체 중단" 예외 개념 → M6-3에서 새 component로 옮긴다 | `SemanticRelatedRecordFinder`(저장 직후 요청-응답 · 매번 전체 기록을 읽어 embedding) · `RelatedRecordFinder` 구현 · `EmbeddingCandidateRetriever`(저장된 embedding · createdAt 조건으로 다시 작성) |
| `test/.../SemanticRelatedRecordFinderTest.kt` | selection · 정렬 · 실패 제외 · cosine 테스트 케이스 | finder 단위 테스트 구조 |
| `androidTest/.../RecordEditorRelatedFlowTest.kt` | 첫 기록 · 수정 · 저장 실패 전파 없음 확인 아이디어 | 저장 직후 Related Memories 이동 검증 전체 (흐름 제거) |
| `CLAUDE.md` 미커밋 변경 | experiments · docs 목록 갱신 | `SemanticRelatedRecordFinder` 설명 → 이번 설계 기준으로 다시 씀 |

**M6-1에서 처리함**: 초안의 순수 로직은 `related/RelatedSelection.kt` · `RelatedSelectionTest`로 분리했고, 나머지 초안 2개는 삭제했다. 커밋된 코드 중 M6-1에서 바뀐 것: `RelatedRecordFinder` · `NoOpRelatedRecordFinder` · `relatedIdsToShow` · `RecordEditorViewModel.awaitRelated` · `RelatedAfterSave` · `SaveSuccess(toRelated)` · `HOLD_RELATED` · `DebugRelatedRecordFinder`. `RelatedMemoriesRoute(relatedIds)`는 진입점 없이 남겨 두고 M6-4에서 `recordId`만 받도록 바꾼다.
그대로 쓰는 것: `RelatedMemoriesScreen` · `buildRelatedMemoriesState`(순서 유지 · 없는 id 건너뛰기) · `DateBasedResurfacedRecordSelector`("다시 만난 생각").

## 8. 네트워크 원칙 · 모델 호스팅 interface

원칙 변경: ~~INTERNET 권한 없음~~ → **사용자 기록은 기기 밖으로 전송하지 않는다. 인터넷은 AI 모델 등 정적 리소스 다운로드에만 쓴다.**
- 보내지 않는 것: 기록 원문 · 감정 · 카테고리 · 사진 · embedding · 판정 · 결과 · 기록 수 · 사용 통계 · 기기 / 사용자 식별자. analytics · crash 업로드 없음.
- 요청은 고정 URL GET만 (query · header · cookie에 사용자 정보 없음). 서버 · 로그인 없음 · `allowBackup=false` 유지.

`ModelSource` (제공자 미정, 구현만 교체):

```kotlin
data class ModelArtifact(
    val id: String,            // "qwen3.5-2b-q4_k_m"
    val version: String,       // 바뀌면 pipeline_version도 바뀜
    val fileName: String,
    val sizeBytes: Long,       // 1,274,396,992 (Qwen GGUF)
    val sha256: String,        // 20cb277f… — 앱에 고정, 서버가 주는 값을 믿지 않음
    val license: String,       // Apache-2.0 표기
)
interface ModelSource { fun urlsFor(artifact: ModelArtifact): List<String> }  // 1차 + 미러
```

호스팅 요구사항: HTTPS · 정적 파일 · 버전별 불변 URL · HTTP Range(이어받기) · 인증 · 쿠키 · 사용자 식별 없음 · 1.3 GB 이상 단일 파일 · 재배포가 라이선스상 가능 · 실패 시 미러로 대체 가능.
다운로드는 플랫폼 `DownloadManager` 기본안 (이어받기 · 네트워크 조건 · 진행 알림을 OS가 처리, 새 dependency 없음) → 받은 뒤 sha256 · 크기 검증 → 내부 저장소로 원자적 이동. 검증 실패 시 파일 삭제 · `Failed`.

**e5 모델 배포 (확정)**: 앱에 포함하지 않고 **e5와 Qwen을 하나의 선택형 모델 다운로드 흐름**으로 제공한다 (합계 약 1.4 GB). 기능을 켜기 전에는 embedding이 필요 없고, 거절한 사용자에게 크기 부담을 주지 않는다. 모델 상태(5절)는 두 파일을 묶은 하나의 상태로 다룬다 — 둘 다 검증돼야 `Ready`.

## 새 dependency · 변경 (구현 전 승인 대상)

Room Migration(1, 2) · WorkManager · DataStore(Preferences) · ONNX Runtime Android + XLM-R SentencePiece tokenizer · llama.cpp(NDK / CMake / JNI, arm64-v8a) · `INTERNET` 권한 · Manifest 주석 변경.
