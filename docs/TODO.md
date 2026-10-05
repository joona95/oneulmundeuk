# TODO / next milestones

## Before release (must)
- [x] **App name**: `오늘문득` (M1.5) — `app_name`, splash intro.
- [ ] **applicationId / package**: still the placeholder `app.placeholder.journal` — decide, then `scripts/rename-package.sh`; also `rootProject.name`.
- [ ] **Launcher icon**: the jelly dot is a placeholder; design the 오늘문득 icon (adaptive + monochrome).
- [ ] **Data-protection milestone (personal records)**: records are currently stored unencrypted in app-private storage.
  - Evaluate SQLCipher (or equivalent) with a key held in Android Keystore; migration from the plain DB.
  - Encrypted export / backup format (backup is currently disabled via `allowBackup=false` + data extraction rules).
  - App lock (BiometricPrompt), notification content hidden on lock screen.
  - Threat model notes: device loss, shared device, backups, screenshots (FLAG_SECURE?).
- [x] **Font**: LINE Seed Sans KR (`docs/font.md`). Reflect in Figma + Design Freeze with the post-M4 sync.
- [ ] Font size: consider subsetting LINE Seed (≈6.8MB for Regular + Bold) before release.

## Status
- [x] M1 Foundation · M1.5 brand/UI polish · M2 Records/Calendar · M3 Home (visual direction final).
- [x] **M4 "문득, 그때"** — done, UX checked on device (debug finder: `src/debug/.../related/RelatedFinderFactory.kt`). No semantic retrieval / embedding / LLM (→ M5).
- [ ] Figma + Design Freeze sync after M4: Home + Save Success + Related Memories in one pass (until then the code is the reference for Home / Save Success).

## Next feature milestones
- [x] Bottom navigation: 홈 · 기록 (M3). 탐색 · 설정 tabs arrive with those screens.
- [ ] Settings › 내 감정 조각 (2-column grid) — persist `MarkerShape` (DataStore) and provide it via `AppTokens.markerShape`.
- [x] Records Calendar (M2): 목록/캘린더 전환, 월 이동, 최대 3개 emotion dot, 날짜별 기록, 카테고리 필터(목록·캘린더 공통).
- [ ] Records: remember the last view mode / filter (DataStore) — not in M2.
- [ ] Records: "+N" or a denser hint when a day has more than 3 records.
- [x] Home (M3): date · hero question · writing surface → Editor · "다시 만난 생각" (date-based: 1년/3개월/1개월 전 ±7일, ≥14일, section hidden when none) · 최근 기록 compact ×3 + 전체 보기. No brand header.
- [ ] Home resurfacing: semantic selector behind `ResurfacedRecordSelector` (later milestone).
- [x] **M4** Related Memories "문득, 예전의 생각이 떠올랐어요" (Thread B v3, no subtitle) — current record + up to 5 past records in finder order, after the save jelly + a ~500ms "저장했어요" hold, only when RelatedRecordFinder returns results (new, non-first records). No results: normal save flow, never an empty state. X only (홈으로 / 이어서 생각 남기기 removed until their meaning + relation persistence are decided).
- [x] **M5-0** 평가셋 + 지표: `experiments/related/` (15 queries · 131 pairs, label 0/1/2, `eval.py`).
- [x] **M5** 관련 기록 찾기 실험 (CLOSED): frozen v1.1 157쌍 · e5 / judge_v1·v2 / selector / listwise / reject filter / threshold / Claude probe · Android Qwen 2B PoC · Modal Qwen 4B.
  결정: **Room + local e5 + local Qwen3.5-2B (Q4_K_M)**, e5 Top 30 → judge_v1 → label 2만 → similarity DESC → ≤ 5 (`docs/m5-related-decision.md`).
- [x] **M6-0 설계**: `docs/m6-related-design.md` — 저장 직후 Related Memories 흐름 제거(화면은 Home에서 진입) · component 경계 · 무효화 규칙 · background lifecycle · 상태 모델 · 10개 제안 · 초안 처리 · 네트워크 원칙.
- [ ] **M6 관련된 생각 찾기 (local inference 통합)** — 저장은 즉시, 분석은 background, 결과는 저장 후 Home / Record Detail에서 노출. 단계마다 앱이 빌드 · 동작하는 상태로 끝낸다.
  - [x] M6-1 저장 흐름 분리: 저장 직후 Related Memories 이동 · `awaitRelated` · `HOLD_RELATED` · `RelatedRecordFinder`/NoOp · debug finder 제거, `RecordRepository` → `RecordChangeListener`(no-op) 추가, M5 초안의 순수 로직은 `related/RelatedSelection.kt`로 분리, 나머지 초안 삭제. 검증(2026-10-05, Mac): `./gradlew test assembleDebug assembleRelease` 통과. `connectedAndroidTest`(RecordDaoTest · RecordChangeListenerTest)는 M6-1 시점 미실행 → M6-2 검증에서 함께 통과.
  - [x] M6-2 Room Migration(1, 2): `record_embedding` · `related_analysis` · `related_judgment` (CASCADE) + 결과 파생 query + create/edit/delete 무효화 규칙 (DAO · 순수 로직 테스트). AI 없음.
    - 완료 (2026-10-05): `RelatedEntities` · `RelatedDao` · `MIGRATION_1_2` · `RelatedText` · `RelatedStore` · `RelatedInvalidator`(앱에서는 analysisEnabled=false), schema `2.json`. 검증(Mac): `./gradlew test assembleDebug assembleRelease` BUILD SUCCESSFUL · `./gradlew connectedAndroidTest` SM-S948N 16 tests 전부 PASS (RecordDaoTest · RecordChangeListenerTest · MigrationTest · RelatedPersistenceTest). 결과 노출: 분석이 PENDING · RUNNING이면 0개, DONE 후에만 다시 노출 (확정).
  - [x] M6-3 pipeline core (fake embedder · judge): `CandidateRetriever`(createdAt < 대상 · Top 30) · `selectWorthShowing` 이전 · `RelatedAnalyzer`(판정 캐시 · 재개 · 취소) · 대기열 처리 로직. 단위 테스트.
    - 완료 (2026-10-05): `TextEmbedder` · `RelatedValueJudge` · `RelatedPipeline` · `EmbeddingCodec` · `rankCandidates` · `RelatedAnalysisStorage`(+Room 구현) · `RelatedAnalyzer` (앱에는 미연결). 검증(Mac): `./gradlew test assembleDebug assembleRelease` BUILD SUCCESSFUL · `./gradlew connectedAndroidTest` SM-S948N 23 tests 전부 PASS.
  - [ ] M6-4 UI 노출 (debug 가짜 결과로 확인): Related Memories를 저장된 결과 기반 `RelatedMemoriesRoute(recordId)`로 (라벨 `이 생각에서`), Home 카드(작성 영역 아래 · `다시 만난 생각` 위) · Record Detail `이어지는 기록`. 카드 모양 디자인 확인 후.
    - 결정 (2026-10-05): UI는 pipeline version을 모른다 — active version은 repository / store 계층이 관리하고 조회 때 내부에서 사용. debug / fake 결과도 같은 경계. Home 카드 디자인은 실제 화면을 보며 결정.
  - [ ] M6-5 기능 상태 · Settings: DataStore(enabled · 제안 상태) · 모델 상태 machine · Settings `관련된 생각 찾기` ON/OFF · `AI 모델 삭제` · 10개 제안 sheet. (다운로드는 fake)
  - [ ] M6-6 모델 다운로드 (e5 + Qwen 하나의 흐름): `ModelArtifact` · `ModelSource`(제공자 미정) · DownloadManager · sha256 검증 · 원자적 이동 · 삭제. `INTERNET` 권한과 Manifest 주석을 새 원칙으로.
  - [ ] M6-7 local Qwen: llama.cpp JNI(NDK, arm64-v8a) · judge_v1 asset(sha 검사) · JSON schema · thinking off. 앱 안에서 frozen 157쌍 → PoC 제품 지표(Bad .25 · Good@5 .64 · F7 1/4)와 비교.
  - [ ] M6-8 local e5: ONNX 변환 · tokenizer · runtime. frozen 157쌍에서 Mac e5 run과 Top 30 순위 비교.
  - [ ] M6-9 WorkManager 연결: `RelatedWorker` · 실행 조건 · 발열 · 실행 예산 · 재시도. 실제 기기에서 저장 → 결과 노출까지 end-to-end (latency · 배터리 · RSS).
    - FAILED 재시도 정책: 최대 3회 · backoff. 현재 `requeue`는 attempts를 0으로 되돌리므로 attempts를 유지하는 재대기 경로를 이때 추가 (M6-3에서 보류).
  - [x] UI · 제품 결정 (2026-10-05): Home 카드 = 작성 영역 아래 · `다시 만난 생각` 위 · Detail 섹션 `이어지는 기록` · Related Memories 라벨 `이 생각에서` · 최초 분석 최신 10개 · 충전 조건 없음 · `나중에` 후 재제안 없음 · e5+Qwen 하나의 선택형 다운로드 · AI 결과 일괄 삭제 없음 (MVP). 남은 확인: Home 카드 모양(M6-4) · 제안 sheet 문구(M6-5).
  - [ ] 실제 사용자 기록이 쌓인 뒤 정책(label 2만 · Top 30 · ≤ 5) 재평가. dataset v1.1로 더 튜닝하지 않는다.
- [ ] Explore (semantic search), reminders, photo picker (Photo Picker + copy into app storage → `photo_path`).
- [x] Editor: compact attribute panel while the keyboard is open (M1.5: labels and the big button hide; 저장 stays in the top bar).
- [ ] Editor: consider a one-line toolbar (emotion/category as a single row) if the compact panel still feels tight on small screens.
- [ ] Records List: search icon (Figma) arrives with Explore.
- [ ] Record Detail: 관련 결과 섹션 (→ M6-4) + "지금의 생각 덧붙이기" action (Figma, 미정).
- [ ] Motion polish candidates: list item fade + 4–6px rise for newly surfaced past records (Related / Home only).
- [ ] Dark theme decision.
