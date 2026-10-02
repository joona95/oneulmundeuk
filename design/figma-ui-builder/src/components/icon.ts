// Icon — a component set with one variant per icon name. Instances are recolored with `tint()`.
import { ICONS, iconSvg } from '../core/icons';
import type { IconName } from '../core/icons';
import type { ComponentDef } from '../core/library';
import { comp, variantSet } from './_util';

function scaleAll(n: SceneNode) {
  if ('constraints' in n) n.constraints = { horizontal: 'SCALE', vertical: 'SCALE' };
  if ('children' in n) n.children.forEach(scaleAll);
}

export const Icon: ComponentDef = {
  name: 'Icon',
  build(t, lib) {
    const size = t.icon.lg;
    return variantSet(lib, t, { name: Object.keys(ICONS) }, (p) => {
      const c = comp({ name: p.name, width: size, height: size });
      c.layoutMode = 'NONE';
      c.fills = [];
      const glyph = figma.createNodeFromSvg(iconSvg(p.name as IconName, t.color.textSecondary, size));
      glyph.name = 'glyph';
      glyph.fills = [];
      c.appendChild(glyph);
      glyph.x = 0;
      glyph.y = 0;
      scaleAll(glyph);
      return c;
    }, { columns: 12 });
  },
};
