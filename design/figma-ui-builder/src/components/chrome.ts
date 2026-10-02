// System & navigation chrome: Status Bar, App Bar, Bottom Navigation.
import type { ComponentDef } from '../core/library';
import { add, frame, text, topBorder } from '../core/layout';
import type { IconName } from '../core/icons';
import type { Theme } from '../tokens/resolve';
import type { Library } from '../core/library';
import { comp, icon, variantSet } from './_util';

export const StatusBar: ComponentDef = {
  name: 'Status Bar',
  build(t, lib) {
    const c = comp({ name: 'Status Bar', dir: 'H', width: t.size.viewportWidth, height: t.size.statusBar, pad: [0, t.space.lg], justify: 'SPACE_BETWEEN', align: 'CENTER' });
    add(c, text(t, 'caption', '9:41', { name: 'time', weight: 600 }));
    const right = add(c, frame({ name: 'system', dir: 'H', gap: t.space.xxs, align: 'CENTER' }));
    for (const n of ['signal', 'wifi', 'battery'] as IconName[]) add(right, icon(lib, n, t.color.textPrimary, t.icon.sm));
    return c;
  },
};

function iconButton(t: Theme, lib: Library, name: IconName, layer: string, color = t.color.textPrimary): FrameNode {
  const b = frame({ name: layer, dir: 'H', width: t.size.iconButton, height: t.size.iconButton, justify: 'CENTER', align: 'CENTER', radius: t.radius.full });
  add(b, icon(lib, name, color, t.icon.lg));
  return b;
}

export const AppBar: ComponentDef = {
  name: 'App Bar',
  build(t, lib) {
    return variantSet(lib, t, { kind: ['title', 'back', 'close'] }, (p) => {
      const isTitle = p.kind === 'title';
      const c = comp({
        name: p.kind,
        dir: 'H',
        width: t.size.viewportWidth,
        height: t.size.appBar,
        pad: { t: 0, b: 0, l: isTitle ? t.layout.screenPadding : t.space.xxs, r: t.space.xs },
        gap: t.space.xxs,
        align: 'CENTER',
        fill: t.color.background,
      });
      if (!isTitle) add(c, iconButton(t, lib, p.kind === 'back' ? 'back' : 'close', 'leading'));
      add(c, text(t, isTitle ? 'title' : 'heading', isTitle ? '기록' : '새 기록', { name: 'title' }), { fillW: true });
      add(c, iconButton(t, lib, 'search', 'action1'));
      add(c, iconButton(t, lib, 'more', 'action2'));
      const save = add(c, frame({ name: 'action-text', dir: 'H', height: t.size.iconButton, pad: [0, t.space.sm], align: 'CENTER', radius: t.radius.full }));
      add(save, text(t, 'label', '저장', { name: 'action-label', color: t.color.accent }));
      return c;
    }, { columns: 1 });
  },
};

export const NAV_ITEMS = [
  { key: 'home', label: '홈', icon: 'home' },
  { key: 'records', label: '기록', icon: 'records' },
  { key: 'explore', label: '탐색', icon: 'explore' },
  { key: 'settings', label: '설정', icon: 'settings' },
] as const;
export type NavKey = (typeof NAV_ITEMS)[number]['key'];

export const BottomNav: ComponentDef = {
  name: 'Bottom Nav',
  build(t, lib) {
    const pill = t.variant.navIndicator === 'pill';
    return variantSet(lib, t, { active: NAV_ITEMS.map((i) => i.key) }, (p) => {
      const c = comp({
        name: p.active,
        dir: 'H',
        width: t.size.viewportWidth,
        height: t.size.bottomNav + t.size.gestureInset,
        pad: { t: t.space.xs, b: t.size.gestureInset, l: t.space.xs, r: t.space.xs },
        fill: t.color.surface,
        align: 'CENTER',
      });
      topBorder(c, t.color.border, t.border.hairline);
      for (const item of NAV_ITEMS) {
        const on = item.key === p.active;
        const cell = add(c, frame({ name: `tab/${item.key}`, gap: t.space.xxs, align: 'CENTER', justify: 'CENTER' }), { fillW: true });
        const ind = add(cell, frame({
          name: 'indicator', dir: 'H', width: t.size.navIndicatorWidth, height: t.size.navIndicatorHeight,
          justify: 'CENTER', align: 'CENTER', radius: t.radius.full,
          fill: on && pill ? t.role.navActiveFill : null,
        }));
        add(ind, icon(lib, item.icon, on ? (pill ? t.role.navActiveIcon : t.color.accent) : t.color.textTertiary, t.icon.lg));
        add(cell, text(t, 'caption', item.label, { name: 'label', color: on ? t.color.textPrimary : t.color.textTertiary, weight: on ? 600 : 500 }));
      }
      return c;
    }, { columns: 1 });
  },
};
