# CLAUDE.md — thoughts-app

개인용 local-first 기록 앱 (Write → Connect → Rediscover). Android 네이티브 + Figma UI Builder 플러그인.

## 반드시 지킬 것

- **제품명은 `오늘문득`.** `Echo`는 내부 설계/작업용 가칭일 뿐이다. 사용자 UI, 리소스 문자열, 브랜드 요소에 절대 노출하지 않는다. 목록 화면 제목 `기록`은 기능명이다(브랜드 아님).
- 브랜드 핵심 경험: 기록 → 망각 → 재발견 → 연결 → 변화 인식. 표현 후보: `문득, 그때` · `문득, 예전의 생각이 떠올랐어요` · `다시 만난 생각`.
- **applicationId / package 미정.** 현재 값 `app.placeholder.journal`은 임시값이다. `dev.juna.echo`, `dev.juna.thoughts` 같은 값으로 임의로 확정하지 않는다. 이름이 정해지면 `scripts/rename-package.sh <new.package>`로 바꾼다.
- **Local-first.** 서버, 로그인, `INTERNET` 권한 없음. `allowBackup=false`.
- **과한 추상화 금지.** UseCase / Mapper / domain layer를 추가하지 않는다. 구조는 `data`(Room + Repository) / `ui`(Compose + ViewModel) / `related` / `util`.
- **Emotion은 stable key로 저장한다** (`calm`, `happy`, `excited`, `so_so`, `tired`, `anxious`, `sad`). ordinal이나 enum name으로 저장하지 않는다. `EmotionConverterTest`가 이를 고정한다.
- **Design Freeze (2026-10-02) 이후 새 디자인 방향이나 variant를 추가하지 않는다.** 결정 사항은 `docs/design-freeze.md`를 따른다.
- 환경 설치나 큰 환경 변경은 사용자 확인 없이 하지 않는다.

## 스택

Kotlin 2.1 · Compose (BOM 2025.01) · Material 3 · Room 2.6 (KSP) · Coroutines/Flow · Navigation Compose 2.8 (type-safe `@Serializable` routes) · AGP 8.7.3 · Gradle 8.11.1 · JDK 17 · minSdk 26 / target 35.
DI는 수동 (`JournalApplication.container` → `AppContainer`).

## 구조

```
app/src/main/java/app/placeholder/journal/
  JournalApplication.kt   AppContainer (database, repository, relatedFinder)
  MainActivity.kt
  data/        model(Emotion, RecordWithCategory) · db(Entity, Dao, AppDatabase, Converters, DefaultCategories) · RecordRepository
  related/     RelatedRecordFinder + NoOpRelatedRecordFinder (추상화만, 구현은 추후)
  ui/          theme(토큰, AppFonts) · components(EmotionMarker, Motion/JellyCurve, SaveSuccess, EmotionPicker, CategoryChips, RecordCard, Common: AppTopBar/AppFab) · splash(SplashIntro) · navigation · records(목록·캘린더, RecordsCalendar 순수 로직) · editor · detail
  util/        TimeFormat
design/figma-ui-builder/  Figma Plugin API 기반 UI Builder (Figma MCP 사용 안 함)
docs/        design-freeze.md · TODO.md · font.md (LINE Seed Sans KR) · licenses/
```

## 명령

```bash
./gradlew test                 # EmotionConverterTest, TimeFormatTest, MotionSpecTest, TypographyTest, RecordsCalendarTest
./gradlew connectedAndroidTest # RecordDaoTest (기기/에뮬레이터)
./gradlew assembleDebug

cd design/figma-ui-builder && npm install && npm run build && npm run typecheck && npm run validate
```

## 현재 상태

- M1 Foundation: 기록 목록 → 새 기록 → 감정/카테고리 선택 → Room 저장 → 목록 반영 → 상세 → 수정/삭제. API 35 에뮬레이터에서 검증 완료.
- M1.5 UI/브랜드 폴리싱: 앱 이름 오늘문득, Design Freeze 정렬(radius·app bar·FAB·empty state·editor panel·edge-to-edge), jelly squash & stretch, splash intro(~0.9s), 폰트 LINE Seed Sans KR 확정, 저장 성공 jelly("통!") 피드백. 실기기 확인 후 Figma/Design Freeze 반영 예정.
- M2 기록 영역: 목록/캘린더 전환, 월간 캘린더(emotion dot 최대 3개), 날짜 선택 → 그날 기록 → 상세, 카테고리 필터(목록·캘린더 공통). 상태는 `RecordListViewModel`(메모리), 날짜 계산은 `TimeFormat.dayKey` 하나로 통일. DAO/schema 변경 없음.

범위 밖 (다음 milestone): Home 재발견, Explore, Settings(내 감정 조각), Related Memories UI, 알림, 사진 picker, 온디바이스 AI(임베딩), 데이터 보호(SQLCipher + Keystore, 앱 잠금 등). 자세한 내용은 `docs/TODO.md`.

## 디자인 원칙 요약

"보기에는 차분하고, 만지면 말랑한 앱." Emotion = Color · Shape = 사용자 취향(내 감정 조각, 기본 동글동글) · Label = 명시적 의미 · Motion = 부드러운 촉감 피드백(idle 애니메이션 없음, Reduce Motion 존중).
전체 설계 문서(살아있는 문서): https://claude.ai/code/artifact/7a63ffb4-b841-4450-9a5a-8b9cb1ad804d
