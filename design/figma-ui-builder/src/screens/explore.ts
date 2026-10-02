// Explore tab: "과거의 나에게 물어보세요" + Semantic Search Results.
import { COPY } from '../data/copy';
import { add, cardSurface, frame, para, text, topBorder } from '../core/layout';
import { icon } from '../components/_util';
import { CATEGORIES, EXAMPLE_QUERIES, SEARCH } from '../data/sample';
import { chip, recordCard, scaffold, section } from './shared';
import type { ScreenDef } from './shared';

export const ExploreScreen: ScreenDef = {
  key: 'explore',
  label: 'Explore',
  build(ctx) {
    const { t, lib } = ctx;
    const { root, content } = scaffold(ctx, { name: 'Explore', nav: 'explore' });

    const hero = section(ctx, content, 'hero', t.space.sm, { pad: { t: t.space.xl, b: 0, l: t.layout.screenPadding, r: t.layout.screenPadding } });
    add(hero, para(t, t.variant.heroType, COPY.explore.title, { name: 'title' }), { fillW: true });
    add(hero, para(t, 'bodySmall', '단어가 아니라 의미로 찾아요. 정확한 표현이 기억나지 않아도 괜찮아요.', { name: 'sub', color: t.color.textSecondary }), { fillW: true });
    add(hero, lib.instance('Search Bar', { variant: { state: 'empty' }, text: { query: '무엇이든 물어보세요' } }), { fillW: true });
    const privacy = add(hero, frame({ name: 'privacy', dir: 'H', gap: t.space.xxs, align: 'CENTER' }));
    add(privacy, icon(lib, 'lock', t.color.textTertiary, t.icon.sm));
    add(privacy, text(t, 'caption', '검색은 이 기기 안에서만 이루어져요', { name: 'note', color: t.color.textTertiary }));

    const ex = section(ctx, content, 'examples', t.space.sm);
    add(ex, text(t, 'heading', '이렇게 물어볼 수 있어요', { name: 'title' }));
    const list = add(ex, cardSurface(frame({ name: 'queries' }), t), { fillW: true });
    EXAMPLE_QUERIES.forEach((q, i) => {
      const row = add(list, frame({ name: `query/${i + 1}`, dir: 'H', gap: t.space.sm, align: 'CENTER', pad: [t.space.md, t.layout.cardPadding] }), { fillW: true });
      if (i > 0) topBorder(row, t.color.border, t.border.hairline);
      add(row, icon(lib, 'search', t.role.metaLabel, t.icon.sm));
      add(row, para(t, 'body', q, { name: 'q' }), { fillW: true });
      add(row, icon(lib, 'arrowRight', t.color.textTertiary, t.icon.sm));
    });

    const topics = section(ctx, content, 'topics', t.space.sm);
    add(topics, text(t, 'heading', '자주 등장한 주제', { name: 'title' }));
    const wrap = add(topics, frame({ name: 'chips', dir: 'H', gap: t.space.xs }), { fillW: true });
    wrap.layoutWrap = 'WRAP';
    wrap.counterAxisSpacing = t.space.xs;
    for (const c of CATEGORIES) add(wrap, chip(ctx, c));
    return root;
  },
};

export const SearchResultsScreen: ScreenDef = {
  key: 'searchResults',
  label: 'Semantic Search Results',
  build(ctx) {
    const { t, lib } = ctx;
    const { root, content } = scaffold(ctx, { name: 'Semantic Search Results', nav: 'explore' });
    content.itemSpacing = t.space.lg;

    const top = section(ctx, content, 'search', t.space.md, { pad: { t: t.space.xs, b: 0, l: t.space.xs, r: t.layout.screenPadding } });
    top.layoutMode = 'HORIZONTAL';
    top.counterAxisAlignItems = 'CENTER';
    top.itemSpacing = t.space.xxs;
    const back = add(top, frame({ name: 'back', dir: 'H', width: t.size.iconButton, height: t.size.iconButton, justify: 'CENTER', align: 'CENTER' }));
    add(back, icon(lib, 'back', t.color.textPrimary, t.icon.lg));
    add(top, lib.instance('Search Bar', { variant: { state: 'filled' }, text: { query: SEARCH.query } }), { fillW: true });

    const summary = section(ctx, content, 'summary', t.space.sm);
    const line = add(summary, frame({ name: 'line', dir: 'H', justify: 'SPACE_BETWEEN', align: 'CENTER' }), { fillW: true });
    add(line, text(t, 'label', `${SEARCH.total}개의 기록을 찾았어요`, { name: 'count', color: t.color.textSecondary }));
    const sort = add(line, frame({ name: 'sort', dir: 'H', gap: t.space.xxs }));
    add(sort, chip(ctx, '관련도순', true));
    add(sort, chip(ctx, '시간순'));

    const list = section(ctx, content, 'results', t.layout.listGap);
    for (const r of SEARCH.results) add(list, recordCard(ctx, r.record, { highlight: r.match, maxLines: 3 }), { fillW: true });
    return root;
  },
};
