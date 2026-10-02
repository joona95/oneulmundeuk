// Helpers shared by component generators.
import { tint } from '../core/library';
import type { Library } from '../core/library';
import type { IconName } from '../core/icons';
import type { Emotion, EmotionSize } from '../emotions/types';
import type { Theme } from '../tokens/resolve';
import { configure, solid } from '../core/layout';
import type { FrameOpts } from '../core/layout';

export function comp(o: FrameOpts): ComponentNode {
  return configure(figma.createComponent(), o);
}

/**
 * Builds one component per variant value combination and combines them into a set.
 * `build` receives the variant props and must return a ComponentNode.
 */
export function variantSet(
  lib: Library,
  t: Theme,
  axes: Record<string, readonly string[]>,
  build: (props: Record<string, string>) => ComponentNode,
  opts: { columns?: number } = {},
): ComponentSetNode {
  const keys = Object.keys(axes);
  const combos: Record<string, string>[] = [{}];
  for (const k of keys) {
    const next: Record<string, string>[] = [];
    for (const c of combos) for (const v of axes[k]) next.push({ ...c, [k]: v });
    combos.splice(0, combos.length, ...next);
  }
  const comps = combos.map((props) => {
    const c = build(props);
    c.name = keys.map((k) => `${k}=${props[k]}`).join(', ');
    lib.container.appendChild(c);
    return c;
  });
  const set = figma.combineAsVariants(comps, lib.container);
  set.layoutMode = 'HORIZONTAL';
  set.layoutWrap = 'WRAP';
  set.itemSpacing = t.space.md;
  set.counterAxisSpacing = t.space.md;
  set.paddingTop = set.paddingBottom = set.paddingLeft = set.paddingRight = t.space.md;
  set.primaryAxisSizingMode = 'FIXED';
  set.counterAxisSizingMode = 'AUTO';
  const maxW = Math.max(...comps.map((c) => c.width));
  const cols = opts.columns ?? Math.min(comps.length, Math.max(1, Math.floor(960 / (maxW + t.space.md))));
  set.resize(cols * maxW + (cols - 1) * t.space.md + 2 * t.space.md, set.height);
  set.fills = [solid(t.color.background)];
  set.strokes = [solid(t.color.border)];
  set.cornerRadius = t.radius.md;
  return set;
}

/** Icon instance at a token size, tinted with a theme color. */
export function icon(lib: Library, name: IconName, color: string, size: number, layer = `icon/${name}`): InstanceNode {
  const inst = lib.icon(name).createInstance();
  inst.resize(size, size);
  tint(inst, color);
  inst.name = layer;
  return inst;
}

export function emotionInst(lib: Library, e: Emotion, size: EmotionSize, layer = 'emotion'): InstanceNode {
  const inst = lib.emotion(e, size).createInstance();
  inst.name = layer;
  return inst;
}

/** Size for "already chosen, just glancing" emotion markers: tiny in v3, regular before. */
export function markerSize(t: Theme): EmotionSize {
  return t.v3 ? 'xs' : 'sm';
}
