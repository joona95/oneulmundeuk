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

공식 zip의 TTF 중 Regular와 Bold 두 개만 아래 이름으로 넣는다. Thin은 쓰지 않는다.

```
app/src/main/assets/fonts/LINESeedSansKR-Regular.ttf
app/src/main/assets/fonts/LINESeedSansKR-Bold.ttf
docs/licenses/LINE_Seed_Sans_KR-OFL.txt   ← zip에 들어 있는 OFL 라이선스 파일 원문
```

zip 안의 원래 파일명이 다르면 위 이름으로 바꿔서 넣는다 (코드는 이 이름만 찾는다).

## 동작

- `ui/theme/AppFonts.kt`가 실행 시 `assets/fonts/`에 두 파일이 있는지 확인한다.
  - 있으면 LINE Seed Sans KR, 없으면 시스템 sans-serif (빌드·실행은 항상 됨).
  - `AppFonts.USE_LINE_SEED = false`로 바꾸면 파일을 지우지 않고 시스템 폰트와 비교할 수 있다.
- weight 매핑 (가짜 볼드 합성 없음): 400·500 → Regular, 600·700 → Bold.

## LINE Seed일 때만 적용되는 최소 조정

| 항목 | 기존 | 실험 |
| --- | --- | --- |
| label (13sp: 칩, 버튼, 날짜 header) weight | 600 | 500 → Regular |
| 모든 스타일 `lineHeightStyle` | 기본 | Center / Trim.None |
| size · lineHeight · letterSpacing | — | 변경 없음 |

title·heading·display는 600/700 그대로(Bold)라서 위계가 유지된다. 코드에서 직접 `FontWeight.SemiBold`를 준 곳(감정 선택 라벨, 시트 라벨, 상세 감정 pill)은 Bold로 보인다.
