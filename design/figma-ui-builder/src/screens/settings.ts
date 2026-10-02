// Settings — Category / Emotion style / Reminder / Privacy & data.
import { add, cardSurface, frame, text } from '../core/layout';
import { icon } from '../components/_util';
import { EMOTIONS, EMOTION_LABEL, emotionStyles } from '../emotions';
import type { EmotionStyleKey, ShapePickerLayout } from '../types';
import { MARKER_SHAPES } from '../emotions';
import type { MarkerShapeKey } from '../emotions';
import { CATEGORIES } from '../data/sample';
import type { IconName } from '../core/icons';
import { appBar, chip, scaffold, section } from './shared';
import type { ScreenContext, ScreenDef } from './shared';

type Control = 'chevron' | 'toggleOn' | 'toggleOff' | 'radioOn' | 'radioOff' | 'value';

interface RowSpec {
  icon?: IconName;
  title: string;
  subtitle?: string;
  value?: string;
  control: Control;
}

function group(ctx: ScreenContext, parent: FrameNode, title: string, rows: RowSpec[], extra?: (card: FrameNode) => void) {
  const { t, lib } = ctx;
  const s = section(ctx, parent, `group/${title}`, t.space.xs);
  add(s, text(t, 'label', title, { name: 'group-title', color: t.color.textTertiary }));
  const card = add(s, cardSurface(frame({ name: 'card' }), t), { fillW: true });
  if (extra) extra(card);
  rows.forEach((r, i) => {
    const hide: string[] = [];
    if (!r.icon) hide.push('leading');
    if (!r.subtitle) hide.push('subtitle');
    const row = lib.instance('Settings Row', {
      variant: { control: r.control },
      text: { title: r.title, ...(r.subtitle ? { subtitle: r.subtitle } : {}), ...(r.value ? { value: r.value } : {}) },
      swap: r.icon ? { leading: lib.icon(r.icon) } : undefined,
      hide,
    });
    add(card, row, { fillW: true });
    if (i > 0 || extra) {
      const divider = frame({ name: 'divider', height: t.border.hairline, fill: t.color.border });
      card.insertChild(card.children.indexOf(row), divider);
      divider.layoutSizingHorizontal = 'FILL';
    }
  });
  return card;
}

/** v1: radio rows with design names + one preview strip of the active style (kept for comparison). */
export function emotionStyleRadioList(ctx: ScreenContext, parent: FrameNode) {
  const { t, lib } = ctx;
  const styles = emotionStyles(t.refined);
  const keys = Object.keys(styles) as EmotionStyleKey[];
  group(ctx, parent, '감정 표현', keys.map((k) => ({
    title: styles[k].label,
    control: k === ctx.config.emotionStyle ? 'radioOn' : 'radioOff',
  })), (card) => {
    const preview = add(card, frame({ name: 'preview', dir: 'H', justify: 'SPACE_BETWEEN', pad: [t.space.md, t.space.sm] }), { fillW: true });
    for (const e of EMOTIONS) {
      const col = add(preview, frame({ name: e, gap: t.space.xxs, align: 'CENTER' }));
      add(col, lib.emotion(e, 'md').createInstance());
      add(col, text(t, 'caption', EMOTION_LABEL[e], { name: 'label', color: t.color.textSecondary }));
    }
  });
}

/** v2: one card per style showing all 7 emotions — users choose by shape, names are secondary. */
export function emotionStylePicker(ctx: ScreenContext, parent: FrameNode) {
  const { t, lib } = ctx;
  const s = section(ctx, parent, 'group/감정 표현', t.space.xs);
  add(s, text(t, 'label', '감정 표현', { name: 'group-title', color: t.color.textTertiary }));
  add(s, text(t, 'caption', '기록할 때는 감정 이름이 함께 보여요. 색은 어떤 모양에서도 같아요.', { name: 'hint', color: t.color.textTertiary }));
  const list = add(s, frame({ name: 'styles', gap: t.space.xs }), { fillW: true });
  for (const k of Object.keys(emotionStyles(t.refined)) as EmotionStyleKey[]) {
    add(list, lib.instance('Emotion Style Option', { variant: { style: k, state: k === ctx.config.emotionStyle ? 'selected' : 'default' } }), { fillW: true });
  }
}

/** v3 Settings › 내 감정 조각. `grid` = 2 columns × 3 rows (compact), `list` = compact cards. */
export function shapePicker(ctx: ScreenContext, parent: FrameNode, layout: ShapePickerLayout) {
  const { t, lib } = ctx;
  const s = section(ctx, parent, 'group/내 감정 조각', t.space.xs);
  add(s, text(t, 'label', '내 감정 조각', { name: 'group-title', color: t.color.textTertiary }));
  add(s, text(t, 'bodySmall', '어떤 모양으로 기록할까요?', { name: 'question', color: t.color.textSecondary }));
  const keys = Object.keys(MARKER_SHAPES) as MarkerShapeKey[];
  const inst = (k: MarkerShapeKey) => lib.instance('Shape Option', { variant: { layout, shape: k, state: k === ctx.config.markerShape ? 'selected' : 'default' } });
  if (layout === 'list') {
    const list = add(s, frame({ name: 'shapes', gap: t.space.xs }), { fillW: true });
    for (const k of keys) add(list, inst(k), { fillW: true });
  } else {
    const grid = add(s, frame({ name: 'shapes', gap: t.space.xs }), { fillW: true });
    for (let i = 0; i < keys.length; i += 2) {
      const row = add(grid, frame({ name: `row-${i / 2 + 1}`, dir: 'H', gap: t.space.xs }), { fillW: true });
      for (const k of keys.slice(i, i + 2)) add(row, inst(k), { fillW: true });
    }
  }
  add(s, text(t, 'caption', '색은 감정마다 같아요. 기록할 때는 감정 이름이 함께 보여요.', { name: 'hint', color: t.color.textTertiary }));
}

export const SettingsScreen: ScreenDef = {
  key: 'settings',
  label: 'Settings',
  build(ctx) {
    const { t, lib } = ctx;
    const { root, content } = scaffold(ctx, { name: 'Settings', nav: 'settings', appBar: appBar(ctx, 'title', '설정') });
    content.itemSpacing = t.space.xl;

    // Category
    group(ctx, content, '카테고리', [{ icon: 'folder', title: '카테고리 관리', subtitle: '순서 변경 · 이름 바꾸기 · 삭제', control: 'chevron' }], (card) => {
      const wrap = add(card, frame({ name: 'chips', dir: 'H', gap: t.space.xs, pad: t.space.md }), { fillW: true });
      wrap.layoutWrap = 'WRAP';
      wrap.counterAxisSpacing = t.space.xs;
      for (const c of CATEGORIES) add(wrap, chip(ctx, c));
      const addChip = add(wrap, frame({ name: 'add', dir: 'H', gap: t.space.xxs, align: 'CENTER', height: t.size.chip, pad: [0, t.space.sm], radius: t.chipRadius, fill: t.role.subtleAction }));
      add(addChip, icon(lib, 'plus', t.role.onSubtleAction, t.icon.sm));
      add(addChip, text(t, 'label', '추가', { name: 'label', color: t.role.onSubtleAction }));
    });

    // v3: "내 감정 조각" — the user picks one marker shape; every emotion uses it, colors carry meaning.
    if (t.v3) shapePicker(ctx, content, ctx.config.shapePicker);
    else if (t.refined) emotionStylePicker(ctx, content);
    else emotionStyleRadioList(ctx, content);

    // Reminder
    const rem = group(ctx, content, '다시 만나기 알림', [
      { title: '사용 안 함', control: 'radioOff' },
      { title: '주 1회', subtitle: '가장 다시 볼 만한 기록 하나', control: 'radioOn' },
      { title: '주 2~3회', control: 'radioOff' },
      { icon: 'calendar', title: '요일', value: '일요일', control: 'value' },
      { icon: 'clock', title: '시간', value: '오후 9:00', control: 'value' },
      { icon: 'lock', title: '잠금 화면에서 내용 가리기', control: 'toggleOn' },
    ]);
    rem.name = 'reminder-card';

    // Privacy & data
    group(ctx, content, '보안 · 데이터', [
      { icon: 'shield', title: '앱 잠금', subtitle: '지문 · 얼굴 인식', control: 'toggleOn' },
      { icon: 'download', title: '암호화된 백업 내보내기', control: 'chevron' },
      // Import is a dev-only seed tool from v2 on, not a product feature.
      ...(t.refined ? [] : [{ icon: 'upload' as const, title: '기존 메모 가져오기', subtitle: '텍스트 · Markdown', control: 'chevron' as const }]),
    ]);
    const foot = section(ctx, content, 'footnote', t.space.xxs);
    const line = add(foot, frame({ name: 'local', dir: 'H', gap: t.space.xxs, align: 'CENTER' }));
    add(line, icon(lib, 'lock', t.color.textTertiary, t.icon.sm));
    add(line, text(t, 'caption', '모든 기록과 분석은 이 기기 안에만 저장돼요', { name: 'note', color: t.color.textTertiary }));
    return root;
  },
};
