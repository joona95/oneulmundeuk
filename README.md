# 오늘문득 — local-first Android app

Status: M1 · M1.5 · M2 (Records/Calendar) · M3 (Home) done. M4 "문득, 그때" (Related Memories after saving) done. Next: M5 (finding related records). See `CLAUDE.md` / `docs/TODO.md`.

Kotlin · Jetpack Compose · Material 3 · Room · Coroutines/Flow · Navigation Compose (type-safe).
There is no server and no login. Every record stays on the device. The app has no `INTERNET` permission, and records are excluded from cloud backup and device transfer.

> **App identity:** `applicationId` / `namespace` = `app.oneulmundeuk` (debug build: `app.oneulmundeuk.debug`). Display name 오늘문득. Signing · data rules: `docs/release-signing.md`. The internal project name never appears in the UI.

## Open in Android Studio

1. **File › Open** → select this folder (`thoughts-app`).
2. Gradle JDK: **17** (Settings › Build Tools › Gradle › Gradle JDK, e.g. the bundled JBR 17+).
3. Let Gradle sync. The wrapper is Gradle 8.11.1; dependency versions live in `gradle/libs.versions.toml`. Accepting newer versions from the upgrade assistant is fine.
4. Run the `app` configuration on an emulator or device (API 26+).

From the command line:

```bash
./gradlew test                    # JVM unit tests (EmotionConverter, TimeFormat, Motion, Typography, RecordsCalendar, ResurfacedRecordSelector)
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest   # Room DAO/repository test (needs an emulator/device)
```

## Structure (kept deliberately small)

```
app/src/main/java/app/oneulmundeuk/
├─ JournalApplication.kt     AppContainer (manual DI: database, repository, relatedFinder, resurfacer)
├─ MainActivity.kt
├─ data/
│  ├─ db/      AppDatabase (v1) · RecordEntity · CategoryEntity · RecordDao · CategoryDao · EmotionConverter · DefaultCategories
│  ├─ model/   Emotion (stable keys) · RecordWithCategory
│  └─ RecordRepository.kt
├─ related/    RelatedRecordFinder + NoOpRelatedRecordFinder (wired in M4; real finder in M5)
├─ resurface/  ResurfacedRecordSelector + DateBased… (Home "다시 만난 생각", date rule, no AI)
├─ ui/
│  ├─ theme/   Design Freeze tokens: Color · EmotionPalette · Type · Dimens · Shape · Theme (AppTheme.tokens)
│  ├─ components/ EmotionMarker (jelly shapes) · EmotionPicker · CategoryChips/Tag · RecordCard · Motion · Common
│  ├─ navigation/ Routes · AppNavHost · BottomBar (홈 · 기록)
│  ├─ home/ · records/ (list + calendar) · editor/ · detail/ · splash/   Screen + ViewModel per screen
│  └─ AppViewModelFactory.kt
└─ util/TimeFormat.kt        Korean date/time strings
```

There are no UseCase, Mapper or domain layers. The UI talks to `RecordRepository` directly.

## Data

| Table | Columns |
| --- | --- |
| `records` | `id` (UUID), `text`, `created_at`, `updated_at` (epoch ms), `emotion` (stable key), `category_id` → categories (ON DELETE SET NULL), `photo_path` (nullable, reserved) |
| `categories` | `id` (UUID), `name` (unique), `sort_order`, `created_at`. Seeded with 커리어 · 성장 · 개발 · 사이드 프로젝트 · 일상 · 관계 · 취미 |

Emotion keys are stored explicitly and never by ordinal or enum name: `calm` 평온 · `happy` 기쁨 · `excited` 설렘 · `so_so` 그냥 그래 · `tired` 지침 · `anxious` 불안 · `sad` 속상함. Unknown keys read back as `null`.

The Room schema JSON is exported to `app/schemas/` on build, so commit it to verify future migrations.

## Design → code

- **Background and color.** The background is Warm Ivory with white surfaces, charcoal text and neutral borders. Sage is used only for primary actions (save, FAB).
- **Emotion marker.** Emotion = color, shape = preference, label = meaning.
  - `EmotionMarker` ports the Figma jelly geometry. All 6 shapes exist; the default is 동글동글 (`AppTokens.markerShape`).
  - The Editor and Detail always show marker + label. The list shows a 14dp marker only.
  - Selected emotion state is a subtle halo, the marker one step larger (28 → 32dp) and a semibold label. There is no black ring.
- **Motion.** Selecting an emotion squishes and settles once. Cards give softly while pressed. Nothing animates while idle. Motion is skipped when the system "Remove animations" setting is on.

## Not implemented yet

Real related-record finding (M5), Explore, Settings (incl. 내 감정 조각 picker), notifications, photo picker, embedding/LLM, DB encryption. See `docs/TODO.md`.
