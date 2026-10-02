// Record Detail and Reminder Notification (lock-screen preview).
import { add, frame, para, solid, text, topBorder } from '../core/layout';
import { tint } from '../core/library';
import { emotionInst, icon, markerSize } from '../components/_util';
import { EMOTION_LABEL } from '../emotions/types';
import { DETAIL, REDISCOVERY, fmtAgo, fmtDate, fmtTime } from '../data/sample';
import { appBar, chip, emotionTint, relatedCard, scaffold, section, sectionHeader } from './shared';
import type { ScreenDef } from './shared';

export const RecordDetailScreen: ScreenDef = {
  key: 'recordDetail',
  label: 'Record Detail',
  build(ctx) {
    const { t, lib } = ctx;
    const r = DETAIL.record;
    const { root, content } = scaffold(ctx, {
      name: 'Record Detail',
      appBar: appBar(ctx, 'back', '', { more: true }),
      footer: (rt) => {
        const bar = frame({ name: 'actions', pad: { t: t.space.sm, b: t.space.sm + t.size.gestureInset, l: t.layout.screenPadding, r: t.layout.screenPadding }, fill: t.color.background });
        topBorder(bar, t.color.border, t.border.hairline);
        add(rt, bar, { fillW: true });
        add(bar, lib.instance('Button', { variant: { kind: 'tonal' }, text: { label: '지금의 생각 덧붙이기' } }), { fillW: true });
      },
    });

    const outer = section(ctx, content, 'header', t.space.sm, { pad: { t: t.space.xs, b: 0, l: t.layout.screenPadding, r: t.layout.screenPadding } });
    // v3: the detail hero gets the record's emotion as a ~6% wash; earlier versions stay plain.
    const head = t.v3 && r.emotion
      ? add(outer, frame({ name: 'hero', gap: t.space.sm, pad: t.space.lg, radius: t.card.heroRadius }), { fillW: true })
      : outer;
    if (t.v3 && r.emotion) head.fills = emotionTint(t, r.emotion, t.emotionTint - 0.01);
    add(head, para(t, 'title', fmtDate(r.at, true), { name: 'date' }), { fillW: true });
    const meta = add(head, frame({ name: 'meta', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
    if (r.emotion) {
      const pill = add(meta, frame({ name: 'emotion-pill', dir: 'H', gap: t.space.xxs, align: 'CENTER', pad: { t: t.space.xxs, b: t.space.xxs, l: t.space.xxs, r: t.space.sm }, radius: t.radius.full, fill: t.v3 ? t.color.surface : t.color.surfaceSecondary }));
      add(pill, emotionInst(lib, r.emotion, 'sm'));
      add(pill, text(t, 'caption', EMOTION_LABEL[r.emotion], { name: 'emotion-label', color: t.color.textSecondary, weight: 600 }));
    }
    if (r.category) {
      const tag = lib.instance('Category Tag', { text: { label: r.category } });
      add(meta, tag);
    }
    add(meta, text(t, 'caption', fmtTime(r.at), { name: 'time', color: t.color.textTertiary }));

    const body = section(ctx, content, 'body', t.space.md);
    add(body, para(t, 'bodyLarge', r.body + '\n\n지금 당장 결정할 필요는 없다. 다만 다음 달까지 "여기서 더 배우고 싶은 것" 세 가지를 적어보고, 그게 안 써지면 그때 다시 생각해보자.', { name: 'text' }), { fillW: true });
    // Tags are post-MVP (v1.1): shown only in the v1 design.
    if (!t.refined && r.tags?.length) {
      const tags = add(body, frame({ name: 'tags', dir: 'H', gap: t.space.xs }), { fillW: true });
      for (const tg of r.tags) add(tags, chip(ctx, `#${tg}`));
    }

    const rel = section(ctx, content, 'related', t.space.md);
    sectionHeader(ctx, rel, '이어지는 기록');
    const list = add(rel, frame({ name: 'timeline' }), { fillW: true });
    if (t.v3) {
      // v3: same quiet Memory Entry rhythm as Related Memories — no rail, no cards.
      list.itemSpacing = t.space.xl;
      for (const x of DETAIL.related) {
        add(list, lib.instance('Memory Entry', {
          text: { when: fmtAgo(x.at), date: fmtDate(x.at), quote: x.body, category: x.category ?? '' },
          swap: x.emotion ? { emotion: lib.emotion(x.emotion, markerSize(t)) } : undefined,
          hide: [...(x.emotion ? [] : ['emotion']), ...(x.category ? [] : ['category'])],
        }), { fillW: true });
      }
    } else {
      DETAIL.related.forEach((x, i) => add(list, relatedCard(ctx, x, i === DETAIL.related.length - 1), { fillW: true }));
    }
    return root;
  },
};

export const ReminderScreen: ScreenDef = {
  key: 'reminder',
  label: 'Reminder Notification',
  build(ctx) {
    const { t, lib } = ctx;
    const item = REDISCOVERY[2];
    const root = frame({ name: 'Reminder Notification', width: t.size.viewportWidth, height: t.size.viewportHeight, fill: t.color.textPrimary, gap: t.space.xxl, clip: true });
    // Lock screen sits on a dark background: invert the status bar ink.
    const sb = add(root, lib.instance('Status Bar'), { fillW: true });
    tint(sb, t.color.surface);
    (sb.findOne((n) => n.name === 'time') as TextNode).fills = [solid(t.color.surface)];
    const clock = add(root, frame({ name: 'clock', align: 'CENTER', gap: t.space.xxs, pad: [t.space.xxxl, 0] }), { fillW: true });
    add(clock, text(t, 'caption', fmtDate('2026-10-04', true), { name: 'date', color: t.color.surface }));
    const big = add(clock, text(t, 'display', '21:00', { name: 'time', color: t.color.surface, weight: 400 }));
    big.fontSize = t.type.display.size * 2;
    big.lineHeight = { value: t.type.display.lineHeight * 2, unit: 'PIXELS' };

    const stack = add(root, frame({ name: 'notifications', gap: t.space.xs, pad: [0, t.space.xs + t.space.xxs] }), { fillW: true });
    add(stack, lib.instance('Notification', {
      text: { title: `${item.period}의 생각`, body: `“${item.record.body}”` },
      swap: item.record.emotion ? { emotion: lib.emotion(item.record.emotion, 'md') } : undefined,
    }), { fillW: true });
    const note = add(stack, frame({ name: 'private-note', dir: 'H', gap: t.space.xxs, align: 'CENTER', pad: [t.space.xs, t.space.xs] }));
    add(note, icon(lib, 'lock', t.color.textTertiary, t.icon.sm));
    add(note, text(t, 'caption', '잠금 화면에서는 “다시 만날 생각이 있어요”로 가려서 표시돼요', { name: 'note', color: t.color.textTertiary }));
    return root;
  },
};
