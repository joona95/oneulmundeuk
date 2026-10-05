# CLAUDE.md — thoughts-app

개인용 local-first 기록 앱 (Write → Connect → Rediscover). Android 네이티브 + Figma UI Builder 플러그인.

## 반드시 지킬 것

- **제품명은 `오늘문득`.** `Echo`는 내부 설계/작업용 가칭일 뿐이다. 사용자 UI, 리소스 문자열, 브랜드 요소에 절대 노출하지 않는다. 화면 title: Home은 page title 없이 Hero 질문이 title 역할, Records는 AppTopBar title `기록`(하단 navigation label도 `기록`), Explore는 page title 없이 Hero "과거의 나에게 물어보세요."가 title 역할, Settings는 page title `설정`.
- 브랜드 핵심 경험: 기록 → 망각 → 재발견 → 연결 → 변화 인식. 표현 후보: `문득, 그때` · `문득, 예전의 생각이 떠올랐어요` · `다시 만난 생각`.
- **applicationId / package 미정.** 현재 값 `app.placeholder.journal`은 임시값이다. `dev.juna.echo`, `dev.juna.thoughts` 같은 값으로 임의로 확정하지 않는다. 이름이 정해지면 `scripts/rename-package.sh <new.package>`로 바꾼다.
- **Local-first. 사용자 기록은 기기 밖으로 전송하지 않는다.** 원문 · 감정 · 카테고리 · 사진 · embedding · 판정 · 결과 · 사용 통계 · 식별자 모두. 인터넷은 AI 모델 등 정적 리소스 다운로드(고정 URL GET)에만 쓴다. 서버 · 로그인 · analytics 없음. `allowBackup=false`. (현재 Manifest에는 아직 `INTERNET` 권한이 없다 — 모델 다운로드 단계(M6-6)에서 추가)
- **과한 추상화 금지.** UseCase / Mapper / domain layer를 추가하지 않는다. 구조는 `data`(Room + Repository) / `ui`(Compose + ViewModel) / `related` / `resurface` / `util`.
- **Emotion은 stable key로 저장한다** (`calm`, `happy`, `excited`, `so_so`, `tired`, `anxious`, `sad`). ordinal이나 enum name으로 저장하지 않는다. `EmotionConverterTest`가 이를 고정한다.
- **Design Freeze (2026-10-02) 이후 새 디자인 방향이나 variant를 추가하지 않는다.** 결정 사항은 `docs/design-freeze.md`를 따른다.
- 환경 설치나 큰 환경 변경은 사용자 확인 없이 하지 않는다.

## 스택

Kotlin 2.1 · Compose (BOM 2025.01) · Material 3 · Room 2.6 (KSP) · Coroutines/Flow · Navigation Compose 2.8 (type-safe `@Serializable` routes) · AGP 8.7.3 · Gradle 8.11.1 · JDK 17 · minSdk 26 / target 35.
DI는 수동 (`JournalApplication.container` → `AppContainer`).

## 구조

```
app/src/main/java/app/placeholder/journal/
  JournalApplication.kt   AppContainer (database, repository, relatedFinder, resurfacer)
  MainActivity.kt
  data/        model(Emotion, RecordWithCategory) · db(Entity, Dao, AppDatabase v2, Migrations, Converters, DefaultCategories, Related*: record_embedding · related_analysis · related_judgment) · RecordRepository
  related/     RecordChangeListener(저장 · 수정 · 삭제 커밋 후 RecordRepository가 호출) → RelatedInvalidator(text version이 바뀐 캐시만 무효화 · 분석 대기열, 앱에서는 analysisEnabled=false) · RelatedText(정규화 + hash) · RelatedStore(결과: label 2 · 현재 text · ≤5) · RelatedSelection(M5 정책 순수 로직). inference는 아직 없음 (docs/m6-related-design.md). 저장은 related 결과를 기다리지 않는다
  resurface/   ResurfacedRecordSelector + DateBased… — Home "다시 만난 생각": "시간이 지나서" 다시 만나는 기록 (날짜 규칙, AI 없음)
  ui/          theme(토큰, AppFonts) · components(EmotionMarker, Motion/JellyCurve, SaveSuccess, EmotionPicker, CategoryChips, RecordCard, Common: AppTopBar/AppFab) · splash(SplashIntro) · navigation(홈·기록 하단 탭) · home · related(Related Memories) · records(목록·캘린더, RecordsCalendar 순수 로직) · editor · detail
  util/        TimeFormat
design/figma-ui-builder/  Figma Plugin API 기반 UI Builder (Figma MCP 사용 안 함)
experiments/  M5 실험 (앱 빌드와 무관): related/(frozen 평가셋 · e5 · judge) · android-qwen-poc/(기기 Qwen 2B) · modal-qwen-4b/(서버 4B)
docs/        design-freeze.md · TODO.md · font.md (LINE Seed Sans KR) · m5-related-decision.md · m6-related-design.md · licenses/
```

## 명령

```bash
./gradlew test                 # EmotionConverterTest, TimeFormatTest, MotionSpecTest, TypographyTest, RecordsCalendarTest, ResurfacedRecordSelectorTest, RelatedMemoriesTest, RelatedSelectionTest, RelatedTextTest
./gradlew connectedAndroidTest # RecordDaoTest, RecordChangeListenerTest, MigrationTest, RelatedPersistenceTest (기기/에뮬레이터)
./gradlew assembleDebug

cd design/figma-ui-builder && npm install && npm run build && npm run typecheck && npm run validate
```

## 현재 상태

- M1 Foundation: 기록 목록 → 새 기록 → 감정/카테고리 선택 → Room 저장 → 목록 반영 → 상세 → 수정/삭제. API 35 에뮬레이터에서 검증 완료.
- M1.5 UI/브랜드 폴리싱: 앱 이름 오늘문득, Design Freeze 정렬(radius·app bar·FAB·empty state·editor panel·edge-to-edge), jelly squash & stretch, splash intro(~1.5s), 폰트 LINE Seed Sans KR 확정, 저장 성공 jelly("통!") 피드백.
- M2 기록 영역: 목록/캘린더 전환, 월간 캘린더(emotion dot 최대 3개), 날짜 선택 → 그날 기록 → 상세, 카테고리 필터(목록·캘린더 공통). 상태는 `RecordListViewModel`(메모리), 날짜 계산은 `TimeFormat.dayKey` 하나로 통일. DAO/schema 변경 없음.
- M3 Home (완료, visual 확정): 시작 화면 Home, 하단 탭(홈·기록). 브랜드 헤더 없음(앱 이름은 splash) → 오늘 날짜(작게) → Hero "오늘은 어떤 생각이 / 문득 떠올랐나요?" → 큰 작성 영역 "지금 떠오르는 생각을 남겨보세요…"(입력 아님, 탭하면 기존 Editor) → "다시 만난 생각"(설명문 + history 아이콘·상대 시간 + 원문 + 감정 마커·날짜·카테고리; 14일 이상 지난 기록 중 1년 → 3개월 → 1개월 전 ±7일, 결정적 선택, 없으면 섹션 숨김) → "최근 기록" compact 카드 3개 + 전체 보기. 같은 Room Flow 재사용.
- M4 "문득, 그때" (완료, 실기기 UX 확인): 새 기록 저장(첫 기록·수정 제외) 직후 `RelatedRecordFinder`(limit 5)를 jelly와 동시에 실행. 결과 0개 → 기존 저장 흐름, 1~5개 → jelly 600ms + "저장했어요" 약 500ms 후 Related Memories(진입 850ms fade + 12dp rise, Reduce Motion은 짧은 fade)(Thread B v3: headline · 방금 남긴 생각 card · Memory Entry 최대 5개, finder 순서 유지, 순위/점수/AI 설명 없음, 하단 버튼 없음, X로 시작 탭 복귀, 과거 기록 → Detail → back → Related). production finder는 M5 전까지 NoOp. Detail "이어지는 기록"·관계 persistence 없음.
  → **M6-1에서 저장 직후 Related Memories 자동 이동을 제거**했다. 저장 피드백은 항상 같은 길이로 끝나고 이전 화면으로 돌아간다. Related Memories 화면은 남아 있으며 M6-4에서 Home 카드로 진입한다.
- M5 관련 기록 찾기 실험 (CLOSED, `experiments/`, `docs/m5-related-decision.md`): 결정 = Room + local e5 + local Qwen3.5-2B Q4_K_M, e5 Top30 → judge_v1 → label 2만 → similarity DESC → ≤5.
- 진행 중: **M6 관련된 생각 찾기** (M6-0 설계 · M6-1 · M6-2 완료, 다음 M6-3) (`docs/m6-related-design.md`, 단계는 `docs/TODO.md`). 저장 직후 Related Memories 자동 이동은 없애고, 분석은 background, 결과는 Home · Record Detail에서 노출. 모델은 기록 10개에서 제안 후 동의 시 다운로드.
- Figma / Design Freeze 동기화는 M4 이후 Home + Save Success + Related Memories를 한 번에 한다(그 전까지 Home·Save Success는 코드가 기준).

M4 이후: Explore, Settings(내 감정 조각), 알림, 사진 picker, 온디바이스 AI(임베딩), 데이터 보호(SQLCipher + Keystore, 앱 잠금 등). 자세한 내용은 `docs/TODO.md`.

## 디자인 원칙 요약

"보기에는 차분하고, 만지면 말랑한 앱." Emotion = Color · Shape = 사용자 취향(내 감정 조각, 기본 동글동글) · Label = 명시적 의미 · Motion = 부드러운 촉감 피드백(idle 애니메이션 없음, Reduce Motion 존중).
전체 설계 문서(살아있는 문서): https://claude.ai/code/artifact/7a63ffb4-b841-4450-9a5a-8b9cb1ad804d
