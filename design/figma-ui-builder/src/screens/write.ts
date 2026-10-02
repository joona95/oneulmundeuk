// Write → Connect flow: Record Editor and Related Memories (shown right after saving).
import { setText, tint } from '../core/library';
import type { RelatedVariant } from '../types';
import { DEFAULT_RELATED_COPY, RELATED_COPY } from '../data/copy';
import type { RelatedCopyKey } from '../data/copy';
import { add, frame, para, text, topBorder } from '../core/layout';
import { EMOTIONS, EMOTION_LABEL } from '../emotions/types';
import { CATEGORIES, JUST_SAVED, RELATED_TO_JUST_SAVED, TODAY, fmtAgo, fmtDate, fmtMonthDay, fmtTime } from '../data/sample';
import { icon, markerSize } from '../components/_util';
import { appBar, chip, recordCard, relatedCard, scaffold, section } from './shared';
import type { ScreenContext, ScreenDef } from './shared';

const EDITOR_TEXT =
  '요즘 회사에서 내가 제대로 성장하고 있는지 가끔 모르겠다.\n\n' +
  '그래도 DB 문제를 직접 파고 원인을 찾는 과정은 꽤 재미있다. 어제 슬로우 쿼리 하나를 잡았는데, 실행 계획을 한 줄씩 읽으면서 왜 느린지 이해되는 순간이 좋았다.\n\n' +
  '이런 걸 더 잘하고 싶다는 마음이 드는 걸 보면, 방향은 조금씩 생기고 있는 걸지도.';

function panelLabel(ctx: ScreenContext, parent: FrameNode, label: string) {
  add(parent, text(ctx.t, 'caption', label, { name: `label/${label}`, color: ctx.t.color.textTertiary, weight: 600 }));
}

export const RecordEditorScreen: ScreenDef = {
  key: 'recordEditor',
  label: 'Record Editor',
  build(ctx) {
    const { t, lib } = ctx;
    const { root, content } = scaffold(ctx, {
      name: 'Record Editor',
      appBar: appBar(ctx, 'close', '새 기록', { textAction: '저장' }),
      footer: (r) => {
        const panel = frame({ name: 'attributes', gap: t.space.md, pad: { t: t.space.md, b: t.space.md + t.size.gestureInset, l: t.layout.screenPadding, r: t.layout.screenPadding }, fill: t.color.surface });
        topBorder(panel, t.color.border, t.border.hairline);
        panel.topLeftRadius = panel.topRightRadius = t.card.heroRadius;
        add(r, panel, { fillW: true });

        const emo = add(panel, frame({ name: 'emotion', gap: t.space.xs }), { fillW: true });
        panelLabel(ctx, emo, '지금 기분');
        // align MAX: the selected (slightly larger) marker never shifts the label baseline
        const opts = add(emo, frame({ name: 'options', dir: 'H', justify: 'SPACE_BETWEEN', align: 'MAX' }), { fillW: true });
        for (const e of EMOTIONS) {
          const selected = e === 'neutral';
          add(opts, lib.instance('Emotion Option', {
            variant: { state: selected ? 'selected' : 'default' },
            swap: { emotion: lib.emotion(e, selected && t.v3 ? 'lg' : 'md') },
            text: { label: EMOTION_LABEL[e] },
          }));
        }

        const cat = add(panel, frame({ name: 'category', gap: t.space.xs }), { fillW: true });
        panelLabel(ctx, cat, '카테고리');
        const chips = add(cat, frame({ name: 'chips', dir: 'H', gap: t.space.xs, clip: true }), { fillW: true });
        chips.overflowDirection = 'HORIZONTAL';
        CATEGORIES.slice(0, 5).forEach((c, i) => add(chips, chip(ctx, c, i === 0)));

        const extras = add(panel, frame({ name: 'extras', dir: 'H', gap: t.space.xs, align: 'CENTER' }), { fillW: true });
        // Tags are post-MVP (v1.1): v2 keeps only the optional photo.
        if (!t.refined) for (const label of ['#성장', '#회사']) add(extras, chip(ctx, label));
        const photoBtn = add(extras, frame({ name: 'add-photo', dir: 'H', gap: t.space.xxs, align: 'CENTER', height: t.size.chip, pad: [0, t.space.sm], radius: t.chipRadius, stroke: t.color.border, strokeWeight: t.border.hairline }));
        add(photoBtn, icon(lib, 'image', t.color.textSecondary, t.icon.sm));
        add(photoBtn, text(t, 'label', '사진', { name: 'label', color: t.color.textSecondary }));

        add(panel, lib.instance('Button', { variant: { kind: 'primary' }, text: { label: '저장하고 이어진 기록 보기' } }), { fillW: true });
      },
    });
    const body = section(ctx, content, 'editor', t.space.md, { pad: { t: t.space.xs, b: 0, l: t.layout.screenPadding, r: t.layout.screenPadding } });
    add(body, text(t, 'caption', `${fmtMonthDay(TODAY)} · ${fmtTime(JUST_SAVED.at)}`, { name: 'timestamp', color: t.color.textTertiary }));
    add(body, para(t, 'bodyLarge', EDITOR_TEXT, { name: 'body' }), { fillW: true });
    add(body, frame({ name: 'caret', width: t.space.hair, height: t.type.bodyLarge.size + t.space.xxs, fill: t.color.accent, radius: t.radius.full }));
    return root;
  },
};

function relatedScaffold(ctx: ScreenContext, name: string) {
  const { t, lib } = ctx;
  return scaffold(ctx, {
    name,
    appBar: appBar(ctx, 'close', ''),
    footer: (r) => {
      const bar = frame({ name: 'actions', dir: 'H', gap: t.space.sm, pad: { t: t.space.sm, b: t.space.sm + t.size.gestureInset, l: t.layout.screenPadding, r: t.layout.screenPadding }, fill: t.color.background });
      add(r, bar, { fillW: true });
      if (t.v3) {
        // v3: the next step of the core loop (다시 만난다 → 이어서 생각한다) is the primary action.
        add(bar, lib.instance('Button', { variant: { kind: 'ghost' }, text: { label: '홈으로' } }));
        add(bar, lib.instance('Button', { variant: { kind: 'primary' }, text: { label: '이어서 생각 남기기' } }), { fillW: true });
        return;
      }
      add(bar, lib.instance('Button', { variant: { kind: 'ghost' }, text: { label: '이어서 쓰기' } }), { fillW: true });
      add(bar, lib.instance('Button', { variant: { kind: 'primary' }, text: { label: '홈으로' } }), { fillW: true });
    },
  });
}

function headline(ctx: ScreenContext, content: FrameNode, title: string, sub: string) {
  const { t } = ctx;
  const s = section(ctx, content, 'title', t.space.xs);
  add(s, para(t, 'title', title, { name: 'headline' }), { fillW: true });
  if (sub) add(s, para(t, 'bodySmall', sub, { name: 'sub', color: t.color.textSecondary }), { fillW: true });
}

const RELATED = RELATED_TO_JUST_SAVED;
const oldest = RELATED[RELATED.length - 1];

/** Current (v1): saved-note box + card timeline. */
function relatedCurrent(ctx: ScreenContext): FrameNode {
  const { t, lib } = ctx;
  const { root, content } = relatedScaffold(ctx, 'Related Memories · 현재');
  const saved = section(ctx, content, 'just-saved', t.space.xs);
  const box = add(saved, frame({ name: 'saved-card', gap: t.space.xs, pad: t.space.md, radius: t.card.radius, fill: t.color.surfaceSecondary }), { fillW: true });
  const head = add(box, frame({ name: 'head', dir: 'H', gap: t.space.xxs, align: 'CENTER' }));
  const check = icon(lib, 'check', t.color.accent, t.icon.sm);
  add(head, check);
  tint(check, t.color.accent);
  add(head, text(t, 'caption', `방금 남긴 생각 · ${fmtTime(JUST_SAVED.at)}`, { name: 'meta', color: t.color.textSecondary, weight: 600 }));
  add(box, para(t, 'bodySmall', JUST_SAVED.body, { name: 'body', color: t.color.textSecondary, maxLines: 2 }), { fillW: true });
  headline(ctx, content, '이 생각과 이어지는\n기록을 찾았어요', `${RELATED.length}개의 기록 · 가장 오래된 기록은 ${fmtAgo(oldest.at)}`);
  const list = section(ctx, content, 'timeline', 0);
  RELATED.forEach((r, i) => add(list, relatedCard(ctx, r, i === RELATED.length - 1), { fillW: true }));
  return root;
}

/**
 * Thread A: one quiet thread from "지금" back into the past. Each past node IS that record's emotion,
 * and the user's original sentence sits uncarded in memory type — the past self is the hero.
 */
function relatedThreadA(ctx: ScreenContext): FrameNode {
  const { lib } = ctx;
  const { root, content } = relatedScaffold(ctx, 'Related Memories · Thread A');
  headline(ctx, content, '예전에도 비슷한\n생각을 남겼어요', `${RELATED.length}개의 기록 · ${fmtAgo(oldest.at)}까지 이어져요`);
  const thread = section(ctx, content, 'thread', 0);
  add(thread, lib.instance('Thread Item', { variant: { kind: 'now' }, text: { time: fmtTime(JUST_SAVED.at), body: JUST_SAVED.body } }), { fillW: true });
  RELATED.forEach((r, i) => {
    const last = i === RELATED.length - 1;
    add(thread, lib.instance('Thread Item', {
      variant: { kind: last ? 'last' : 'past' },
      text: { when: fmtAgo(r.at), date: fmtDate(r.at), quote: r.body, ...(r.category ? { label: r.category } : {}) },
      swap: r.emotion ? { emotion: lib.emotion(r.emotion, markerSize(ctx.t)) } : undefined,
      hide: r.category ? [] : ['category'],
    }), { fillW: true });
  });
  return root;
}

/**
 * Thread B: the new thought on top, then the past arrives in "echoes" separated by time-gap rules.
 * Older quotes step down very slightly in tone so distance in time is felt, not explained.
 */
function relatedThreadB(ctx: ScreenContext): FrameNode {
  if (ctx.t.v3) return relatedThreadBV3(ctx, DEFAULT_RELATED_COPY);
  const { t, lib } = ctx;
  const { root, content } = relatedScaffold(ctx, 'Related Memories · Thread B');
  headline(ctx, content, '이어지는 생각이 있어요', '방금 남긴 생각과 닮은 예전 기록이에요');
  const stack = section(ctx, content, 'echoes', t.space.sm);
  const now = recordCard(ctx, { ...JUST_SAVED, at: JUST_SAVED.at }, { maxLines: 3 });
  setText(now, 'time', `지금 · ${fmtTime(JUST_SAVED.at)}`);
  add(stack, now, { fillW: true });
  const tones = [1, 0.92, 0.84, 0.76];
  RELATED.forEach((r, i) => {
    add(stack, lib.instance('Time Gap', { text: { gap: fmtAgo(r.at) } }), { fillW: true });
    const q = lib.instance('Memory Quote', {
      text: { quote: r.body, date: fmtDate(r.at), ...(r.category ? { label: r.category } : {}) },
      swap: r.emotion ? { emotion: lib.emotion(r.emotion, markerSize(ctx.t)) } : undefined,
      hide: r.category ? [] : ['category'],
    });
    (q.findOne((n) => n.name === 'quote') as TextNode).opacity = tones[Math.min(i, tones.length - 1)];
    add(stack, q, { fillW: true });
  });
  return root;
}

/**
 * Thread B · v3 — the decided direction. Current thought (quiet card) → for each past record:
 * hairline, "6개월 전" + tiny emotion marker, then the user's own sentence in memory type.
 * Generous whitespace instead of containers; no AI explanation, no scores.
 */
export function relatedThreadBV3(ctx: ScreenContext, copy: RelatedCopyKey): FrameNode {
  const { t, lib } = ctx;
  const c = RELATED_COPY[copy];
  const { root, content } = relatedScaffold(ctx, 'Related Memories · Thread B');
  headline(ctx, content, c.title, c.sub);
  const nowSec = section(ctx, content, 'now', t.space.xs);
  add(nowSec, text(t, 'caption', '방금 남긴 생각', { name: 'now-label', color: t.color.textTertiary, weight: 600 }));
  const now = recordCard(ctx, JUST_SAVED, { maxLines: 3 });
  setText(now, 'time', `지금 · ${fmtTime(JUST_SAVED.at)}`);
  add(nowSec, now, { fillW: true });
  const past = section(ctx, content, 'past', t.space.xl);
  for (const r of RELATED) {
    add(past, lib.instance('Memory Entry', {
      text: { when: fmtAgo(r.at), date: fmtDate(r.at), quote: r.body, category: r.category ?? '' },
      swap: r.emotion ? { emotion: lib.emotion(r.emotion, markerSize(t)) } : undefined,
      hide: [...(r.emotion ? [] : ['emotion']), ...(r.category ? [] : ['category'])],
    }), { fillW: true });
  }
  return root;
}

export const RELATED_VARIANTS: Record<RelatedVariant, { label: string; build: (ctx: ScreenContext) => FrameNode }> = {
  current: { label: '현재', build: relatedCurrent },
  threadA: { label: 'Thread A', build: relatedThreadA },
  threadB: { label: 'Thread B', build: relatedThreadB },
};

export const RelatedMemoriesScreen: ScreenDef = {
  key: 'relatedMemories',
  label: 'Related Memories',
  build: (ctx) => RELATED_VARIANTS[ctx.config.relatedVariant].build(ctx),
};
