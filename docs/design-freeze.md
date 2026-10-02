# Design Freeze (2026-10-02)

전체 설계 문서(MVP 개념, IA, 플로우, 토큰, 화면 스펙)는 Claude Docs에 있다:
https://claude.ai/code/artifact/7a63ffb4-b841-4450-9a5a-8b9cb1ad804d

이 파일은 구현 시 따라야 할 **최종 결정**만 요약한다. 새 디자인 방향이나 variant는 추가하지 않는다.

## 최종 구성

Refinement v3 · Soft & Clean · Warm Ivory · Sage · Same Shape · 기본 모양 동글동글 · 2열 grid · Related Thread B · subtitle 없음 · Soft Interaction.

Figma Builder `DEFAULT_CONFIG`:
`{ variant:'soft', accent:'sage', background:'ivory', cardStyle:'border', density:'comfortable', homeVariant:'B', refine:'v3', relatedVariant:'threadB', markerVariant:'same', markerShape:'jelly', shapePicker:'grid', relatedSubtitle:'none' }`

## 원칙

- 보기에는 차분하고, 만지면 말랑한 앱. 사용자가 직접 쓴 텍스트가 항상 화면의 주인공.
- 기본 UI: Warm Ivory 배경, White surface, Charcoal 텍스트, 연한 뉴트럴 테두리, 넓은 여백, 제한적인 Sage(Primary action에만).
- 선택 상태(칩, 내비, 캘린더 선택일, 라디오)는 Charcoal.
- 피할 것: 캐릭터 중심, 화려한 감성 다이어리, 강한 파스텔 카드 배경, 생산성 도구 느낌, AI가 전면에 나오는 UI.

## 감정 마커 — Same Shape

- Emotion = Color · Shape = 사용자 취향 · Label = 명시적 의미 · Motion = 부드러운 촉감.
- 7개 감정과 stable key: 평온 `calm` · 기쁨 `happy` · 설렘 `excited` · 그냥 그래 `so_so` · 지침 `tired` · 불안 `anxious` · 속상함 `sad`.
- **내 감정 조각** (Settings, "어떤 모양으로 기록할까요?"): 동글동글(기본, jelly) · 하트 · 별 · 네모 · 조약돌 · 마름모. 모든 모양은 같은 처리(미세하게 불규칙한 실루엣, 잉크 테두리, 하이라이트). 2열 grid, 카드마다 이름 + 7색 미리보기, 선택은 연한 뉴트럴 테두리.
- 표시 규칙: Editor·Detail = 모양 + 색 + 이름 / List·Related·Rediscovery = 14px 마커 / Calendar = 색 점만.
- Editor 선택 상태: 검은 테두리 없음. 연한 surface halo + 마커 28→32px + 이름 semibold.
- 카드 배경 감정 tint는 Rediscovery 카드와 Detail 상단에만 5–8%.

## Related Memories — Thread B

- 흐름: 방금 남긴 생각 → 시간 구분("6개월 전") → 과거 기록 원문.
- 제목 "문득,\n예전의 생각이 떠올랐어요". subtitle 없음.
- 강한 timeline, 연결선, AI 설명, 유사도 점수 없음. "비슷한" 표현 쓰지 않음.
- 메타데이터 위계: 시간 구분(1차) · 날짜/카테고리(흐림) — 기록 원문이 가장 강하게.

## Copy 역할

| 상황 | 말투 | 문구 |
| --- | --- | --- |
| Home 재발견 | 다시 만남 | 다시 만난 생각 |
| 저장 직후 | 떠오름 | 문득, 예전의 생각이 떠올랐어요 |
| Explore | 찾아봄 | 과거의 나에게 물어보세요. |

## Motion

- 평소에는 정적, 만질 때만 말랑.
- 선택: squish → settle. 탭: 아주 작은 squish. 누름: soft give(0.98). 과거 기록 등장: fade + 4–6px rise, 1회.
- 금지: idle loop, sway, breathing, 큰 bounce, 장식용 motion.
- Reduce Motion(ANIMATOR_DURATION_SCALE == 0)에서는 상태 변화만.

## Responsive

360px 기준. 칩·버튼은 최소 높이만 고정, 글자 크기에 따라 커짐. 카테고리 태그는 최대 폭 120, 한 줄 말줄임. 긴 기록은 줄바꿈 또는 줄 수 제한. FAB은 하단 내비 위 고정.

## MVP Record 모델

text · createdAt · updatedAt · emotion? · categoryId? · photoPath?. 태그는 v1.1. 메모 가져오기는 개발용 seed만.
