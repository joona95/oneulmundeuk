// Inputs & controls: Category Chip, Button, FAB, Search Bar, Segmented, Section Header,
// Emotion Option, Settings Row, Empty State.
import type { ComponentDef } from '../core/library';
import { add, ellipse, frame, para, shadows, solid, text } from '../core/layout';
import { comp, emotionInst, icon, variantSet } from './_util';

export const CategoryChip: ComponentDef = {
  name: 'Category Chip',
  build(t, lib) {
    return variantSet(lib, t, { state: ['default', 'selected'] }, (p) => {
      const on = p.state === 'selected';
      const c = comp({
          name: p.state, dir: 'H', minHeight: t.size.chip, pad: [t.space.xxs + t.space.hair, t.space.sm], gap: t.space.xxs,
          align: 'CENTER', radius: t.chipRadius,
          fill: on ? t.role.chipSelectedFill : t.color.surface,
          stroke: on ? t.role.chipSelectedStroke : t.color.border, strokeWeight: t.border.hairline,
        });
      add(c, text(t, 'label', '커리어', { name: 'label', color: on ? t.role.onChipSelected : t.color.textSecondary }));
      return c;
    });
  },
};

/** Small, read-only category tag used inside cards. */
export const CategoryTag: ComponentDef = {
  name: 'Category Tag',
  build(t) {
    // Long user-defined category names: the tag caps its width and the label truncates to one line.
    const c = comp({ name: 'Category Tag', dir: 'H', pad: [t.space.hair, t.space.xs], radius: t.chipRadius, fill: t.color.surfaceSecondary, align: 'CENTER', maxWidth: t.size.categoryTagMax });
    add(c, text(t, 'caption', '커리어', { name: 'label', color: t.color.textSecondary, maxLines: 1 }));
    return c;
  },
};

export const Button: ComponentDef = {
  name: 'Button',
  build(t, lib) {
    return variantSet(lib, t, { kind: ['primary', 'tonal', 'ghost'] }, (p) => {
      const fill = p.kind === 'primary' ? t.color.accent : p.kind === 'tonal' ? t.role.tonalFill : null;
      const fg = p.kind === 'primary' ? t.color.onAccent : p.kind === 'tonal' ? t.role.onTonal : t.color.textSecondary;
      const c = comp({
        name: p.kind, dir: 'H', minHeight: t.size.button, pad: [t.space.sm, t.space.xl], gap: t.space.xs,
        justify: 'CENTER', align: 'CENTER', radius: t.buttonRadius, fill,
        stroke: p.kind === 'ghost' ? t.color.border : undefined, strokeWeight: t.border.hairline,
      });
      add(c, text(t, 'label', '저장하기', { name: 'label', color: fg }));
      return c;
    });
  },
};

export const Fab: ComponentDef = {
  name: 'FAB',
  build(t, lib) {
    const c = comp({ name: 'FAB', dir: 'H', width: t.size.fab, height: t.size.fab, justify: 'CENTER', align: 'CENTER', radius: t.radius.lg, fill: t.color.accent });
    c.effects = shadows({ ...t, card: { ...t.card, shadows: [{ y: 4, blur: 12, opacity: 0.16, color: t.color.accent }] } });
    add(c, icon(lib, 'plus', t.color.onAccent, t.icon.lg));
    return c;
  },
};

export const SearchBar: ComponentDef = {
  name: 'Search Bar',
  build(t, lib) {
    return variantSet(lib, t, { state: ['empty', 'filled'] }, (p) => {
      const filled = p.state === 'filled';
      const c = comp({
        name: p.state, dir: 'H', width: t.size.viewportWidth - 2 * t.space.lg, height: t.size.searchBar,
        pad: [0, t.space.md], gap: t.space.sm, align: 'CENTER', radius: t.inputRadius,
        fill: filled ? t.color.surface : t.color.surfaceSecondary,
        stroke: filled ? t.role.focusStroke : undefined, strokeWeight: t.border.focus,
      });
      add(c, icon(lib, 'search', filled ? t.role.searchIconActive : t.color.textTertiary, t.icon.md));
      add(c, text(t, 'body', filled ? '이직에 대해 어떤 생각을 했었지?' : '무엇이든 물어보세요', {
        name: 'query', color: filled ? t.color.textPrimary : t.color.textTertiary, maxLines: 1,
      }), { fillW: true });
      if (filled) add(c, icon(lib, 'close', t.color.textTertiary, t.icon.md, 'clear'));
      return c;
    }, { columns: 1 });
  },
};

export const Segmented: ComponentDef = {
  name: 'Segmented',
  build(t, lib) {
    return variantSet(lib, t, { active: ['list', 'calendar'] }, (p) => {
      const c = comp({ name: p.active, dir: 'H', width: t.size.viewportWidth - 2 * t.space.lg, height: t.size.segmented, pad: t.space.xxs, gap: t.space.xxs, radius: t.radius.full, fill: t.color.surfaceSecondary });
      for (const [key, label, ic] of [['list', '목록', 'records'], ['calendar', '달력', 'calendar']] as const) {
        const on = key === p.active;
        const seg = add(c, frame({ name: `seg/${key}`, dir: 'H', gap: t.space.xs, justify: 'CENTER', align: 'CENTER', radius: t.radius.full, fill: on ? t.color.surface : null }), { fillW: true, fillH: true });
        if (on) seg.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.06 }, offset: { x: 0, y: 1 }, radius: 3, spread: 0, visible: true, blendMode: 'NORMAL' }];
        add(seg, icon(lib, ic, on ? t.color.textPrimary : t.color.textTertiary, t.icon.sm));
        add(seg, text(t, 'label', label, { name: 'label', color: on ? t.color.textPrimary : t.color.textTertiary }));
      }
      return c;
    }, { columns: 1 });
  },
};

export const SectionHeader: ComponentDef = {
  name: 'Section Header',
  build(t) {
    const c = comp({ name: 'Section Header', dir: 'H', width: t.size.viewportWidth - 2 * t.space.lg, justify: 'SPACE_BETWEEN', align: 'CENTER' });
    const left = add(c, frame({ name: 'left', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
    if (t.variant.sectionDot) add(left, ellipse(t.space.xs - 2, t.color.accent, 'dot'));
    add(left, text(t, 'heading', '다시 만난 생각', { name: 'title' }));
    add(c, text(t, 'label', '전체 보기', { name: 'action', color: t.color.textTertiary, weight: 500 }));
    return c;
  },
};

export const EmotionOption: ComponentDef = {
  name: 'Emotion Option',
  build(t, lib) {
    return variantSet(lib, t, { state: ['default', 'selected'] }, (p) => {
      const on = p.state === 'selected';
      if (!t.refined) {
        const c = comp({ name: p.state, gap: t.space.xxs, align: 'CENTER', pad: [t.space.xs, t.space.xxs], radius: t.radius.md, fill: on ? t.role.optionSelectedFill : null, width: t.size.calendarCell });
        add(c, emotionInst(lib, 'calm', 'md'));
        add(c, text(t, 'caption', '평온', { name: 'label', color: on ? t.role.onOptionSelected : t.color.textTertiary, weight: on ? 600 : 500 }));
        return c;
      }
      // v2: icon + label always; selection = a charcoal halo around the icon and a bold label.
      // v3 (Design Freeze): no black ring — a very subtle surface halo with a faint neutral edge, the
      // selected jelly marker one step larger, and a semibold label. The marker itself is the emphasis.
      // Width hugs the label so "그냥 그래" never overflows a fixed cell.
      const haloSize = t.v3 ? t.size.emotionSelected + t.space.xs : t.size.emotionMd + t.space.xs;
      const c = comp({ name: p.state, gap: t.space.xxs + t.space.hair, align: 'CENTER' });
      const halo = add(c, frame({
        name: 'halo', dir: 'H', width: haloSize, height: haloSize, justify: 'CENTER', align: 'CENTER', radius: t.radius.full,
        fill: on ? (t.v3 ? t.color.surfaceSecondary : t.role.optionSelectedFill) : null,
        stroke: on ? (t.v3 ? t.color.border : t.role.optionSelectedStroke) : undefined,
        strokeWeight: t.v3 ? t.border.hairline : t.border.focus,
      }));
      add(halo, emotionInst(lib, 'calm', on && t.v3 ? 'lg' : 'md'));
      add(c, text(t, 'caption', '평온', { name: 'label', color: on ? t.role.onOptionSelected : t.color.textSecondary, weight: on ? 600 : 500 }));
      return c;
    });
  },
};

export const SettingsRow: ComponentDef = {
  name: 'Settings Row',
  build(t, lib) {
    return variantSet(lib, t, { control: ['chevron', 'toggleOn', 'toggleOff', 'radioOn', 'radioOff', 'value'] }, (p) => {
      const c = comp({ name: p.control, dir: 'H', width: t.size.viewportWidth - 2 * t.space.lg, pad: [t.space.md, t.space.md], gap: t.space.sm, align: 'CENTER' });
      add(c, icon(lib, 'folder', t.color.textSecondary, t.icon.md, 'leading'));
      const labels = add(c, frame({ name: 'labels', gap: t.space.hair }), { fillW: true });
      add(labels, para(t, 'body', '카테고리 관리', { name: 'title' }), { fillW: true });
      add(labels, para(t, 'caption', '7개', { name: 'subtitle', color: t.color.textTertiary }), { fillW: true });
      if (p.control === 'chevron' || p.control === 'value') {
        if (p.control === 'value') add(c, text(t, 'bodySmall', '주 1회', { name: 'value', color: t.color.textSecondary }));
        add(c, icon(lib, 'chevronRight', t.color.textTertiary, t.icon.md, 'trailing'));
      } else if (p.control.startsWith('toggle')) {
        const on = p.control === 'toggleOn';
        const track = add(c, frame({ name: 'toggle', dir: 'H', width: t.size.toggleWidth, height: t.size.toggleHeight, pad: (t.size.toggleHeight - t.size.toggleThumb) / 2, radius: t.radius.full, justify: on ? 'MAX' : 'MIN', align: 'CENTER', fill: on ? t.color.accent : t.color.border }));
        add(track, ellipse(t.size.toggleThumb, t.color.surface, 'thumb'));
      } else {
        const on = p.control === 'radioOn';
        const outer = add(c, frame({ name: 'radio', dir: 'H', width: t.size.radioOuter, height: t.size.radioOuter, justify: 'CENTER', align: 'CENTER', radius: t.radius.full, stroke: on ? t.role.radioOn : t.color.textTertiary, strokeWeight: t.border.focus }));
        if (on) add(outer, ellipse(t.size.radioOuter / 2, t.role.radioOn, 'dot'));
      }
      return c;
    }, { columns: 1 });
  },
};

export const EmptyState: ComponentDef = {
  name: 'Empty State',
  build(t, lib) {
    const c = comp({ name: 'Empty State', width: t.size.viewportWidth - 2 * t.space.lg, pad: t.space.xxl, gap: t.space.sm, align: 'CENTER' });
    add(c, emotionInst(lib, 'neutral', 'md'));
    add(c, para(t, 'heading', '아직 이어질 기록이 없어요', { name: 'title', align: 'CENTER' }), { fillW: true });
    add(c, para(t, 'bodySmall', '오늘이 첫 페이지예요. 시간이 지나면 이 생각이 다시 찾아올 거예요.', { name: 'body', align: 'CENTER', color: t.color.textSecondary }), { fillW: true });
    c.fills = [solid(t.color.background, 0)];
    return c;
  },
};
