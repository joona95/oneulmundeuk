// Refinement comparison frames. These never replace screens: they sit in their own Sections
// so v1 and v2 can be judged side by side before DEFAULT_CONFIG is flipped.
import { add, frame, para, solid, text } from '../core/layout';
import type { Library } from '../core/library';
import { EMOTIONS, EMOTION_LABEL, EMOTION_STYLES, EMOTION_STYLES_V1, wrapSvg } from '../emotions';
import type { EmotionStyleDef } from '../emotions';
import { RELATED_VARIANTS } from '../screens/write';
import { SettingsScreen } from '../screens/settings';
import type { Theme } from '../tokens/resolve';
import type { BuilderConfig, RelatedVariant } from '../types';

export interface ComparisonBoard {
  title: string;
  frames: FrameNode[];
}

type Libs = { v1: { t: Theme; lib: Library }; v2: { t: Theme; lib: Library } };

function caption(t: Theme, parent: FrameNode, chars: string) {
  add(parent, para(t, 'bodySmall', chars, { name: 'note', color: t.color.textSecondary }), { fillW: true });
}

/** Emotion: v1 blob vs v2 blob vs the other styles, in color and as mono silhouettes. */
export function emotionComparison(t: Theme): ComparisonBoard {
  const rows: [string, string, EmotionStyleDef][] = [
    ['기존 Blob', 'v1', EMOTION_STYLES_V1.blob],
    ['개선 Blob · 몽글몽글', 'v2 default', EMOTION_STYLES.blob],
    ['Creature · 꼬물꼬물', 'v2 실루엣 공유', EMOTION_STYLES.creature],
    ['Doodle · 끄적끄적', '', EMOTION_STYLES.doodle],
    ['Geometric · 반듯반듯', '', EMOTION_STYLES.geometric],
  ];
  const mono = { fill: t.color.textPrimary, ink: t.color.textPrimary };
  const board = frame({ name: 'Emotion Comparison', gap: t.space.xxl, pad: t.space.xxl, fill: t.color.surface, radius: t.radius.xl });
  add(board, text(t, 'title', 'Emotion Comparison', { name: 'title' }));
  caption(t, board, '각 열: 56px 컬러 · 28/20/16px · 8px 캘린더 점 · 28px 흑백 실루엣(색 없이도 구분되는지). 색 매핑은 모든 스타일에서 동일.');
  for (const [name, tag, st] of rows) {
    const block = add(board, frame({ name, gap: t.space.sm }));
    const head = add(block, frame({ name: 'head', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
    add(head, text(t, 'heading', name, { name: 'style' }));
    if (tag) add(head, text(t, 'caption', tag, { name: 'tag', color: t.color.textTertiary }));
    const row = add(block, frame({ name: 'emotions', dir: 'H', gap: t.space.xl }));
    for (const e of EMOTIONS) {
      const col = add(row, frame({ name: e, gap: t.space.xs, align: 'CENTER' }));
      const big = figma.createNodeFromSvg(wrapSvg(st.draw(e, t.color.emotion[e]), t.size.emotionMd * 2));
      big.name = `${e}/56`;
      add(col, big);
      const sizes = add(col, frame({ name: 'sizes', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
      for (const px of [t.size.emotionMd, t.size.emotionSm, t.icon.sm]) {
        const n = figma.createNodeFromSvg(wrapSvg(st.draw(e, t.color.emotion[e]), px));
        n.name = `${e}/${px}`;
        add(sizes, n);
      }
      const dot = figma.createEllipse();
      dot.resize(t.size.emotionXs, t.size.emotionXs);
      dot.fills = [solid(t.color.emotion[e].fill)];
      dot.name = `${e}/dot`;
      add(sizes, dot);
      const m = figma.createNodeFromSvg(wrapSvg(st.draw(e, mono), t.size.emotionMd));
      m.name = `${e}/mono`;
      add(col, m);
      add(col, text(t, 'caption', EMOTION_LABEL[e], { name: 'label', color: t.color.textSecondary }));
    }
  }
  return { title: 'Comparison · Emotion', frames: [board] };
}

/** Related Memories: current (v1 colors) vs Thread A / Thread B (v2 colors). */
export function relatedComparison(base: BuilderConfig, libs: Libs): ComparisonBoard {
  const pick: [RelatedVariant, 'v1' | 'v2'][] = [['current', 'v1'], ['threadA', 'v2'], ['threadB', 'v2']];
  const frames = pick.map(([variant, ver]) => {
    const { t, lib } = libs[ver];
    const f = RELATED_VARIANTS[variant].build({ t, lib, config: { ...base, refine: ver, relatedVariant: variant } });
    f.name = `${f.name} (${ver})`;
    return f;
  });
  return { title: 'Comparison · Related Memories', frames };
}

/** Settings: v1 radio list vs v2 mini-preview cards. */
export function settingsComparison(base: BuilderConfig, libs: Libs): ComparisonBoard {
  const frames = (['v1', 'v2'] as const).map((ver) => {
    const { t, lib } = libs[ver];
    const f = SettingsScreen.build({ t, lib, config: { ...base, refine: ver } });
    f.name = ver === 'v1' ? 'Settings · 현재 radio list (v1)' : 'Settings · 미리보기 카드 (v2)';
    return f;
  });
  return { title: 'Comparison · Settings', frames };
}
