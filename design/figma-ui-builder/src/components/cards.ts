// Content cards: Record Card, Rediscovery Card (variant registry), Related Memory Card,
// Quick Record Entry, Notification, Photo.
import type { ComponentDef, Library } from '../core/library';
import { add, cardSurface, ellipse, frame, para, solid, spacer, text } from '../core/layout';
import type { Theme } from '../tokens/resolve';
import { comp, emotionInst, icon, markerSize, variantSet } from './_util';

const contentWidth = (t: Theme) => t.size.viewportWidth - 2 * t.space.lg;

function categoryTag(lib: Library): InstanceNode {
  const inst = lib.component('Category Tag').createInstance();
  inst.name = 'category';
  return inst;
}

// ─── Record Card ────────────────────────────────────────────────────────────
export const RecordCard: ComponentDef = {
  name: 'Record Card',
  build(t, lib) {
    const c = cardSurface(comp({ name: 'Record Card', width: contentWidth(t), pad: t.layout.cardPadding, gap: t.space.sm }), t);
    const meta = add(c, frame({ name: 'meta', dir: 'H', gap: t.space.xs, align: 'CENTER' }), { fillW: true });
    add(meta, emotionInst(lib, 'calm', markerSize(t)));
    add(meta, text(t, 'caption', '오늘 · 오전 8:42', { name: 'time', color: t.color.textTertiary }), { fillW: true });
    add(meta, categoryTag(lib));
    add(c, para(t, 'body', '출근길에 문득, 요즘 내가 제대로 성장하고 있는지 모르겠다는 생각이 들었다.', { name: 'body', maxLines: 3 }), { fillW: true });
    const photo = add(c, frame({ name: 'photo', dir: 'H', gap: t.space.xxs, align: 'CENTER' }));
    add(photo, icon(lib, 'image', t.color.textTertiary, t.icon.sm));
    add(photo, text(t, 'caption', '사진 1장', { name: 'photo-label', color: t.color.textTertiary }));
    photo.visible = false;
    return c;
  },
};

// ─── Rediscovery Card — variant registry ───────────────────────────────────
// Add a new Home rediscovery look: add an entry here; it becomes a `style=` variant automatically.
type RediscoveryBuilder = (t: Theme, lib: Library) => ComponentNode;

function periodLabel(t: Theme, lib: Library, parent: FrameNode | ComponentNode) {
  const row = add(parent, frame({ name: 'period-row', dir: 'H', gap: t.space.xxs + t.space.hair, align: 'CENTER' }));
  add(row, icon(lib, 'history', t.role.metaLabel, t.icon.sm));
  add(row, text(t, 'caption', '1년 전 이맘때', { name: 'period', color: t.role.metaLabel, weight: 600 }));
}

function footer(t: Theme, lib: Library, parent: FrameNode | ComponentNode) {
  const f = add(parent, frame({ name: 'footer', dir: 'H', gap: t.space.xs, align: 'CENTER' }), { fillW: true });
  add(f, emotionInst(lib, 'calm', markerSize(t)));
  add(f, text(t, 'caption', '2025년 10월 3일', { name: 'date', color: t.color.textTertiary }), { fillW: true });
  add(f, categoryTag(lib));
}

function quoteGlyph(t: Theme, parent: FrameNode | ComponentNode) {
  if (!t.variant.quoteGlyph) return;
  const g = add(parent, text(t, 'display', '“', { name: 'glyph', color: t.color.accent }));
  g.textAutoResize = 'WIDTH_AND_HEIGHT';
  g.lineHeight = { value: t.type.heading.lineHeight, unit: 'PIXELS' };
}

export const REDISCOVERY_STYLES: Record<string, RediscoveryBuilder> = {
  /** Default: horizontally scrolling card with the user's past sentence as the hero. */
  card(t, lib) {
    const fill = t.variant.tintedRediscovery ? t.color.accentContainer : undefined;
    const c = cardSurface(comp({ name: 'card', width: t.size.rediscoveryCardWidth, pad: t.layout.cardPadding, gap: t.space.sm }), t, { hero: true, fill });
    periodLabel(t, lib, c);
    quoteGlyph(t, c);
    add(c, para(t, 'memory', '아직 회사 파악도 덜 된 것 같고 부족한 것만 보인다. 그래도 하나씩 알아가는 중이다.', { name: 'quote', maxLines: 4 }), { fillW: true });
    footer(t, lib, c);
    return c;
  },
  /** Full-width hero used by Home variant C. */
  hero(t, lib) {
    const c = cardSurface(comp({ name: 'hero', width: contentWidth(t), pad: t.space.xl, gap: t.space.md }), t, { hero: true, fill: t.role.heroFill });
    periodLabel(t, lib, c);
    quoteGlyph(t, c);
    add(c, para(t, 'bodyLarge', '운영 이슈를 직접 다루면서 조금씩 보는 눈이 생기는 것 같다. 예전엔 로그만 봐도 막막했는데, 이제는 어디부터 볼지 감이 온다.', { name: 'quote', maxLines: 6 }), { fillW: true });
    footer(t, lib, c);
    const cta = add(c, frame({ name: 'cta', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
    add(cta, icon(lib, 'edit', t.role.onHero, t.icon.sm));
    add(cta, text(t, 'label', '지금의 생각 덧붙이기', { name: 'cta-label', color: t.role.onHero }));
    return c;
  },
  /** Compact row used in dense lists (Home variant A). */
  compact(t, lib) {
    const c = cardSurface(comp({ name: 'compact', dir: 'H', width: contentWidth(t), pad: [t.space.md, t.layout.cardPadding], gap: t.space.sm, align: 'CENTER' }), t);
    add(c, emotionInst(lib, 'calm', 'md'));
    const col = add(c, frame({ name: 'text', gap: t.space.hair }), { fillW: true });
    add(col, text(t, 'caption', '1년 전 이맘때', { name: 'period', color: t.role.metaLabel, weight: 600 }));
    add(col, para(t, 'bodySmall', '아직 회사 파악도 덜 된 것 같고 부족한 것만 보인다.', { name: 'quote', maxLines: 2 }), { fillW: true });
    add(c, icon(lib, 'chevronRight', t.color.textTertiary, t.icon.md));
    return c;
  },
};
export type RediscoveryStyle = keyof typeof REDISCOVERY_STYLES;

export const RediscoveryCard: ComponentDef = {
  name: 'Rediscovery Card',
  build(t, lib) {
    return variantSet(lib, t, { style: Object.keys(REDISCOVERY_STYLES) }, (p) => REDISCOVERY_STYLES[p.style](t, lib), { columns: 3 });
  },
};

// ─── Related Memory Card ────────────────────────────────────────────────────
export const RelatedMemoryCard: ComponentDef = {
  name: 'Related Memory Card',
  build(t, lib) {
    const c = comp({ name: 'Related Memory Card', dir: 'H', width: contentWidth(t), gap: t.space.sm });
    const rail = add(c, frame({ name: 'rail', width: t.size.timelineRail, align: 'CENTER', pad: { t: t.space.xxs, b: 0, l: 0, r: 0 }, gap: t.space.xxs }), { fillH: true });
    const dot = add(rail, ellipse(t.space.sm, t.color.accentContainer, 'node'));
    dot.strokes = [solid(t.color.accent)];
    dot.strokeWeight = t.border.focus;
    add(rail, frame({ name: 'line', width: t.space.hair, fill: t.color.border, radius: t.radius.full }), { fillH: true });

    const col = add(c, frame({ name: 'content', gap: t.space.xs, pad: { t: 0, b: t.space.lg, l: 0, r: 0 } }), { fillW: true });
    add(col, text(t, 'label', '6개월 전', { name: 'when', color: t.role.metaLabel }));
    const card = add(col, cardSurface(frame({ name: 'card', pad: t.layout.cardPadding, gap: t.space.sm }), t), { fillW: true });
    add(card, para(t, 'memory', '운영 이슈를 직접 다루면서 조금씩 보는 눈이 생기는 것 같다.', { name: 'quote', maxLines: 5 }), { fillW: true });
    const meta = add(card, frame({ name: 'meta', dir: 'H', gap: t.space.xs, align: 'CENTER' }), { fillW: true });
    add(meta, emotionInst(lib, 'calm', markerSize(t)));
    add(meta, text(t, 'caption', '2026년 4월 11일', { name: 'date', color: t.color.textTertiary }), { fillW: true });
    add(meta, categoryTag(lib));
    return c;
  },
};

// ─── Quick Record Entry (Home) ──────────────────────────────────────────────
function sendButton(t: Theme, lib: Library): FrameNode {
  const b = frame({ name: 'send', dir: 'H', width: t.size.sendButton, height: t.size.sendButton, justify: 'CENTER', align: 'CENTER', radius: t.radius.full, fill: t.color.accent });
  add(b, icon(lib, 'arrowUp', t.color.onAccent, t.icon.md));
  return b;
}

function emotionRow(t: Theme, lib: Library, parent: FrameNode | ComponentNode) {
  const row = add(parent, frame({ name: 'emotions', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
  for (const e of ['calm', 'joy', 'excited', 'neutral', 'tired', 'anxious', 'sad'] as const) add(row, emotionInst(lib, e, 'sm', `emotion/${e}`));
  return row;
}

export const QuickEntry: ComponentDef = {
  name: 'Quick Record Entry',
  build(t, lib) {
    return variantSet(lib, t, { size: ['regular', 'large', 'bar'] }, (p) => {
      if (p.size === 'bar') {
        const c = comp({ name: 'bar', dir: 'H', width: contentWidth(t), height: t.size.searchBar, pad: { t: 0, b: 0, l: t.space.md, r: t.space.xs }, gap: t.space.sm, align: 'CENTER', radius: t.radius.full, fill: t.color.surface, stroke: t.color.border, strokeWeight: t.border.hairline });
        add(c, icon(lib, 'edit', t.color.textTertiary, t.icon.md));
        add(c, text(t, 'body', '한 줄만 남겨도 괜찮아요', { name: 'placeholder', color: t.color.textTertiary }), { fillW: true });
        add(c, sendButton(t, lib));
        return c;
      }
      const large = p.size === 'large';
      const c = comp({ name: p.size, width: contentWidth(t), pad: t.layout.cardPadding, gap: t.space.md, radius: t.card.heroRadius, fill: t.color.surface, stroke: t.role.focusStroke, strokeWeight: t.refined ? t.border.hairline : t.border.focus });
      const area = add(c, frame({ name: 'input', minHeight: large ? t.size.quickEntryLarge : t.size.quickEntryRegular }), { fillW: true });
      add(area, para(t, 'bodyLarge', '지금 떠오르는 생각을 남겨보세요…', { name: 'placeholder', color: t.color.textTertiary }), { fillW: true });
      if (large) {
        const tools = add(c, frame({ name: 'tools', dir: 'H', gap: t.space.md, align: 'CENTER' }), { fillW: true });
        add(tools, icon(lib, 'folder', t.color.textSecondary, t.icon.md));
        add(tools, icon(lib, 'tag', t.color.textSecondary, t.icon.md));
        add(tools, icon(lib, 'image', t.color.textSecondary, t.icon.md));
        add(tools, spacer(), { fillW: true });
        add(tools, icon(lib, 'expand', t.color.textSecondary, t.icon.md));
      }
      const bottom = add(c, frame({ name: 'bottom', dir: 'H', align: 'CENTER', justify: 'SPACE_BETWEEN' }), { fillW: true });
      if (t.refined) {
        // v2: picking needs a label — a single "+ 기분" pill opens the icon + label picker.
        const mood = lib.component('Mood Pill', { state: 'empty' }).createInstance();
        mood.name = 'mood';
        add(bottom, mood);
      } else {
        emotionRow(t, lib, bottom);
      }
      add(bottom, sendButton(t, lib));
      return c;
    }, { columns: 3 });
  },
};

// ─── Notification (Reminder) ────────────────────────────────────────────────
export const Notification: ComponentDef = {
  name: 'Notification',
  build(t, lib) {
    const c = comp({ name: 'Notification', width: contentWidth(t), pad: t.space.md, gap: t.space.xs, radius: t.radius.xl, fill: t.color.surface });
    const head = add(c, frame({ name: 'head', dir: 'H', gap: t.space.xs, align: 'CENTER' }), { fillW: true });
    const app = add(head, frame({ name: 'app-icon', dir: 'H', width: t.icon.md, height: t.icon.md, radius: t.radius.full, fill: t.role.appIcon, justify: 'CENTER', align: 'CENTER' }));
    add(app, ellipse(t.space.xs, t.color.background, 'mark'));
    add(head, text(t, 'caption', '다시 만난 생각 · 지금', { name: 'app', color: t.color.textSecondary }), { fillW: true });
    add(head, icon(lib, 'chevronRight', t.color.textTertiary, t.icon.sm));
    const row = add(c, frame({ name: 'row', dir: 'H', gap: t.space.sm }), { fillW: true });
    const col = add(row, frame({ name: 'texts', gap: t.space.hair }), { fillW: true });
    add(col, para(t, 'bodySmall', '1년 전 이맘때의 생각', { name: 'title', weight: 600 }), { fillW: true });
    add(col, para(t, 'bodySmall', '“아직 회사 파악도 덜 된 것 같고 부족한 것만 보인다.”', { name: 'body', color: t.color.textSecondary, maxLines: 2 }), { fillW: true });
    add(row, emotionInst(lib, 'tired', 'md'));
    return c;
  },
};

// ─── Photo placeholder ─────────────────────────────────────────────────────
export const Photo: ComponentDef = {
  name: 'Photo',
  build(t, lib) {
    const c = comp({ name: 'Photo', dir: 'H', width: contentWidth(t), height: t.size.photo, radius: t.radius.lg, justify: 'CENTER', align: 'CENTER', fill: t.color.surfaceSecondary, clip: true });
    c.fills = [{
      type: 'GRADIENT_LINEAR',
      gradientTransform: [[1, 0, 0], [0, 1, 0]],
      gradientStops: [
        { position: 0, color: { ...solid(t.color.emotion.calm.fill).color, a: 1 } },
        { position: 1, color: { ...solid(t.color.accentContainer).color, a: 1 } },
      ],
    }];
    add(c, icon(lib, 'image', t.color.surface, t.icon.lg));
    return c;
  },
};
