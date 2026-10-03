# Font experiment — LINE Seed Sans KR (진행 중, 확정 아님)

> 이 문서는 실험 기록이다. 폰트가 확정되면 Figma와 `design-freeze.md`를 함께 업데이트한다. 그 전까지 Design Freeze의 typography 정의는 바뀌지 않는다.

## 목적

시스템 폰트보다 조금 더 둥글고 부드러운 한글 인상(깔끔함 70 / 부드러움 30)을 jelly와 함께 실제 기기에서 비교한다.

## 출처 · 라이선스

- 공식 배포처: LINE Seed (LY Corporation) — https://seed.line.me/index_kr.html
- 공식 다운로드: https://seed.line.me/src/images/fonts/LINE_Seed_Sans_KR.zip
- 라이선스: SIL Open Font License 1.1 — https://scripts.sil.org/OFL (공식 사이트 표기. 앱 번들 포함·상업적 사용 가능, 폰트 파일 단독 판매 불가, 라이선스 고지 필요)
- 서드파티 미러(npm 패키지, 폰트 다운로드 사이트 등)는 사용하지 않는다.

## 파일 배치 (Regular, Bold만)

공식 zip의 `LINESeedKR-Rg.ttf` / `LINESeedKR-Bd.ttf`를 내용 변경 없이 `res/font` 규칙(소문자·밑줄)에 맞는 이름으로 넣었다. Thin은 포함하지 않는다.

```
app/src/main/res/font/line_seed_kr_regular.ttf   LINE Seed Sans KR Regular (400), Version 1.000, 3.4MB
app/src/main/res/font/line_seed_kr_bold.ttf      LINE Seed Sans KR Bold (700), Version 1.000, 3.4MB
docs/licenses/LINE_Seed_Sans_KR-NOTICE.txt    출처·저작권·라이선스 고지
```

폰트 파일 메타데이터 확인 결과: Copyright © LY Corporation, "licensed under the SIL Open Font License, Version 1.1" (http://scripts.sil.org/OFL).
zip에 OFL 원문 파일이 있으면 `docs/licenses/`에 함께 넣는다.

## 동작

- `ui/theme/AppFonts.kt`의 `AppFonts.LineSeed`가 `R.font.line_seed_kr_*`를 참조한다. 파일이 없거나 이름이 바뀌면 빌드 에러가 난다 (조용한 fallback 없음).
- `AppTheme` → `appTypography(family)` → `MaterialTheme(typography = …)`로 Material3 Typography 전체 스타일에 적용된다. 앱 안에 다른 `MaterialTheme`이나 `fontFamily` override는 없다.
- `AppFonts.USE_LINE_SEED = false`로 바꾸면 파일을 지우지 않고 시스템 폰트와 비교할 수 있다.
- weight 매핑 (가짜 볼드 합성 없음): 400·500 → Regular, 600·700 → Bold.
- 임시 비교(TEMP font-check): 기록 목록의 `기록` 제목에만 LINE Seed를 직접 지정해 두었다. `USE_LINE_SEED = false`일 때 이 제목만 LINE Seed, 나머지는 시스템 폰트로 보인다. 폰트 결정 후 제거한다.

## 확인 메모 (2026-10-03)

- 이전 구현(assets 경로)도 Mac 빌드 산출물 기준으로 APK에 두 폰트가 들어 있었고 코드도 올바른 파일명을 참조했다.
- 폰트 파일: TrueType(glyf), 한글 11,172자 전체 포함, 앱 UI의 한글 107자 누락 없음 → glyph 부족으로 인한 fallback 아님.
- LINE Seed Sans KR의 한글은 Android 기본 한글 폰트(Noto Sans CJK KR)와 골격이 비슷한 고딕이라, 작은 크기에서 차이가 미묘하다. 차이가 잘 보이는 곳: 숫자(`10월 3일`), 라틴 문자, 글자의 시각적 크기(LINE Seed가 약간 작고 위로 앉음), 받침과 `ㄹ`의 둥근 정도.

## LINE Seed일 때만 적용되는 최소 조정

| 항목 | 기존 | 실험 |
| --- | --- | --- |
| label (13sp: 칩, 버튼, 날짜 header) weight | 600 | 500 → Regular |
| 모든 스타일 `lineHeightStyle` | 기본 | Center / Trim.None |
| size · lineHeight · letterSpacing | — | 변경 없음 |

title·heading·display는 600/700 그대로(Bold)라서 위계가 유지된다. 코드에서 직접 `FontWeight.SemiBold`를 준 곳(감정 선택 라벨, 시트 라벨, 상세 감정 pill)은 Bold로 보인다.
