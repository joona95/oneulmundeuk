// Home — three layout variants. Add a variant: write a builder and register it in HOME_VARIANTS
// (also add the key to HomeVariant in types.ts and an <option> in ui.html).
import { COPY } from '../data/copy';
import { add, frame, para, text } from '../core/layout';
import { RECORDS, REDISCOVERY, TODAY, fmtMonthDay } from '../data/sample';
import type { HomeVariant } from '../types';
import { hScroll, rediscoveryCard, recordCard, scaffold, section, sectionHeader } from './shared';
import type { ScreenContext, ScreenDef } from './shared';

const GREETING = '오늘은 어떤 생각을\n하고 있나요?';

function greeting(ctx: ScreenContext, parent: FrameNode, style: 'display' | 'title' = ctx.t.variant.heroType === 'display' ? 'display' : 'title') {
  const { t } = ctx;
  const s = section(ctx, parent, 'greeting', t.space.xs, { pad: { t: t.space.md, b: 0, l: t.layout.screenPadding, r: t.layout.screenPadding } });
  add(s, text(t, 'label', fmtMonthDay(TODAY), { name: 'date', color: t.color.textTertiary, weight: 500 }));
  add(s, para(t, style, GREETING, { name: 'greeting' }), { fillW: true });
  return s;
}

function recent(ctx: ScreenContext, parent: FrameNode, count: number) {
  const s = section(ctx, parent, 'recent', ctx.t.layout.listGap);
  sectionHeader(ctx, s, '최근 기록', '전체 보기');
  for (const r of RECORDS.slice(0, count)) add(s, recordCard(ctx, r, { maxLines: 2 }), { fillW: true });
}

/** A. 기록 입력 중심 — a large composer; rediscovery reduced to one compact row. */
function variantA(ctx: ScreenContext): FrameNode {
  const { t, lib } = ctx;
  const { root, content } = scaffold(ctx, { name: 'Home · A 기록 입력 중심', nav: 'home' });
  greeting(ctx, content, 'title');
  const entry = section(ctx, content, 'quick-entry');
  add(entry, lib.instance('Quick Record Entry', { variant: { size: 'large' } }), { fillW: true });
  const rd = section(ctx, content, 'rediscovery', t.space.sm);
  sectionHeader(ctx, rd, COPY.homeRediscovery.title);
  add(rd, rediscoveryCard(ctx, 'compact', REDISCOVERY[2].period, REDISCOVERY[2].record), { fillW: true });
  recent(ctx, content, 2);
  return root;
}

/** B. 기록 + Rediscovery 균형 (default) — composer and rediscovery carry similar visual weight. */
function variantB(ctx: ScreenContext): FrameNode {
  const { t, lib } = ctx;
  const { root, content } = scaffold(ctx, { name: 'Home · B 균형', nav: 'home' });
  const top = frame({ name: 'now', gap: t.space.lg });
  add(content, top, { fillW: true });
  greeting(ctx, top);
  const entry = section(ctx, top, 'quick-entry');
  add(entry, lib.instance('Quick Record Entry', { variant: { size: 'regular' } }), { fillW: true });

  const past = frame({ name: 'past', gap: t.space.sm });
  add(content, past, { fillW: true });
  const head = section(ctx, past, 'rediscovery-header', t.space.xxs);
  sectionHeader(ctx, head, COPY.homeRediscovery.title, '모두 보기');
  add(head, para(t, 'bodySmall', COPY.homeRediscovery.hint, { name: 'hint', color: t.color.textSecondary }), { fillW: true });
  const row = hScroll(ctx, past, 'rediscovery-cards');
  for (const item of REDISCOVERY.slice(0, 3)) add(row, rediscoveryCard(ctx, 'card', item.period, item.record));

  recent(ctx, content, 3);
  return root;
}

/** C. Rediscovery 중심 — the past is the hero; writing is a slim bar above the nav. */
function variantC(ctx: ScreenContext): FrameNode {
  const { t, lib } = ctx;
  const { root, content } = scaffold(ctx, {
    name: 'Home · C Rediscovery 중심',
    nav: 'home',
    footer: (r) => {
      const bar = frame({ name: 'composer', pad: { t: t.space.sm, b: t.space.sm, l: t.layout.screenPadding, r: t.layout.screenPadding }, fill: t.color.background });
      add(r, bar, { fillW: true });
      add(bar, lib.instance('Quick Record Entry', { variant: { size: 'bar' } }), { fillW: true });
    },
  });
  const s = section(ctx, content, 'greeting', t.space.xs, { pad: { t: t.space.md, b: 0, l: t.layout.screenPadding, r: t.layout.screenPadding } });
  add(s, text(t, 'label', fmtMonthDay(TODAY), { name: 'date', color: t.color.textTertiary, weight: 500 }));
  add(s, para(t, 'title', '1년 전 이맘때,\n이런 생각을 했어요', { name: 'title' }), { fillW: true });
  const hero = section(ctx, content, 'hero');
  add(hero, rediscoveryCard(ctx, 'hero', REDISCOVERY[2].period, REDISCOVERY[2].record), { fillW: true });
  const more = section(ctx, content, 'more', t.layout.listGap);
  sectionHeader(ctx, more, '이맘때의 다른 기록');
  for (const item of [REDISCOVERY[0], REDISCOVERY[3]]) add(more, rediscoveryCard(ctx, 'compact', item.period, item.record), { fillW: true });
  recent(ctx, content, 1);
  return root;
}

export const HOME_VARIANTS: Record<HomeVariant, { label: string; build: (ctx: ScreenContext) => FrameNode }> = {
  A: { label: '기록 입력 중심', build: variantA },
  B: { label: '기록 + Rediscovery 균형', build: variantB },
  C: { label: 'Rediscovery 중심', build: variantC },
};

export const HomeScreen: ScreenDef = {
  key: 'home',
  label: 'Home',
  build: (ctx) => HOME_VARIANTS[ctx.config.homeVariant].build(ctx),
};
