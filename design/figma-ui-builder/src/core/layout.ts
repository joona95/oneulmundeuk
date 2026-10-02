// Low-level node helpers. Every visual value passed in here must come from the Theme.
import type { Theme } from '../tokens/resolve';
import type { TypeKey, Weight } from '../tokens/base';
import { font } from './fonts';

export function rgb(hex: string): RGB {
  const h = hex.replace('#', '');
  const n = parseInt(h.length === 3 ? h.split('').map((c) => c + c).join('') : h, 16);
  return { r: ((n >> 16) & 255) / 255, g: ((n >> 8) & 255) / 255, b: (n & 255) / 255 };
}

export function solid(hex: string, opacity = 1): SolidPaint {
  return { type: 'SOLID', color: rgb(hex), opacity };
}

export type Dir = 'V' | 'H';
export type Pad = number | [number, number] | { t: number; r: number; b: number; l: number };

export interface FrameOpts {
  name: string;
  dir?: Dir;
  gap?: number;
  pad?: Pad;
  fill?: string | null;
  fillOpacity?: number;
  radius?: number;
  stroke?: string;
  strokeWeight?: number;
  /** Primary-axis alignment. */
  justify?: 'MIN' | 'CENTER' | 'MAX' | 'SPACE_BETWEEN';
  /** Counter-axis alignment. */
  align?: 'MIN' | 'CENTER' | 'MAX' | 'BASELINE';
  width?: number; // fixed width (otherwise hug)
  height?: number; // fixed height (otherwise hug)
  /** Hug height but never shorter than this (keeps tap targets, grows with font scale). */
  minHeight?: number;
  /** Hug width but never wider than this (long user-defined labels). */
  maxWidth?: number;
  clip?: boolean;
}

function applyPad(f: FrameNode | ComponentNode, pad: Pad | undefined) {
  if (pad === undefined) return;
  if (typeof pad === 'number') {
    f.paddingTop = f.paddingBottom = f.paddingLeft = f.paddingRight = pad;
  } else if (Array.isArray(pad)) {
    f.paddingTop = f.paddingBottom = pad[0];
    f.paddingLeft = f.paddingRight = pad[1];
  } else {
    f.paddingTop = pad.t;
    f.paddingRight = pad.r;
    f.paddingBottom = pad.b;
    f.paddingLeft = pad.l;
  }
}

/** Configures an auto-layout frame/component in place. */
export function configure<T extends FrameNode | ComponentNode>(f: T, o: FrameOpts): T {
  f.name = o.name;
  f.layoutMode = o.dir === 'H' ? 'HORIZONTAL' : 'VERTICAL';
  f.itemSpacing = o.gap ?? 0;
  applyPad(f, o.pad);
  f.primaryAxisAlignItems = o.justify ?? 'MIN';
  f.counterAxisAlignItems = o.align ?? 'MIN';
  f.fills = o.fill ? [solid(o.fill, o.fillOpacity ?? 1)] : [];
  if (o.radius !== undefined) f.cornerRadius = o.radius;
  if (o.stroke && (o.strokeWeight ?? 1) > 0) {
    f.strokes = [solid(o.stroke)];
    f.strokeWeight = o.strokeWeight ?? 1;
    f.strokeAlign = 'INSIDE';
  } else {
    f.strokes = [];
  }
  f.clipsContent = o.clip ?? false;
  // Sizing: fixed when a dimension is given, hug otherwise.
  const horizontal = f.layoutMode === 'HORIZONTAL';
  const w = o.width ?? 100;
  const h = o.height ?? 100;
  f.resize(w, h);
  const primaryFixed = horizontal ? o.width !== undefined : o.height !== undefined;
  const counterFixed = horizontal ? o.height !== undefined : o.width !== undefined;
  f.primaryAxisSizingMode = primaryFixed ? 'FIXED' : 'AUTO';
  f.counterAxisSizingMode = counterFixed ? 'FIXED' : 'AUTO';
  if (o.minHeight !== undefined) f.minHeight = o.minHeight;
  if (o.maxWidth !== undefined) f.maxWidth = o.maxWidth;
  return f;
}

export function frame(o: FrameOpts): FrameNode {
  return configure(figma.createFrame(), o);
}

export interface PlaceOpts {
  fillW?: boolean;
  fillH?: boolean;
  /** Absolute positioning inside an auto-layout parent. */
  absolute?: { x: number; y: number };
}

/** Appends a child and applies sizing that is only legal after it has an auto-layout parent. */
export function add<T extends SceneNode>(parent: FrameNode | ComponentNode | InstanceNode, child: T, p: PlaceOpts = {}): T {
  parent.appendChild(child);
  const c = child as unknown as FrameNode;
  if (p.absolute) {
    c.layoutPositioning = 'ABSOLUTE';
    c.x = p.absolute.x;
    c.y = p.absolute.y;
    return child;
  }
  if (p.fillW) c.layoutSizingHorizontal = 'FILL';
  if (p.fillH) c.layoutSizingVertical = 'FILL';
  return child;
}

export interface TextOpts {
  name?: string;
  color?: string;
  weight?: Weight;
  align?: 'LEFT' | 'CENTER' | 'RIGHT';
  maxLines?: number;
}

export function text(t: Theme, style: TypeKey, chars: string, o: TextOpts = {}): TextNode {
  const s = t.type[style];
  const node = figma.createText();
  node.fontName = font(o.weight ?? s.weight);
  node.fontSize = s.size;
  node.lineHeight = { value: s.lineHeight, unit: 'PIXELS' };
  node.letterSpacing = { value: s.letterSpacing, unit: 'PERCENT' };
  node.characters = chars;
  node.name = o.name ?? style;
  node.fills = [solid(o.color ?? t.color.textPrimary)];
  node.textAlignHorizontal = o.align ?? 'LEFT';
  node.textAutoResize = 'WIDTH_AND_HEIGHT';
  if (o.maxLines) {
    node.textTruncation = 'ENDING'; // must precede maxLines
    node.maxLines = o.maxLines;
  }
  return node;
}

/** Text that wraps to its parent's width. Use with add(parent, node, { fillW: true }). */
export function para(t: Theme, style: TypeKey, chars: string, o: TextOpts = {}): TextNode {
  const node = text(t, style, chars, o);
  node.textAutoResize = 'HEIGHT';
  return node;
}

export function shadows(t: Theme): DropShadowEffect[] {
  return t.card.shadows.map((s) => ({
    type: 'DROP_SHADOW',
    color: { ...rgb(s.color), a: s.opacity },
    offset: { x: 0, y: s.y },
    radius: s.blur,
    spread: 0,
    visible: true,
    blendMode: 'NORMAL',
  }));
}

/** Applies the theme's card surface (fill, border, elevation, radius). */
export function cardSurface<T extends FrameNode | ComponentNode>(f: T, t: Theme, opts: { hero?: boolean; fill?: string } = {}): T {
  f.fills = [solid(opts.fill ?? t.card.fill)];
  f.cornerRadius = opts.hero ? t.card.heroRadius : t.card.radius;
  if (t.card.strokeWeight > 0) {
    f.strokes = [solid(t.card.stroke)];
    f.strokeWeight = t.card.strokeWeight;
    f.strokeAlign = 'INSIDE';
  } else {
    f.strokes = [];
  }
  f.effects = shadows(t);
  return f;
}

export function ellipse(diameter: number, color: string, name = 'dot'): EllipseNode {
  const e = figma.createEllipse();
  e.name = name;
  e.resize(diameter, diameter);
  e.fills = [solid(color)];
  return e;
}

export function rect(w: number, h: number, color: string, r = 0, name = 'rect'): RectangleNode {
  const n = figma.createRectangle();
  n.name = name;
  n.resize(w, h);
  n.fills = [solid(color)];
  n.cornerRadius = r;
  return n;
}

/** Flexible spacer for SPACE_BETWEEN-less layouts. */
export function spacer(name = 'spacer'): FrameNode {
  const f = figma.createFrame();
  f.name = name;
  f.fills = [];
  f.resize(1, 1);
  return f;
}

/** Single top hairline (dividers between rows, sheet edges). */
export function topBorder(f: FrameNode | ComponentNode, color: string, weight: number) {
  f.strokes = [solid(color)];
  f.strokeAlign = 'INSIDE';
  f.strokeTopWeight = weight;
  f.strokeBottomWeight = 0;
  f.strokeLeftWeight = 0;
  f.strokeRightWeight = 0;
}
