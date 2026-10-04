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
- [ ] **M4 "문득, 그때"** — next. Related past records right after saving (see Related Memories below). No semantic retrieval / embedding / LLM yet.
- [ ] Figma + Design Freeze sync after M4: Home + Save Success + Related Memories in one pass (until then the code is the reference for Home / Save Success).

## Next feature milestones
- [x] Bottom navigation: 홈 · 기록 (M3). 탐색 · 설정 tabs arrive with those screens.
- [ ] Settings › 내 감정 조각 (2-column grid) — persist `MarkerShape` (DataStore) and provide it via `AppTokens.markerShape`.
- [x] Records Calendar (M2): 목록/캘린더 전환, 월 이동, 최대 3개 emotion dot, 날짜별 기록, 카테고리 필터(목록·캘린더 공통).
- [ ] Records: remember the last view mode / filter (DataStore) — not in M2.
- [ ] Records: "+N" or a denser hint when a day has more than 3 records.
- [x] Home (M3): date · hero question · writing surface → Editor · "다시 만난 생각" (date-based: 1년/3개월/1개월 전 ±7일, ≥14일, section hidden when none) · 최근 기록 compact ×3 + 전체 보기. No brand header.
- [ ] Home resurfacing: semantic selector behind `ResurfacedRecordSelector` (later milestone).
- [ ] **M4** Related Memories "문득, 예전의 생각이 떠올랐어요" (Thread B, no subtitle) — after the save-success jelly ("통!"), only when RelatedRecordFinder returns results. With no results: normal save flow, never an empty state. No embedding / LLM in M4.
- [ ] RelatedRecordFinder implementations (keyword baseline → on-device embedding). Embedding table arrives as Room Migration(1, 2).
- [ ] Explore (semantic search), reminders, photo picker (Photo Picker + copy into app storage → `photo_path`).
- [x] Editor: compact attribute panel while the keyboard is open (M1.5: labels and the big button hide; 저장 stays in the top bar).
- [ ] Editor: consider a one-line toolbar (emotion/category as a single row) if the compact panel still feels tight on small screens.
- [ ] Records List: search icon (Figma) arrives with Explore.
- [ ] Record Detail: "이어지는 기록" section + "지금의 생각 덧붙이기" action (Figma) arrive with Related Memories.
- [ ] Motion polish candidates: list item fade + 4–6px rise for newly surfaced past records (Related / Home only).
- [ ] Dark theme decision.
