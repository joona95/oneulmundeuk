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
- [ ] **M6 관련된 생각 찾기 (local inference 통합)** — 저장은 즉시, 분석은 background, 결과는 저장 후 Home / Record Detail에서 노출.
  - [ ] M6-0 재설계: working tree의 M5 초안(`SemanticRelatedRecordFinder` · 테스트 2개 · `CLAUDE.md` 변경)을 background · 결과 저장 구조에 맞게 다시 설계. 저장 직후 M4 Related Memories 화면의 역할(유지 / 결과 도착 후 노출 / 제거)과 debug finder 처리 결정.
  - [ ] Room Migration(1, 2): record embedding cache(모델 id · 버전 포함) + 관련 결과 table(대상 기록 · 관련 기록 · 순서 · judge 버전 · 생성 시각). 기록 수정 · 삭제 시 무효화 / 재분석.
  - [ ] local e5: `dragonkue/multilingual-e5-small-ko-v2` ONNX 변환 + Android tokenizer · runtime 선택. 앱 포함 여부(크기)를 결정. frozen 157쌍에서 Mac e5 run과 순위 일치 확인.
  - [ ] local Qwen: llama.cpp를 앱에 JNI로 통합(`examples/llama.android` 방식, NDK · arm64). PoC 구성 그대로(b10456 기준 · Q4_K_M · judge_v1 sha `553ab6176c0293f9` · temperature 0 · JSON schema · thinking off). frozen 157쌍을 앱 안에서 돌려 PoC 제품 지표(Bad .25 · Good@5 .64 · F7 1/4)와 비교.
  - [ ] background 실행: 저장 후 분석 작업 예약(기록 1건 단위 · 직렬 · 취소 / 재시도 · 프로세스 종료 대비). 실행 조건(충전 중 · 유휴 · 발열 · 배터리) 결정. 모델 메모리(peak RSS 약 2.7 GB) 고려.
  - [ ] 기능 제안 · 모델 다운로드: 기록 10개(초기값)에서 `관련된 생각 찾기` 제안 → 동의 시 약 1.3 GB 다운로드(이어받기 · sha256 검증 · 저장 공간 확인 · Wi-Fi 권장). 거절해도 기록 기능 정상, 재제안 정책 결정. 모델 호스팅 위치와 라이선스(Apache 2.0) 표기.
  - [ ] `INTERNET` 권한: 현재 "INTERNET 없음" 원칙과 충돌 → 모델 다운로드에만 쓰는 범위로 원칙 · 문서 갱신. 기록 원문 · inference 결과는 기기 밖으로 보내지 않는다.
  - [ ] Settings: `관련된 생각 찾기` ON/OFF(inference만 중단, 모델 유지) · `AI 모델 삭제`(별도) · 모델 상태(없음 / 다운로드 중 / 준비됨) 표시.
  - [ ] 노출: Home · Record Detail "이어지는 기록"을 저장된 결과로 표시(0개면 섹션 없음).
  - [ ] 실제 사용자 기록이 쌓인 뒤 정책(label 2만 · Top 30 · ≤ 5) 재평가. dataset v1.1로 더 튜닝하지 않는다.
- [ ] Explore (semantic search), reminders, photo picker (Photo Picker + copy into app storage → `photo_path`).
- [x] Editor: compact attribute panel while the keyboard is open (M1.5: labels and the big button hide; 저장 stays in the top bar).
- [ ] Editor: consider a one-line toolbar (emotion/category as a single row) if the compact panel still feels tight on small screens.
- [ ] Records List: search icon (Figma) arrives with Explore.
- [ ] Record Detail: "이어지는 기록" section (→ M6, 저장된 관련 결과로 표시) + "지금의 생각 덧붙이기" action (Figma, 미정).
- [ ] Motion polish candidates: list item fade + 4–6px rise for newly surfaced past records (Related / Home only).
- [ ] Dark theme decision.
