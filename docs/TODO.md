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
- [ ] **Font**: add Pretendard (SIL OFL) to `res/font` and switch `AppFont` in `ui/theme/Type.kt` (system sans-serif fallback for now).

## Next feature milestones
- [ ] Bottom navigation (홈 · 기록 · 탐색 · 설정) once a second tab exists.
- [ ] Settings › 내 감정 조각 (2-column grid) — persist `MarkerShape` (DataStore) and provide it via `AppTokens.markerShape`.
- [ ] Records Calendar (color dots only).
- [ ] Home "다시 만난 생각" (passive rediscovery).
- [ ] Related Memories "문득, 예전의 생각이 떠올랐어요" (Thread B, no subtitle) — hook in `AppNavHost` after saving a new record.
- [ ] RelatedRecordFinder implementations (keyword baseline → on-device embedding). Embedding table arrives as Room Migration(1, 2).
- [ ] Explore (semantic search), reminders, photo picker (Photo Picker + copy into app storage → `photo_path`).
- [x] Editor: compact attribute panel while the keyboard is open (M1.5: labels and the big button hide; 저장 stays in the top bar).
- [ ] Editor: consider a one-line toolbar (emotion/category as a single row) if the compact panel still feels tight on small screens.
- [ ] Records List: search icon, 목록/달력 segmented control and category filter chips (Figma) arrive with Calendar / Explore.
- [ ] Record Detail: "이어지는 기록" section + "지금의 생각 덧붙이기" action (Figma) arrive with Related Memories.
- [ ] Motion polish candidates: list item fade + 4–6px rise for newly surfaced past records (Related / Home only).
- [ ] Dark theme decision.
