// Small Creature — the blob silhouette plus two tiny eyes. No mouth, no limbs (must not read as a mascot).
import { BLOB_V1, BLOB_V2 } from './shapes';
import type { BlobGeometry } from './shapes';
import type { Emotion, EmotionStyleDef } from './types';

function eyes(g: BlobGeometry, e: Emotion, ink: string): string {
  const [cx, cy] = g.center(e);
  const l = cx - 2.6, r = cx + 2.6, y = cy - 0.6;
  const dot = (x: number, yy: number, rr = 1.05) => `<circle cx="${x}" cy="${yy}" r="${rr}" fill="${ink}"/>`;
  const line = (d: string) => `<path d="${d}" stroke="${ink}" stroke-width="1.3" stroke-linecap="round" fill="none"/>`;
  switch (e) {
    case 'joy': // smiling closed eyes ^ ^
      return line(`M${l - 1.1} ${y + 0.5}q1.1 -1.4 2.2 0M${r - 1.1} ${y + 0.5}q1.1 -1.4 2.2 0`);
    case 'calm': // relaxed closed eyes
      return line(`M${l - 1.1} ${y}q1.1 1 2.2 0M${r - 1.1} ${y}q1.1 1 2.2 0`);
    case 'tired': // flat lids
      return line(`M${l - 1.1} ${y + 0.4}h2.2M${r - 1.1} ${y + 0.4}h2.2`);
    case 'excited':
      return dot(l, y - 0.4, 1.3) + dot(r, y - 0.4, 1.3);
    case 'anxious':
      return dot(l - 0.3, y, 0.85) + dot(r + 0.3, y, 0.85);
    case 'sad':
      return dot(l + 0.2, y + 0.9) + dot(r - 0.2, y + 0.9);
    default:
      return dot(l, y) + dot(r, y);
  }
}

export function makeCreatureStyle(g: BlobGeometry): EmotionStyleDef {
  return {
    key: 'creature',
    label: 'Small Creature',
    userLabel: '꼬물꼬물',
    description: '작은 눈이 있는 친구들',
    draw(e, c) {
      return `<path d="${g.path(e)}" fill="${c.fill}"/>` + eyes(g, e, c.ink);
    },
  };
}

export const creatureStyle = makeCreatureStyle(BLOB_V2);
export const creatureStyleV1 = makeCreatureStyle(BLOB_V1);
