// Shared silhouette math for blob-like emotion styles (Abstract Blob, Small Creature).
import type { Emotion } from './types';

type Pt = [number, number];

interface PolarSpec {
  cx: number;
  cy: number;
  r: (theta: number) => number;
  sx?: number;
  sy?: number;
  /** Horizontal shear applied after scaling (x += shear * (y - cy)). */
  shear?: number;
  samples?: number;
  /** Optional point transform applied last (e.g. flattening a base). */
  post?: (x: number, y: number) => Pt;
}

const f = (n: number) => (Math.round(n * 100) / 100).toString();

/** Closed smooth path through polar samples (Catmull-Rom → cubic Bézier). */
export function polarPath(s: PolarSpec): string {
  const n = s.samples ?? 36;
  const pts: Pt[] = [];
  for (let i = 0; i < n; i++) {
    const th = (i / n) * Math.PI * 2;
    const r = s.r(th);
    const y = s.cy + Math.sin(th) * r * (s.sy ?? 1);
    const x = s.cx + Math.cos(th) * r * (s.sx ?? 1) + (s.shear ?? 0) * (y - s.cy);
    pts.push(s.post ? s.post(x, y) : [x, y]);
  }
  const at = (i: number) => pts[(i + n) % n];
  let d = `M${f(pts[0][0])} ${f(pts[0][1])}`;
  for (let i = 0; i < n; i++) {
    const p0 = at(i - 1), p1 = at(i), p2 = at(i + 1), p3 = at(i + 2);
    const c1: Pt = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2: Pt = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += `C${f(c1[0])} ${f(c1[1])} ${f(c2[0])} ${f(c2[1])} ${f(p2[0])} ${f(p2[1])}`;
  }
  return d + 'Z';
}

const UP = -Math.PI / 2; // y-down coordinate space: "up" is -90°

/** Lobes of a k-fold wave, with one lobe pointing straight up. */
const wave = (base: number, k: number, amp: number) => (th: number) => base * (1 + amp * Math.cos(k * (th - UP)));

export const DROP_PATH = 'M12 3.6C14.9 7.4 18.4 10.5 18.4 14.2A6.4 6.4 0 0 1 5.6 14.2C5.6 10.5 9.1 7.4 12 3.6Z';

/** v1 silhouettes — kept for the refinement comparison. */
/** Silhouette per emotion. The silhouette alone (no color) must distinguish the emotion. */
export function blobPathV1(e: Emotion): string {
  switch (e) {
    case 'neutral':
      return polarPath({ cx: 12, cy: 12, r: () => 8 });
    case 'calm': // low, wide, resting pebble
      return polarPath({ cx: 12, cy: 13.4, r: wave(9.2, 2, 0.04), sy: 0.64 });
    case 'joy': // three soft bumps rising up
      return polarPath({ cx: 12, cy: 12.4, r: wave(8, 3, 0.1), sy: 0.96 });
    case 'excited': // soft five-point burst
      return polarPath({ cx: 12, cy: 12.4, r: wave(8.3, 5, 0.17), samples: 60 });
    case 'tired': // heavy bottom, slumping sideways
      return polarPath({ cx: 12, cy: 13.6, r: (th) => 8.2 * (1 + 0.16 * Math.sin(th)), sy: 0.76, shear: 0.18 });
    case 'anxious': // trembling edge
      return polarPath({ cx: 12, cy: 12, r: wave(8, 9, 0.065), samples: 72 });
    case 'sad': // a drop
      return DROP_PATH;
  }
}

/** Approximate visual center of each silhouette (used for faces / highlights). */
export function blobCenterV1(e: Emotion): Pt {
  switch (e) {
    case 'calm':
      return [12, 13.4];
    case 'tired':
      return [12.2, 14];
    case 'sad':
      return [12, 14.4];
    default:
      return [12, 12.4];
  }
}

// ─── v2 silhouettes ─────────────────────────────────────────────────────────
// Goals: similar optical size (~17–18px bbox on the 24 grid), no plain circle, and a silhouette
// that reads on its own: calm rests, joy rises, excited spreads, neutral sits square, tired slumps,
// anxious trembles, sad falls.

/** Squash everything below `base` toward it — a resting, flattened bottom. */
const flattenBelow = (base: number, k: number) => (x: number, y: number): Pt => [x, y > base ? base + (y - base) * k : y];

export const DROP_PATH_V2 = 'M12 3.2C13.7 6.5 18.7 10.3 18.7 14.6A6.7 6.7 0 0 1 5.3 14.6C5.3 10.3 10.3 6.5 12 3.2Z';

export function blobPathV2(e: Emotion): string {
  switch (e) {
    case 'calm': // wide pebble resting on a flat base
      return polarPath({ cx: 12, cy: 12.9, r: wave(9.3, 2, 0.03), sy: 0.68, post: flattenBelow(15.2, 0.4), samples: 48 });
    case 'joy': { // round body lifting a soft crown of three lumps — reads "up", never like a drop
      const g = (x: number) => Math.exp(-((x / 0.34) ** 2));
      const d = (th: number) => Math.atan2(Math.sin(th - UP), Math.cos(th - UP)); // angle from straight up
      return polarPath({
        cx: 12, cy: 13, samples: 96, sy: 0.92,
        r: (th) => 7.5 * (1 + 0.23 * g(d(th) + 0.62) + 0.23 * g(d(th) - 0.62) + 0.15 * g(d(th))),
      });
    }
    case 'excited': // five soft petals bursting out, slightly turned
      return polarPath({ cx: 12, cy: 12.2, r: (th) => 8.3 * (1 + 0.2 * Math.cos(5 * (th - UP - 0.12))), samples: 80 });
    case 'neutral': // squircle — even, steady, not a circle
      return polarPath({ cx: 12, cy: 12, samples: 64, r: (th) => 7.6 / Math.pow(Math.pow(Math.abs(Math.cos(th)), 3.4) + Math.pow(Math.abs(Math.sin(th)), 3.4), 1 / 3.4) });
    case 'tired': // melting slump: heavy bottom, leaning, puddled base
      return polarPath({ cx: 12, cy: 13.6, r: (th) => 8.3 * (1 + 0.2 * Math.sin(th)), sy: 0.74, shear: 0.22, post: flattenBelow(17.4, 0.45), samples: 60 });
    case 'anxious': // irregular tremble (three mixed frequencies)
      return polarPath({
        cx: 12, cy: 12, samples: 96,
        r: (th) => 7.9 * (1 + 0.05 * Math.cos(7 * th) + 0.04 * Math.cos(11 * th + 1.3) + 0.025 * Math.cos(17 * th + 0.4)),
      });
    case 'sad': // a falling drop
      return DROP_PATH_V2;
  }
}

export function blobCenterV2(e: Emotion): Pt {
  switch (e) {
    case 'calm':
      return [12, 13];
    case 'tired':
      return [12.4, 14.2];
    case 'sad':
      return [12, 14.6];
    case 'joy':
      return [12, 13.4];
    default:
      return [12, 12.2];
  }
}

export interface BlobGeometry {
  path(e: Emotion): string;
  center(e: Emotion): Pt;
}
export const BLOB_V1: BlobGeometry = { path: blobPathV1, center: blobCenterV1 };
export const BLOB_V2: BlobGeometry = { path: blobPathV2, center: blobCenterV2 };
