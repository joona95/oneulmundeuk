# TODO / next milestones

## Before release (must)
- [ ] **App name + applicationId**: replace the placeholder `app.placeholder.journal` (`scripts/rename-package.sh`), `rootProject.name`, `app_name`, launcher icon.
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
- [ ] Editor: collapse the attribute sheet to a one-line toolbar while the keyboard is open.
- [ ] Dark theme decision.
