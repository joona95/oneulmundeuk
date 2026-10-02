// Screen scaffolding and record → instance mappers shared by all screen generators.
import type { Library } from '../core/library';
import { add, frame, solid, text } from '../core/layout';
import { font } from '../core/fonts';
import type { FrameOpts } from '../core/layout';
import type { Theme } from '../tokens/resolve';
import type { BuilderConfig, ScreenKey } from '../types';
import type { NavKey } from '../components';
import { markerSize } from '../components/_util';
import { fmtAgo, fmtDate, fmtMeta } from '../data/sample';
import type { SampleRecord } from '../data/sample';
import type { Emotion } from '../emotions/types';

export interface ScreenContext {
  t: Theme;
  lib: Library;
  config: BuilderConfig;
}

export interface ScreenDef {
  key: ScreenKey;
  label: string;
  build(ctx: ScreenContext): FrameNode;
}

export interface Scaffold {
  root: FrameNode;
  /** Vertical scroll area. Has NO side padding: use section() for padded blocks. */
  content: FrameNode;
}

export interface ScaffoldOpts {
  name: string;
  nav?: NavKey;
  appBar?: InstanceNode;
  fab?: boolean;
  /** Fixed block between the scroll area and the nav (e.g. editor panel, action bar). */
  footer?: (root: FrameNode) => void;
  fill?: string;
}

export function scaffold(ctx: ScreenContext, o: ScaffoldOpts): Scaffold {
  const { t, lib } = ctx;
  const root = frame({ name: o.name, width: t.size.viewportWidth, height: t.size.viewportHeight, fill: o.fill ?? t.color.background, clip: true });
  add(root, lib.instance('Status Bar'), { fillW: true });
  if (o.appBar) add(root, o.appBar, { fillW: true });
  const content = add(root, frame({ name: 'content', gap: t.layout.sectionGap, pad: { t: t.space.xs, b: t.space.xxl, l: 0, r: 0 }, clip: true }), { fillW: true, fillH: true });
  content.overflowDirection = 'VERTICAL';
  if (o.footer) o.footer(root);
  if (o.nav) add(root, lib.instance('Bottom Nav', { variant: { active: o.nav } }), { fillW: true });
  if (o.fab) {
    const fab = lib.instance('FAB');
    const navH = o.nav ? t.size.bottomNav + t.size.gestureInset : t.size.gestureInset;
    add(root, fab, {
      absolute: {
        x: t.size.viewportWidth - t.size.fab - t.layout.screenPadding,
        y: t.size.viewportHeight - navH - t.size.fab - t.space.md,
      },
    });
  }
  return { root, content };
}

/** A padded vertical block inside the scroll area. */
export function section(ctx: ScreenContext, parent: FrameNode, name: string, gap?: number, extra: Partial<FrameOpts> = {}): FrameNode {
  const { t } = ctx;
  return add(parent, frame({ name, gap: gap ?? t.space.sm, pad: [0, t.layout.screenPadding], ...extra }), { fillW: true });
}

/** Horizontally scrolling row that bleeds past the right screen edge. */
export function hScroll(ctx: ScreenContext, parent: FrameNode, name: string, gap?: number): FrameNode {
  const { t } = ctx;
  const row = add(parent, frame({ name, dir: 'H', gap: gap ?? t.space.sm, pad: [0, t.layout.screenPadding] }), { fillW: true });
  row.overflowDirection = 'HORIZONTAL';
  row.clipsContent = true;
  return row;
}

export function sectionHeader(ctx: ScreenContext, parent: FrameNode, title: string, action?: string): InstanceNode {
  const inst = ctx.lib.instance('Section Header', { text: { title, action: action ?? '' }, hide: action ? [] : ['action'] });
  return add(parent, inst, { fillW: true });
}

export function appBar(ctx: ScreenContext, kind: 'title' | 'back' | 'close', title: string, show: { search?: boolean; more?: boolean; textAction?: string } = {}): InstanceNode {
  const hide: string[] = [];
  if (!show.search) hide.push('action1');
  if (!show.more) hide.push('action2');
  if (!show.textAction) hide.push('action-text');
  const textOv: Record<string, string> = { title };
  if (show.textAction) textOv['action-label'] = show.textAction;
  return ctx.lib.instance('App Bar', { variant: { kind }, text: textOv, hide });
}

/** Fills a Record Card instance from a sample record. */
export function recordCard(ctx: ScreenContext, r: SampleRecord, o: { highlight?: string; maxLines?: number } = {}): InstanceNode {
  const { lib, t } = ctx;
  const inst = lib.instance('Record Card', {
    text: { time: fmtMeta(r.at), body: r.body, ...(r.category ? { label: r.category } : {}) },
    swap: r.emotion ? { emotion: lib.emotion(r.emotion, markerSize(t)) } : undefined,
    hide: [...(r.emotion ? [] : ['emotion']), ...(r.category ? [] : ['category'])],
    show: r.photo ? ['photo'] : [],
  });
  const body = inst.findOne((n) => n.type === 'TEXT' && n.name === 'body') as TextNode;
  if (o.maxLines) body.maxLines = o.maxLines;
  if (o.highlight) highlight(t, body, o.highlight);
  return inst;
}

/** Emphasizes the semantically matched phrase (semibold + accent ink). No background boxes. */
export function highlight(t: Theme, node: TextNode, phrase: string) {
  const i = node.characters.indexOf(phrase);
  if (i < 0) return;
  node.setRangeFills(i, i + phrase.length, [solid(t.role.matchInk)]);
  node.setRangeFontName(i, i + phrase.length, font(600));
}

export function rediscoveryCard(ctx: ScreenContext, style: string, period: string, r: SampleRecord): InstanceNode {
  const { lib } = ctx;
  const texts: Record<string, string> = { period, quote: r.body };
  if (style !== 'compact') texts.date = fmtDate(r.at);
  if (style !== 'compact' && r.category) texts.label = r.category;
  const inst = lib.instance('Rediscovery Card', {
    variant: { style },
    text: texts,
    swap: r.emotion ? { emotion: lib.emotion(r.emotion, style === 'compact' ? 'md' : markerSize(ctx.t)) } : undefined,
    hide: style !== 'compact' && !r.category ? ['category'] : [],
  });
  // v3: re-meeting the past is one of the few moments color is allowed to surface — a 6–7% tint.
  if (ctx.t.v3 && r.emotion) inst.fills = emotionTint(ctx.t, r.emotion);
  return inst;
}

/** Surface + a very faint (≈7%) wash of the emotion color. Only for rediscovery / related / detail hero. */
export function emotionTint(t: Theme, e: Emotion, strength = t.emotionTint): Paint[] {
  return [solid(t.color.surface), solid(t.color.emotion[e].fill, strength)];
}

export function relatedCard(ctx: ScreenContext, r: SampleRecord, last: boolean): InstanceNode {
  const { lib, t } = ctx;
  return lib.instance('Related Memory Card', {
    text: { when: fmtAgo(r.at), quote: r.body, date: fmtDate(r.at), ...(r.category ? { label: r.category } : {}) },
    swap: r.emotion ? { emotion: lib.emotion(r.emotion, markerSize(t)) } : undefined,
    hide: [...(last ? ['line'] : []), ...(r.category ? [] : ['category'])],
  });
}

export function chip(ctx: ScreenContext, label: string, selected = false): InstanceNode {
  return ctx.lib.instance('Category Chip', { variant: { state: selected ? 'selected' : 'default' }, text: { label } });
}

export function caption(ctx: ScreenContext, chars: string, name = 'caption'): TextNode {
  return text(ctx.t, 'caption', chars, { name, color: ctx.t.color.textTertiary });
}
