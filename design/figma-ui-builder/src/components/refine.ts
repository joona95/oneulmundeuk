// Components introduced by the v2 refinement: emotion-style picker card, memory thread items,
// time-gap divider, memory quote, and the label-first mood pill used in Quick Record Entry.
import type { ComponentDef, Library } from '../core/library';
import { add, ellipse, frame, para, solid, text } from '../core/layout';
import { EMOTIONS, MARKER_SHAPES, emotionStyles, sameShapeMarker, wrapSvg } from '../emotions';
import type { MarkerShapeKey } from '../emotions';
import type { Theme } from '../tokens/resolve';
import type { EmotionStyleKey } from '../types';
import { comp, emotionInst, icon, markerSize, variantSet } from './_util';

const contentWidth = (t: Theme) => t.size.viewportWidth - 2 * t.space.lg;

function categoryTag(lib: Library): InstanceNode {
  const inst = lib.component('Category Tag').createInstance();
  inst.name = 'category';
  return inst;
}

// ─── Emotion Style Option (Settings) ───────────────────────────────────────
// Users pick a style by looking at all 7 emotions drawn in it, not by its name.
export const EmotionStyleOption: ComponentDef = {
  name: 'Emotion Style Option',
  build(t, lib) {
    const styles = emotionStyles(t.refined);
    return variantSet(lib, t, { style: Object.keys(styles), state: ['default', 'selected'] }, (p) => {
      const st = styles[p.style as EmotionStyleKey];
      const on = p.state === 'selected';
      const c = comp({
        name: p.style, width: contentWidth(t), pad: t.space.md, gap: t.space.sm, radius: t.card.radius,
        fill: t.color.surface, stroke: on ? t.color.textPrimary : t.color.border, strokeWeight: on ? t.border.focus : t.border.hairline,
      });
      const head = add(c, frame({ name: 'head', dir: 'H', gap: t.space.sm, align: 'CENTER' }), { fillW: true });
      const names = add(head, frame({ name: 'names', gap: t.space.hair }), { fillW: true });
      add(names, text(t, 'body', st.userLabel, { name: 'title', weight: 600 }));
      add(names, text(t, 'caption', st.description, { name: 'description', color: t.color.textTertiary }));
      const badge = add(head, frame({
        name: 'check', dir: 'H', width: t.size.checkBadge, height: t.size.checkBadge, radius: t.radius.full, justify: 'CENTER', align: 'CENTER',
        fill: on ? t.color.accent : null, stroke: on ? undefined : t.color.borderStrong, strokeWeight: t.border.focus,
      }));
      if (on) add(badge, icon(lib, 'check', t.color.onAccent, t.icon.sm));
      const row = add(c, frame({ name: 'preview', dir: 'H', justify: 'SPACE_BETWEEN', align: 'CENTER' }), { fillW: true });
      for (const e of EMOTIONS) {
        const n = figma.createNodeFromSvg(wrapSvg(st.draw(e, t.color.emotion[e]), t.size.stylePreviewIcon));
        n.name = e;
        n.fills = [];
        add(row, n);
      }
      return c;
    }, { columns: 2 });
  },
};

// ─── Mood pill (label-first emotion picking in Quick Record Entry) ─────────
export const MoodPill: ComponentDef = {
  name: 'Mood Pill',
  build(t, lib) {
    return variantSet(lib, t, { state: ['empty', 'chosen'] }, (p) => {
      const chosen = p.state === 'chosen';
      const c = comp({
        name: p.state, dir: 'H', height: t.size.chip, pad: { t: 0, b: 0, l: chosen ? t.space.xs - t.space.hair : t.space.sm, r: t.space.sm },
        gap: t.space.xxs + t.space.hair, align: 'CENTER', radius: t.radius.full,
        fill: chosen ? t.color.surfaceSecondary : null, stroke: chosen ? undefined : t.color.border, strokeWeight: t.border.hairline,
      });
      if (chosen) add(c, emotionInst(lib, 'calm', 'sm'));
      else add(c, icon(lib, 'plus', t.color.textSecondary, t.icon.sm, 'leading'));
      add(c, text(t, 'label', chosen ? '평온' : '기분', { name: 'label', color: chosen ? t.color.textPrimary : t.color.textSecondary }));
      return c;
    });
  },
};

// ─── Memory thread (Related Memories · variant A) ──────────────────────────
function rail(t: Theme, parent: ComponentNode, node: SceneNode, withLine: boolean) {
  const r = add(parent, frame({ name: 'rail', width: t.size.threadRail, align: 'CENTER', gap: t.space.xs, pad: { t: t.space.hair, b: 0, l: 0, r: 0 } }), { fillH: true });
  add(r, node);
  const line = add(r, frame({ name: 'line', width: t.border.focus, fill: t.color.borderStrong, radius: t.radius.full }), { fillH: true });
  if (!withLine) line.visible = false;
}

export const ThreadItem: ComponentDef = {
  name: 'Thread Item',
  build(t, lib) {
    return variantSet(lib, t, { kind: ['now', 'past', 'last'] }, (p) => {
      const c = comp({ name: p.kind, dir: 'H', width: contentWidth(t), gap: t.space.sm });
      if (p.kind === 'now') {
        const node = ellipse(t.size.threadNode, t.color.accentContainer, 'node');
        node.strokes = [solid(t.color.accent)];
        node.strokeWeight = t.border.focus + t.border.hairline / 2;
        rail(t, c, node, true);
        const col = add(c, frame({ name: 'content', gap: t.space.xs, pad: { t: 0, b: t.space.xl, l: 0, r: 0 } }), { fillW: true });
        const head = add(col, frame({ name: 'head', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
        add(head, text(t, 'caption', '지금', { name: 'when', color: t.color.accent, weight: 600 }));
        add(head, text(t, 'caption', '오전 8:42', { name: 'time', color: t.color.textTertiary }));
        const box = add(col, frame({ name: 'box', pad: [t.space.sm, t.space.md], radius: t.card.radius, fill: t.color.surfaceSecondary }), { fillW: true });
        add(box, para(t, 'bodySmall', '요즘 회사에서 내가 제대로 성장하고 있는지 가끔 모르겠다.', { name: 'body', color: t.color.textSecondary, maxLines: 3 }), { fillW: true });
        return c;
      }
      rail(t, c, emotionInst(lib, 'calm', 'sm'), p.kind === 'past');
      const col = add(c, frame({ name: 'content', gap: t.space.xs, pad: { t: 0, b: p.kind === 'past' ? t.space.xl : 0, l: 0, r: 0 } }), { fillW: true });
      const head = add(col, frame({ name: 'head', dir: 'H', gap: t.space.xs, align: 'CENTER' }), { fillW: true });
      add(head, text(t, 'label', '5개월 전', { name: 'when' }));
      add(head, text(t, 'caption', '2026년 4월 11일', { name: 'date', color: t.color.textTertiary }), { fillW: true });
      add(head, categoryTag(lib));
      add(col, para(t, 'memory', '운영 이슈를 직접 다루면서 조금씩 보는 눈이 생기는 것 같다.', { name: 'quote', maxLines: 6 }), { fillW: true });
      return c;
    }, { columns: 3 });
  },
};

// ─── Time gap + memory quote (Related Memories · variant B) ────────────────
export const TimeGap: ComponentDef = {
  name: 'Time Gap',
  build(t) {
    const c = comp({ name: 'Time Gap', dir: 'H', width: contentWidth(t), gap: t.space.sm, align: 'CENTER', pad: [t.space.xs, 0] });
    add(c, frame({ name: 'line-l', height: t.border.hairline, fill: t.color.borderStrong }), { fillW: true });
    add(c, text(t, 'caption', '5개월 전', { name: 'gap', color: t.color.textSecondary, weight: 600 }));
    add(c, frame({ name: 'line-r', height: t.border.hairline, fill: t.color.borderStrong }), { fillW: true });
    return c;
  },
};

export const MemoryQuote: ComponentDef = {
  name: 'Memory Quote',
  build(t, lib) {
    const c = comp({ name: 'Memory Quote', width: contentWidth(t), gap: t.space.sm, pad: [t.space.xs, t.space.xxs] });
    add(c, para(t, 'memory', '운영 이슈를 직접 다루면서 조금씩 보는 눈이 생기는 것 같다.', { name: 'quote', maxLines: 6 }), { fillW: true });
    const meta = add(c, frame({ name: 'meta', dir: 'H', gap: t.space.xs, align: 'CENTER' }), { fillW: true });
    add(meta, emotionInst(lib, 'calm', markerSize(t)));
    add(meta, text(t, 'caption', '2026년 4월 11일', { name: 'date', color: t.color.textTertiary }), { fillW: true });
    add(meta, categoryTag(lib));
    return c;
  },
};

// ─── Memory Entry (Related Memories · Thread B, v3) ────────────────────────
// A hairline, then time + a tiny emotion marker, then the user's own sentence as the hero.
// No card container: whitespace and a light hierarchy carry the rhythm, so it never reads as search results.
export const MemoryEntry: ComponentDef = {
  name: 'Memory Entry',
  build(t, lib) {
    const c = comp({ name: 'Memory Entry', width: contentWidth(t), gap: t.space.sm });
    add(c, frame({ name: 'rule', height: t.border.hairline, fill: t.color.border }), { fillW: true });
    const meta = add(c, frame({ name: 'meta', dir: 'H', gap: t.space.xs, align: 'CENTER', pad: { t: t.space.sm, b: 0, l: 0, r: 0 } }), { fillW: true });
    // Quiet metadata, never a data row: 1) "6개월 전" (primary meta) 2) date, muted 3) category, muted.
    // The user's sentence below (memory type, charcoal) stays the most important element.
    add(meta, emotionInst(lib, 'calm', markerSize(t)));
    add(meta, text(t, 'label', '6개월 전', { name: 'when', color: t.color.textSecondary }));
    add(meta, text(t, 'caption', '·', { name: 'sep', color: t.color.textTertiary }));
    add(meta, text(t, 'caption', '2026년 4월 11일', { name: 'date', color: t.color.textTertiary }), { fillW: true });
    add(meta, text(t, 'caption', '커리어', { name: 'category', color: t.color.textTertiary, maxLines: 1 }));
    add(c, para(t, 'memory', '운영 이슈를 직접 다루면서 조금씩 보는 눈이 생기는 것 같다.', { name: 'quote', maxLines: 8 }), { fillW: true });
    return c;
  },
};

// ─── Shape Option (Settings › 내 감정 조각) ────────────────────────────────
// Each option previews all 7 emotion colors in that shape — users choose by looking, not by name.
// Selection is subtle: a small check badge, a slightly stronger neutral border, and a bolder name.
export const ShapeOption: ComponentDef = {
  name: 'Shape Option',
  build(t, lib) {
    const gridW = (contentWidth(t) - t.space.xs) / 2;
    return variantSet(lib, t, { layout: ['list', 'grid'], shape: Object.keys(MARKER_SHAPES), state: ['default', 'selected'] }, (p) => {
      const key = p.shape as MarkerShapeKey;
      const st = sameShapeMarker(key);
      const on = p.state === 'selected';
      const badge = () => {
        const b = frame({ name: 'selected', dir: 'H', width: t.size.selectedBadge, height: t.size.selectedBadge, radius: t.radius.full, justify: 'CENTER', align: 'CENTER', fill: on ? t.color.accent : null });
        if (on) add(b, icon(lib, 'check', t.color.onAccent, t.icon.sm - t.space.xxs));
        return b;
      };
      const preview = (parent: ComponentNode | FrameNode, px: number, gap: number) => {
        const row = add(parent, frame({ name: 'preview', dir: 'H', gap, align: 'CENTER' }));
        for (const e of EMOTIONS) {
          const n = figma.createNodeFromSvg(wrapSvg(st.draw(e, t.color.emotion[e]), px));
          n.name = e;
          n.fills = [];
          add(row, n);
        }
        return row;
      };
      const name = text(t, p.layout === 'list' ? 'body' : 'label', MARKER_SHAPES[key].userLabel, { name: 'title', weight: on ? 600 : 500, color: on ? t.color.textPrimary : t.color.textSecondary });
      if (p.layout === 'list') {
        const c = comp({ name: `${p.layout}-${p.shape}-${p.state}`, dir: 'H', width: contentWidth(t), pad: [t.space.sm, t.space.md], gap: t.space.sm, align: 'CENTER', radius: t.card.radius, fill: t.color.surface, stroke: on ? t.color.borderStrong : t.color.border, strokeWeight: t.border.hairline });
        const nameBox = add(c, frame({ name: 'name', width: t.size.shapeNameColumn }));
        add(nameBox, name);
        preview(c, t.size.shapePreviewList, t.space.xxs + t.space.hair);
        add(c, frame({ name: 'spacer' }), { fillW: true });
        add(c, badge());
        return c;
      }
      const c = comp({ name: `${p.layout}-${p.shape}-${p.state}`, width: gridW, pad: [t.space.sm, t.space.sm + t.space.hair], gap: t.space.sm, radius: t.card.radius, fill: t.color.surface, stroke: on ? t.color.borderStrong : t.color.border, strokeWeight: t.border.hairline });
      const head = add(c, frame({ name: 'head', dir: 'H', align: 'CENTER' }), { fillW: true });
      add(head, name, { fillW: true });
      add(head, badge());
      preview(c, t.size.shapePreviewGrid, t.space.xxs);
      return c;
    }, { columns: 4 });
  },
};
