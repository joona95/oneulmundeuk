# TODO / next milestones

## Before release (must)
- [x] **App name**: `오늘문득` (M1.5) — `app_name`, splash intro.
- [x] **applicationId / package**: `app.oneulmundeuk` (debug `app.oneulmundeuk.debug`), release signing via `~/.gradle/gradle.properties` — `docs/release-signing.md`. (`rootProject.name` stays `thoughts-app`, internal only.)
- [ ] Play 출시 준비: upload key 생성 · Play App Signing에 release key를 app signing key로 등록 (`docs/release-signing.md`).
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
- [x] Bottom navigation: 홈 · 기록 (M3) · 탐색 (Explore MVP) · 설정 (Settings MVP).
- [x] Settings MVP (검증: `clean test assembleDebug assembleRelease` PASS · 기기 수동 확인 일부 · `connectedAndroidTest` 미실행 — `SettingsTest` 등 instrumented test는 아직 실행 안 됨): 카테고리(active chip · 카테고리 관리: 추가 · 삭제=archive) · 내 감정 조각(2열, DataStore `settings.marker_shape` → `AppTokens.markerShape`, 캘린더 dot 제외) · 다시 만나기 알림 ON/OFF · 관련된 생각 ON/OFF + 상태(OFF / MODEL_NOT_DOWNLOADED / DOWNLOADING / READY)
  - [ ] 다시 만나기 알림 실제 발송: WorkManager · 알림 권한/채널 · 시간 기반 resurfacing("다시 만난 생각" 계열, semantic AI 아님)
  - [ ] 관련된 생각: 모델 다운로드 · 설치 여부(`relatedModelInstalled`) · DOWNLOADING/READY 연결 · 설정값으로 분석 gate(지금은 runtime 미변경) · 모델 삭제
  - [ ] 10번째 기록 + Home 복귀 시 `관련된 생각 찾기` bottom sheet 1회
  - [ ] 카테고리 순서 변경 · 이름 바꾸기 · restore (Figma에는 있음, MVP 제외)
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
  - [ ] M6-4 UI 노출 (debug 가짜 결과로 확인): 저장 직후 grace window(1.5초 = 일반 저장 피드백 길이) 안에 결과가 있으면 `저장했어요` + jelly를 1.5초 다 보여주고 0.5초 연출 pause(`RELATED_NAV_DELAY_MS`) 뒤(결과 없으면 1.5초에 바로 복귀) Related Memories로 자동 이동(overlay에 `문득` 문구 · CTA 없음; Thread B: target만 rounded card, 과거 기록은 divider로 나뉜 thread item, 라벨 `이 생각에서`, 기록 최대 12줄, `RelatedMemoriesRoute(recordId)`) · Record Detail `이어지는 기록`(최대 4줄). Home은 시간 기반 `다시 만난 생각`만(semantic 카드 제거), Home `최근 기록` 본문은 기록 목록과 같은 최대 3줄. 구현됨(미커밋) — Mac 빌드 · 테스트 · 실기기 확인 후 체크.
    - 결정 (2026-10-05): UI는 pipeline version을 모른다 — active version은 repository / store 계층이 관리하고 조회 때 내부에서 사용. debug / fake 결과도 같은 경계. Home 카드 디자인은 실제 화면을 보며 결정.
  - [ ] M6-5 기능 상태 · Settings: DataStore(enabled · 제안 상태) · 모델 상태 machine · Settings `관련된 생각 찾기` ON/OFF · `AI 모델 삭제` · 10개 제안 sheet. (다운로드는 fake)
  - [~] M6-6 모델 다운로드 기반 (미커밋): `related/model` — `RelatedModels`(e5 + Qwen manifest) · `ModelInstaller`(noBackupFilesDir/models · `.part` 이어받기 · size + sha256 · marker · 원자적 rename · 삭제 · 복구) · `SemanticGate`(ON AND Ready) · Settings 상태 UX(모델 받기 · 진행률 · 준비됨 · AI 모델 삭제). 방식: in-app 다운로드(DownloadManager 아님 — 앱 내부 저장소에 직접 쓰고 복사 없이 rename).
    - [ ] 모델 호스트 확정 → `ModelSource.Https` URL · HTTPS `ModelFetcher`(Range) 구현 · `INTERNET` + `ACCESS_NETWORK_STATE` 권한 · Manifest 주석
    - [ ] e5 Android artifact(ONNX 변환 · tokenizer) 확정 → 파일명 · 크기 · sha256 (M6-8). 그 전까지 번들은 READY가 될 수 없음
    - [ ] 모바일 데이터에서 "지금 받기" 동의(MVP는 Wi-Fi 전용) · 다운로드 취소 버튼
  - [ ] M6-7 local Qwen: llama.cpp JNI(NDK, arm64-v8a) · judge_v1 asset(sha 검사) · JSON schema · thinking off. 앱 안에서 frozen 157쌍 → PoC 제품 지표(Bad .25 · Good@5 .64 · F7 1/4)와 비교.
  - [ ] M6-8 local e5: ONNX 변환 · tokenizer · runtime. frozen 157쌍에서 Mac e5 run과 Top 30 순위 비교.
  - [ ] M6-9 WorkManager 연결: `RelatedWorker` · 실행 조건 · 발열 · 실행 예산 · 재시도. 실제 기기에서 저장 → 결과 노출까지 end-to-end (latency · 배터리 · RSS).
  - [ ] M6-9 늦게 끝난 결과 UX 결정: grace window 뒤 DONE을 어떻게 알릴지 (local notification / 다음 앱 진입 one-shot / 조용한 inbox · indicator). Home 상시 semantic 카드는 쓰지 않는다. 실제 모델 latency를 본 뒤 grace window 길이도 다시 확인.
    - FAILED 재시도 정책: 최대 3회 · backoff. 현재 `requeue`는 attempts를 0으로 되돌리므로 attempts를 유지하는 재대기 경로를 이때 추가 (M6-3에서 보류).
  - [x] UI · 제품 결정 (2026-10-05): Home 카드 = 작성 영역 아래 · `다시 만난 생각` 위 · Detail 섹션 `이어지는 기록` · Related Memories 라벨 `이 생각에서` · 최초 분석 최신 10개 · 충전 조건 없음 · `나중에` 후 재제안 없음 · e5+Qwen 하나의 선택형 다운로드 · AI 결과 일괄 삭제 없음 (MVP). 남은 확인: 제안 sheet 문구(M6-5). (Home semantic 카드는 M6-4 실기기 확인 후 폐기 — 저장 직후 노출로 변경)
  - [ ] 실제 사용자 기록이 쌓인 뒤 정책(label 2만 · Top 30 · ≤ 5) 재평가. dataset v1.1로 더 튜닝하지 않는다.
- [ ] Explore MVP (미커밋): Figma `Explore` / `Semantic Search Results` 구조 — hero · subtitle · 검색창 · 기기 안 검색 안내 · `이렇게 물어볼 수 있어요`(결정적 template, 탭 → 바로 검색) · `자주 등장한 주제`(기록이 있는 Category, 많은 순 → 기록 탭 해당 카테고리 필터). 결과 화면: 검색창 · `N개의 기록을 찾았어요` · 관련도순/시간순(같은 결과 집합) · RecordCard → Detail.
  - **검색 production 결정 (experiments/search S1 · S2 · Thought Index PoC, 2026-10):** `search/ExploreSearch` = e5 only + 결정적 날짜 routing (`search/ExploreQuery`, s2_temporal.py 이식: 작년 이맘때 · 지난달 · 올해 초 · 작년 여름 · 올봄/올해 봄 · 작년/올해). 시간 표현 + 의미 → 날짜 필터 후 e5 순, 시간 표현만 → 날짜 순(모델 불필요), 그 외 → e5 Top10. LLM SearchJudge(S1에서 e5보다 나쁨) · LLM 추천 질문 생성(PoC grounding 4/4 실패) · Thought Index(채택 안 함)는 production에 없다.
  - 추천 질문 = 날짜 template(해당 기간 기록 ≥ 2) 최대 2 + category template(기록 ≥ 3 · 14일 이상에 걸침, e5 있을 때만) → 최대 4, 부족하면 채우지 않음. aggregation 질문 추천 금지.
  - category 추천 질문 클릭 = category hint: 그 category의 e5 Top 3 → 전체 e5 순서로 나머지(중복 제외) → 최대 10. score 보정 · strict filter 없음. 검색창에 직접 입력한 같은 문장에는 적용하지 않는다. 날짜 routing은 그대로.
  - **Explore 검색 정책 실험은 여기서 종료 (2026-10).** 남은 검색 개선(no-result threshold, topic recurring/change, category hint 조정 등)은 실제 e5 / Qwen을 연결해 실사용한 뒤 결정한다.
- [x] Explore 검색 실험 종료: S1 (e5 only가 최선, SearchJudge 제거) · S2 (날짜 routing 채택, multi-record selector는 보류) · Thought Index PoC (채택 안 함). 결과는 `experiments/search/results-*.md` (frozen).
- [ ] Explore production e5: 실제 e5 `embedQuery`("query: " prefix) 연결(M6-7 runtime과 함께) · 검색 latency 측정 · no-result(유사도 threshold) 정책 · 결과 카드 match highlight(Figma).
- [ ] Explore topic이 명시된 recurring / change ("잠 못 드는 밤이 반복됐던 때", "달리기에 대한 마음이 변했나?"): 실제 Qwen runtime이 생긴 뒤 topic 추출 → e5 high-recall 후보 → 작성일 순 multi-record Qwen 1회 (`ExploreQuery`에 variant 추가). 그 전까지 일반 e5. topic 없는 broad aggregation / change ("자꾸 반복되는 걱정", "생각을 바꾼 주제")는 MVP에서 특별 처리하지 않는다 (semantic clustering / index 테이블 추가하지 않음).
- [ ] reminders, photo picker (Photo Picker + copy into app storage → `photo_path`).
- [x] Editor: compact attribute panel while the keyboard is open (M1.5: labels and the big button hide; 저장 stays in the top bar).
- [x] 카테고리 정책 (미커밋): 신규 설치 기본 회사 · 일상 · 취미 · 관계 · 기타, 기존 설치는 그대로. 삭제 = archive(`categories.archived_at`, Room v3 `MIGRATION_2_3`) — 새 기록 선택지에서만 제외, Edit은 현재 값 유지, Records 필터는 사용 기록이 있으면 표시, Explore/검색 변경 없음 (`CategoryPolicy`)
  - [x] 카테고리 관리 UI(추가 · 삭제 dialog) — Settings MVP
  - [x] archive된 이름과 같은 새 카테고리: 생성하지 않고 "예전에 사용했던 카테고리 이름이에요" 안내 (restore 정책은 미정)
- [ ] Editor: consider a one-line toolbar (emotion/category as a single row) if the compact panel still feels tight on small screens.
- [ ] Records List: search icon (Figma) arrives with Explore.
- [ ] Record Detail: 관련 결과 섹션 (M6-4 구현) + "지금의 생각 덧붙이기" action (Figma, 미정).
- [ ] Motion polish candidates: list item fade + 4–6px rise for newly surfaced past records (Related / Home only).
- [ ] Dark theme decision.
