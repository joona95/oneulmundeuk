// Records tab: List and Calendar views.
import { add, ellipse, frame, text } from '../core/layout';
import { icon } from '../components/_util';
import { CALENDAR, CATEGORIES, RECORDS, fmtAgo, fmtMonthDay } from '../data/sample';
import type { SampleRecord } from '../data/sample';
import { appBar, chip, hScroll, recordCard, scaffold, section } from './shared';
import type { ScreenContext, ScreenDef } from './shared';

function header(ctx: ScreenContext, content: FrameNode, active: 'list' | 'calendar') {
  const { t, lib } = ctx;
  const top = section(ctx, content, 'view-switch', t.space.md);
  add(top, lib.instance('Segmented', { variant: { active } }), { fillW: true });
  if (active === 'list') {
    const chips = hScroll(ctx, content, 'filters', t.space.xs);
    add(chips, chip(ctx, '전체', true));
    for (const c of CATEGORIES) add(chips, chip(ctx, c));
  }
}

function dayLabel(r: SampleRecord): string {
  const ago = fmtAgo(r.at);
  return ago === '오늘' || ago === '어제' ? `${ago} · ${fmtMonthDay(r.at)}` : fmtMonthDay(r.at);
}

export const RecordsListScreen: ScreenDef = {
  key: 'recordsList',
  label: 'Records — List',
  build(ctx) {
    const { t } = ctx;
    const { root, content } = scaffold(ctx, { name: 'Records — List', nav: 'records', fab: true, appBar: appBar(ctx, 'title', '기록', { search: true }) });
    content.itemSpacing = t.space.lg;
    header(ctx, content, 'list');
    const groups = new Map<string, SampleRecord[]>();
    for (const r of RECORDS.slice(0, 8)) {
      const k = dayLabel(r);
      groups.set(k, [...(groups.get(k) ?? []), r]);
    }
    for (const [label, items] of groups) {
      const g = section(ctx, content, `day/${label}`, t.layout.listGap);
      add(g, text(t, 'label', label, { name: 'day', color: t.color.textSecondary }));
      for (const r of items) add(g, recordCard(ctx, r), { fillW: true });
    }
    return root;
  },
};

export const RecordsCalendarScreen: ScreenDef = {
  key: 'recordsCalendar',
  label: 'Records — Calendar',
  build(ctx) {
    const { t, lib } = ctx;
    const { root, content } = scaffold(ctx, { name: 'Records — Calendar', nav: 'records', fab: true, appBar: appBar(ctx, 'title', '기록', { search: true }) });
    content.itemSpacing = t.space.lg;
    header(ctx, content, 'calendar');

    const cal = section(ctx, content, 'calendar', t.space.xs);
    const month = add(cal, frame({ name: 'month', dir: 'H', align: 'CENTER', justify: 'SPACE_BETWEEN', pad: [t.space.xxs, 0] }), { fillW: true });
    add(month, icon(lib, 'chevronLeft', t.color.textSecondary, t.icon.md, 'prev'));
    add(month, text(t, 'heading', `${CALENDAR.year}년 ${CALENDAR.month}월`, { name: 'month-label' }));
    add(month, icon(lib, 'chevronRight', t.color.textSecondary, t.icon.md, 'next'));

    const week = add(cal, frame({ name: 'weekdays', dir: 'H' }), { fillW: true });
    ['일', '월', '화', '수', '목', '금', '토'].forEach((d, i) => {
      const cell = add(week, frame({ name: d, dir: 'H', justify: 'CENTER', pad: [t.space.xxs, 0] }), { fillW: true });
      add(cell, text(t, 'caption', d, { name: 'wd', color: i === 0 ? t.color.emotion.excited.ink : t.color.textTertiary }));
    });

    const first = new Date(Date.UTC(CALENDAR.year, CALENDAR.month - 1, 1)).getUTCDay();
    const daysInMonth = new Date(Date.UTC(CALENDAR.year, CALENDAR.month, 0)).getUTCDate();
    const cells = Math.ceil((first + daysInMonth) / 7) * 7;
    const grid = add(cal, frame({ name: 'grid', gap: t.space.hair }), { fillW: true });
    let row: FrameNode | null = null;
    for (let i = 0; i < cells; i++) {
      if (i % 7 === 0) row = add(grid, frame({ name: `week-${i / 7 + 1}`, dir: 'H' }), { fillW: true });
      const day = i - first + 1;
      const inMonth = day >= 1 && day <= daysInMonth;
      const selected = day === CALENDAR.selectedDay;
      const cell = add(row!, frame({ name: inMonth ? `day-${day}` : 'empty', height: t.size.calendarCell + t.space.xs, align: 'CENTER', gap: t.space.hair }), { fillW: true });
      if (!inMonth) continue;
      const num = add(cell, frame({ name: 'num', dir: 'H', width: t.size.sendButton - t.space.xxs, height: t.size.sendButton - t.space.xxs, justify: 'CENTER', align: 'CENTER', radius: t.radius.full, fill: selected ? t.role.calendarSelectedFill : null }));
      add(num, text(t, 'bodySmall', String(day), {
        name: 'n',
        weight: selected ? 600 : 400,
        color: selected ? t.role.onCalendarSelected : CALENDAR.days[day] ? t.color.textPrimary : t.color.textTertiary,
      }));
      const dots = add(cell, frame({ name: 'emotions', dir: 'H', gap: t.space.hair, height: t.size.emotionXs }));
      for (const e of (CALENDAR.days[day] ?? []).slice(0, 3)) add(dots, ellipse(t.size.emotionXs - t.space.hair, t.color.emotion[e].fill, e));
    }

    const list = section(ctx, content, 'selected-day', t.layout.listGap);
    const sel = CALENDAR.selectedRecords[0];
    const lbl = add(list, frame({ name: 'day-header', dir: 'H', justify: 'SPACE_BETWEEN', align: 'CENTER' }), { fillW: true });
    add(lbl, text(t, 'label', fmtMonthDay(sel.at), { name: 'day', color: t.color.textSecondary }));
    add(lbl, text(t, 'caption', `${CALENDAR.selectedRecords.length}개의 기록`, { name: 'count', color: t.color.textTertiary }));
    for (const r of CALENDAR.selectedRecords) add(list, recordCard(ctx, r), { fillW: true });
    return root;
  },
};
