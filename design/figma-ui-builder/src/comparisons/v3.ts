// v3 comparison boards: emotion marker A/B (with in-context usage), motion concept, Related copy.
import { add, cardSurface, ellipse, frame, para, text } from '../core/layout';
import type { Library } from '../core/library';
import { EMOTIONS, EMOTION_LABEL, MARKER_SHAPES, MARKER_STYLES, sameShapeMarker, transformed, wrapSvg } from '../emotions';
import type { MarkerShapeKey } from '../emotions';
import { SettingsScreen } from '../screens/settings';
import { RecordEditorScreen } from '../screens/write';
import { RecordsListScreen } from '../screens/records';
import type { Emotion, EmotionStyleDef } from '../emotions';
import { RECORDS } from '../data/sample';
import { RELATED_COPY } from '../data/copy';
import type { RelatedCopyKey } from '../data/copy';
import { relatedThreadBV3 } from '../screens/write';
import { recordCard } from '../screens/shared';
import type { ScreenContext } from '../screens/shared';
import type { Theme } from '../tokens/resolve';
import type { BuilderConfig } from '../types';
import type { ComparisonBoard } from './index';

type Pair = { t: Theme; lib: Library };

function svg(t: Theme, st: EmotionStyleDef, e: Emotion, px: number, name: string, wrap?: (inner: string) => string) {
  const inner = st.draw(e, t.color.emotion[e]);
  const n = figma.createNodeFromSvg(wrapSvg(wrap ? wrap(inner) : inner, px));
  n.name = name;
  n.fills = [];
  return n;
}

function note(t: Theme, parent: FrameNode, chars: string) {
  add(parent, para(t, 'bodySmall', chars, { name: 'note', color: t.color.textSecondary }), { fillW: true });
}

/** Emotion Comparison Sheet — Variant A (same shape) vs B (mixed shapes), shapes and real usage. */
export function markerComparison(base: BuilderConfig, same: Pair, mixed: Pair): ComparisonBoard {
  const t = same.t;
  const board = frame({ name: 'Emotion Marker Comparison', width: 1240, gap: t.space.xxxl, pad: t.space.xxxl, fill: t.color.surface, radius: t.radius.xxl });
  add(board, text(t, 'title', 'Emotion Marker — B · Mixed Soft Shapes 채택 (A는 참고)', { name: 'title' }));
  note(t, board, '마커는 감정을 설명하는 아이콘이 아니라 기록에 붙는 작은 색 조각이에요. 의미는 색 + 이름이 전달하고, 모양은 앱의 시각 언어예요. 고를 때는 항상 이름이 함께 보이고(Editor·Detail), 훑어볼 때는 작은 마커만(List·Related) 또는 점만(Calendar) 보여요.');
  const variants: [string, string, EmotionStyleDef, Pair][] = [
    ['A · Same Shape', '모든 감정이 같은 말랑한 모양, 색만 다름 — 가장 단순하고 차분함. 감정을 메타데이터로 다루는 방향과 잘 맞음.', MARKER_STYLES.same, same],
    ['B · Mixed Shapes', '여러 말랑한 모양 (원·하트·별·둥근 사각형·조약돌·다이아몬드·둥근 삼각형). 모양에 감정 의미는 없음 — 색 외의 구분 단서와 motion의 재미를 더함.', MARKER_STYLES.mixed, mixed],
  ];
  for (const [name, desc, st, pair] of variants) {
    const block = add(board, frame({ name, gap: t.space.lg }), { fillW: true });
    add(block, text(t, 'heading', name, { name: 'variant' }));
    note(t, block, desc);
    const row = add(block, frame({ name: 'markers', dir: 'H', gap: t.space.xl }));
    for (const e of EMOTIONS) {
      const col = add(row, frame({ name: e, gap: t.space.xs, align: 'CENTER' }));
      add(col, svg(t, st, e, 48, `${e}/48`));
      const sizes = add(col, frame({ name: 'sizes', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
      for (const px of [t.size.emotionMd, t.size.emotionSm, t.size.emotionMarker]) add(sizes, svg(t, st, e, px, `${e}/${px}`));
      add(sizes, ellipse(t.size.emotionXs, t.color.emotion[e].fill, `${e}/dot`));
      add(col, text(t, 'caption', EMOTION_LABEL[e], { name: 'label', color: t.color.textSecondary }));
    }
    // In context: picker (shape + color + label), list (tiny marker), calendar (dot).
    const ctxRow = add(block, frame({ name: 'in-context', dir: 'H', gap: t.space.xl }), { fillW: true });
    const sctx: ScreenContext = { t: pair.t, lib: pair.lib, config: { ...base, refine: 'v3' } };

    const picker = add(ctxRow, cardSurface(frame({ name: 'Editor picker', gap: t.space.sm, pad: t.space.lg, width: 380 }), pair.t));
    add(picker, text(t, 'caption', 'Editor · 고를 때 = 모양 + 색 + 이름', { name: 'cap', color: t.color.textTertiary, weight: 600 }));
    const opts = add(picker, frame({ name: 'options', dir: 'H', justify: 'SPACE_BETWEEN' }), { fillW: true });
    for (const e of EMOTIONS) {
      add(opts, pair.lib.instance('Emotion Option', { variant: { state: e === 'joy' ? 'selected' : 'default' }, swap: { emotion: pair.lib.emotion(e, e === 'joy' && pair.t.v3 ? 'lg' : 'md') }, text: { label: EMOTION_LABEL[e] } }));
    }

    const list = add(ctxRow, frame({ name: 'Record List', gap: t.space.sm, width: 360 }));
    add(list, text(t, 'caption', 'Record List · 훑어볼 때 = 작은 마커', { name: 'cap', color: t.color.textTertiary, weight: 600 }));
    for (const r of RECORDS.slice(0, 3)) add(list, recordCard(sctx, r, { maxLines: 2 }), { fillW: true });

    const cal = add(ctxRow, cardSurface(frame({ name: 'Calendar', gap: t.space.sm, pad: t.space.lg, width: 300 }), pair.t));
    add(cal, text(t, 'caption', 'Calendar · 색 점만', { name: 'cap', color: t.color.textTertiary, weight: 600 }));
    const week = add(cal, frame({ name: 'week', dir: 'H', justify: 'SPACE_BETWEEN' }), { fillW: true });
    const days: [number, Emotion[]][] = [[21, ['joy']], [22, ['calm', 'anxious', 'neutral']], [23, []], [24, ['anxious', 'neutral']], [25, ['tired']], [26, []], [27, ['sad']]];
    for (const [d, es] of days) {
      const cell = add(week, frame({ name: `d${d}`, gap: t.space.xxs, align: 'CENTER' }));
      add(cell, text(t, 'bodySmall', String(d), { name: 'n', color: es.length ? t.color.textPrimary : t.color.textTertiary }));
      const dots = add(cell, frame({ name: 'dots', dir: 'H', gap: t.space.hair, height: t.size.emotionXs }));
      for (const e of es) add(dots, ellipse(t.size.emotionXs - t.space.hair, t.color.emotion[e].fill, e));
    }
  }
  return { title: 'Comparison · Emotion Marker (B 채택)', frames: [board] };
}

/** Motion concept — storyboards, not animation. "평소에는 정적이고, 만졌을 때만 말랑하다." */
export function motionBoard(t: Theme, st: EmotionStyleDef): ComparisonBoard {
  const board = frame({ name: 'Motion Concept', width: 1240, gap: t.space.xxl, pad: t.space.xxxl, fill: t.color.surface, radius: t.radius.xxl });
  add(board, text(t, 'title', 'Motion — 평소에는 정적이고, 만졌을 때만 말랑하다', { name: 'title' }));
  note(t, board, 'Idle animation 없음. 모든 모양에 공통으로 squish → settle — 선택하거나 탭할 때만 아주 살짝 눌렸다 돌아와요. 과거 기록은 흔들림 없이 조용히 떠올라요(fade + 4~6px rise, 1회). 진폭 ≤ 5%, spring 과장·빠른 bounce 금지, 목록의 마커는 움직이지 않음.');
  const e: Emotion = 'joy';
  type Step = { label: string; sx?: number; sy?: number; dy?: number; op?: number; w?: number };
  type Row = { name: string; where: string; spec: string; kind: 'marker' | 'surface' | 'press'; steps: Step[] };
  const rows: Row[] = [
    {
      name: 'Select · squish → settle', kind: 'marker', where: 'Editor 감정 선택, Home `+ 기분` — 선택한 마커 1개만',
      spec: '0 → 90ms scale(1.05, 0.95) ease-out · → 240ms (0.98, 1.02) · → 400ms (1, 1) spring(dampingRatio ≈ 0.85, low stiffness). 선택 시 1회 soft bounce.',
      steps: [{ label: '0ms' }, { label: '90ms', sx: 1.05, sy: 0.95, dy: 0.4 }, { label: '240ms', sx: 0.98, sy: 1.02 }, { label: '400ms' }],
    },
    {
      name: 'Tap · tiny squish', kind: 'marker', where: '이미 선택된 마커를 다시 탭하거나 Detail의 감정 pill을 탭할 때',
      spec: 'scale(1.03, 0.97) 70ms → (1, 1) 160ms ease-out. bounce 없음.',
      steps: [{ label: 'rest' }, { label: '70ms', sx: 1.03, sy: 0.97, dy: 0.3 }, { label: '230ms' }],
    },
    {
      name: 'Surface · fade + rise', kind: 'surface', where: 'Related Memories의 과거 기록, Home의 다시 만난 생각 카드가 처음 보일 때',
      spec: 'opacity 0 → 1 · translateY 5px → 0 · 280ms ease-out(decelerate) · 항목 간 60ms stagger. spring·rotate·좌우 흔들림 없음. 1회만.',
      steps: [{ label: '0ms', dy: 5, op: 0.05 }, { label: '120ms', dy: 2.5, op: 0.55 }, { label: '280ms', dy: 0, op: 1 }],
    },
    {
      name: 'Press · soft give', kind: 'press', where: '카드·버튼을 누르는 동안 (만지면 말랑)',
      spec: '누르는 동안 surface scale 0.98 + 마커 (1.04, 0.96) · 100ms in / 180ms out ease-out. 손을 떼면 원래대로.',
      steps: [{ label: 'rest' }, { label: 'pressed', sx: 1.04, sy: 0.96, dy: 0.5, w: 0.98 }, { label: 'release' }],
    },
  ];
  for (const r of rows) {
    const block = add(board, cardSurface(frame({ name: r.name, dir: 'H', gap: t.space.xxl, pad: t.space.xl, align: 'CENTER' }), t), { fillW: true });
    const info = add(block, frame({ name: 'info', gap: t.space.xs, width: 420 }));
    add(info, text(t, 'heading', r.name, { name: 'name' }));
    add(info, para(t, 'caption', r.where, { name: 'where', color: t.color.textSecondary }), { fillW: true });
    add(info, para(t, 'caption', r.spec, { name: 'spec', color: t.color.textTertiary }), { fillW: true });
    const strip = add(block, frame({ name: 'frames', dir: 'H', gap: t.space.lg, align: 'CENTER' }));
    r.steps.forEach((s, i) => {
      if (i > 0) add(strip, text(t, 'body', '→', { name: 'arrow', color: t.color.textTertiary }));
      const cell = add(strip, frame({ name: s.label, gap: t.space.xs, align: 'CENTER' }));
      const wrap = (inner: string) => transformed(inner, s.sx ?? 1, s.sy ?? 1, 0, s.dy ?? 0);
      if (r.kind === 'marker') {
        add(cell, svg(t, st, e, 56, 'marker', wrap));
      } else if (r.kind === 'press') {
        const card = add(cell, cardSurface(frame({ name: 'card', dir: 'H', width: Math.round(132 * (s.w ?? 1)), gap: t.space.xs, pad: t.space.sm, align: 'CENTER' }), t));
        add(card, svg(t, st, e, t.size.emotionSm, 'marker', wrap));
        add(card, frame({ name: 'line', height: t.space.xs, fill: t.color.surfaceSecondary, radius: t.radius.full }), { fillW: true });
      } else {
        // a past entry quietly surfacing: the offset is shown as top padding inside a fixed slot
        const slot = add(cell, frame({ name: 'slot', width: 150, height: 64, pad: { t: 6 + (s.dy ?? 0) * 2, b: 0, l: 0, r: 0 } }));
        const entry = add(slot, frame({ name: 'entry', gap: t.space.xs }), { fillW: true });
        entry.opacity = s.op ?? 1;
        const meta = add(entry, frame({ name: 'meta', dir: 'H', gap: t.space.xxs, align: 'CENTER' }));
        add(meta, svg(t, st, 'calm', t.size.emotionMarker, 'marker'));
        add(meta, frame({ name: 'when', width: 40, height: t.space.xs, fill: t.color.borderStrong, radius: t.radius.full }));
        add(entry, frame({ name: 'quote-1', height: t.space.xs + t.space.hair, fill: t.color.textTertiary, radius: t.radius.full }), { fillW: true });
        add(entry, frame({ name: 'quote-2', width: 96, height: t.space.xs + t.space.hair, fill: t.color.textTertiary, radius: t.radius.full }));
      }
      add(cell, text(t, 'caption', s.label, { name: 't', color: t.color.textTertiary }));
    });
  }
  const later = add(board, frame({ name: 'Later (not in MVP)', gap: t.space.xs, pad: t.space.lg, radius: t.card.radius, stroke: t.color.border, strokeWeight: t.border.hairline }), { fillW: true });
  add(later, text(t, 'heading', '향후 확장 — MVP에서는 구현하지 않음', { name: 'name' }));
  note(t, later, '모양별 상호작용 훅은 MarkerShapeDef.motion에 준비돼 있어요 (현재 모두 squish). 예: 하트 → subtle pulse, 별 → tiny pop, 동글동글/조약돌 → squish. idle animation(숨쉬기 등)은 쓰지 않아요.');
  const rm = add(board, frame({ name: 'Reduce Motion', gap: t.space.xs, pad: t.space.lg, radius: t.card.radius, fill: t.color.surfaceSecondary }), { fillW: true });
  add(rm, text(t, 'heading', 'Reduce Motion', { name: 'name' }));
  note(t, rm, '모든 scale·translate 제거. 선택은 즉시 halo(외곽선) + 라벨 굵기 변화, 떠오르기는 120ms opacity만(또는 즉시 표시). Android: Settings.Global.ANIMATOR_DURATION_SCALE == 0f 이거나 앱의 ‘움직임 줄이기’가 켜져 있으면 Compose 애니메이션 spec을 snap()으로 교체.');
  return { title: 'Comparison · Motion (final)', frames: [board] };
}

/** Shape picker: Settings with the 2-column grid vs compact cards, plus the full shape × palette sheet. */
export function shapePickerComparison(base: BuilderConfig, v3: Pair): ComparisonBoard {
  const t = v3.t;
  const settings = (['grid', 'list'] as const).map((layout) => {
    const f = SettingsScreen.build({ t, lib: v3.lib, config: { ...base, refine: 'v3', markerVariant: 'same', shapePicker: layout } });
    f.name = layout === 'grid' ? 'Settings · 내 감정 조각 · 2열 grid' : 'Settings · 내 감정 조각 · compact card';
    return f;
  });
  const sheet = frame({ name: '내 감정 조각 · shape × palette', gap: t.space.xl, pad: t.space.xxl, fill: t.color.surface, radius: t.radius.xxl });
  add(sheet, text(t, 'title', '내 감정 조각', { name: 'title' }));
  note(t, sheet, 'Emotion = Color · Shape = 취향 · Label = 의미 · Motion = 부드러운 촉감. 어떤 모양을 골라도 7가지 색 매핑은 같아요. 모든 모양은 같은 젤리 처리(미세하게 불규칙한 실루엣·부드러운 가장자리·작은 하이라이트)와 비슷한 시각적 무게를 가져요.');
  for (const k of Object.keys(MARKER_SHAPES) as MarkerShapeKey[]) {
    const st = sameShapeMarker(k);
    const row = add(sheet, frame({ name: k, dir: 'H', gap: t.space.lg, align: 'CENTER' }));
    const label = add(row, frame({ name: 'name', width: 72 }));
    add(label, text(t, 'body', MARKER_SHAPES[k].userLabel, { name: 'label', weight: 600 }));
    for (const e of EMOTIONS) add(row, svg(t, st, e, t.size.emotionMd + t.space.xs, `${e}/36`));
    const small = add(row, frame({ name: 'list size', dir: 'H', gap: t.space.xxs, align: 'CENTER', pad: { t: 0, b: 0, l: t.space.lg, r: 0 } }));
    for (const e of EMOTIONS) add(small, svg(t, st, e, t.size.emotionMarker, `${e}/14`));
  }
  const cal = add(sheet, frame({ name: 'calendar', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
  add(cal, text(t, 'caption', 'Calendar는 모양과 무관하게 색 점만 →', { name: 'cap', color: t.color.textTertiary }));
  for (const e of EMOTIONS) add(cal, ellipse(t.size.emotionXs, t.color.emotion[e].fill, e));
  return { title: 'Comparison · Shape Picker (내 감정 조각)', frames: [...settings, sheet] };
}

/** A chosen shape applied: Record Editor and Record List with the same shape in different emotion colors. */
export function shapeAppliedExamples(base: BuilderConfig, pair: Pair, shapeLabel: string): ComparisonBoard {
  const config = { ...base, refine: 'v3' as const, markerVariant: 'same' as const };
  const editor = RecordEditorScreen.build({ t: pair.t, lib: pair.lib, config });
  editor.name = `Record Editor · ${shapeLabel}`;
  const list = RecordsListScreen.build({ t: pair.t, lib: pair.lib, config });
  list.name = `Records — List · ${shapeLabel}`;
  return { title: `Example · 내 감정 조각 = ${shapeLabel}`, frames: [editor, list] };
}

/** Related Memories final: Thread B v3 · "문득, 예전의 생각이 떠올랐어요" — without vs with subtitle. */
export function relatedCopyComparison(base: BuilderConfig, _v2: Pair, v3: Pair): ComparisonBoard {
  // Design Freeze: the subtitle comparison is closed; this board now shows the final screen as reference.
  const keys: RelatedCopyKey[] = ['surfaced'];
  const frames = keys.map((k) => {
    const f = relatedThreadBV3({ t: v3.t, lib: v3.lib, config: { ...base, refine: 'v3', relatedVariant: 'threadB' } }, k);
    f.name = `Related Memories · ${RELATED_COPY[k].label}`;
    return f;
  });
  return { title: 'Comparison · Related Memories (final reference)', frames };
}
