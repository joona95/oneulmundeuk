// v3 emotion markers — soft "jelly" shapes that are NOT pictograms.
// Meaning comes from color + label; the shape is the app's visual identity.
// Variant A ("same"): every emotion uses the same soft shape, only the color changes.
// Variant B ("mixed", adopted): a fixed set of soft shapes is spread across emotions for extra
// distinction. Shapes are not pictograms, but the assignment must never clash with the emotion:
// warm/open shapes for positive states, soft and grounded ones for low states, no heart on a sad
// record, no star on a tired one.
import type { Emotion, EmotionColors, EmotionStyleDef } from './types';
import type { MarkerShapeKey } from '../types';
export type { MarkerShapeKey };

type Pt = [number, number];
const f = (n: number) => (Math.round(n * 100) / 100).toString();
const TAU = Math.PI * 2;
const UP = -Math.PI / 2;

/** Closed Catmull-Rom → cubic Bézier path through points. */
function smooth(pts: Pt[]): string {
  const n = pts.length;
  const at = (i: number) => pts[(i + n) % n];
  let d = `M${f(pts[0][0])} ${f(pts[0][1])}`;
  for (let i = 0; i < n; i++) {
    const p0 = at(i - 1), p1 = at(i), p2 = at(i + 1), p3 = at(i + 2);
    d += `C${f(p1[0] + (p2[0] - p0[0]) / 6)} ${f(p1[1] + (p2[1] - p0[1]) / 6)} ${f(p2[0] - (p3[0] - p1[0]) / 6)} ${f(p2[1] - (p3[1] - p1[1]) / 6)} ${f(p2[0])} ${f(p2[1])}`;
  }
  return d + 'Z';
}

export type MarkerShape = 'pebble' | 'circle' | 'roundSquare' | 'star' | 'heart' | 'diamond' | 'triangle';

/** Unit outline for each shape (center ~0,0), sampled counter-clockwise. */
function outline(shape: MarkerShape, n = 72): Pt[] {
  const pts: Pt[] = [];
  for (let i = 0; i < n; i++) {
    const th = (i / n) * TAU;
    const c = Math.cos(th), s = Math.sin(th);
    let r = 1;
    switch (shape) {
      case 'pebble': { // wide, low, slightly asymmetric stone
        const rr = 1 + 0.05 * Math.cos(3 * th + 1.2);
        pts.push([c * rr * 1.18, s * rr * 0.8 + 0.06 * c * c]);
        continue;
      }
      case 'circle': r = 1; break;
      case 'roundSquare': r = 1 / Math.pow(Math.pow(Math.abs(c), 4) + Math.pow(Math.abs(s), 4), 1 / 4); break;
      case 'diamond': { // rhombus, a little taller than wide, corners softened by the curve fit
        r = 1 / Math.pow(Math.pow(Math.abs(c), 1.22) + Math.pow(Math.abs(s), 1.22), 1 / 1.22);
        pts.push([c * r * 0.88, s * r]);
        continue;
      }
      case 'star': r = 1 + 0.26 * Math.cos(5 * (th - UP)); break;
      case 'triangle': r = 1 / (1 - 0.13 * Math.cos(3 * (th - UP))); break; // flat-ish sides, soft corners
      case 'heart': {
        // classic parametric heart, sampled by parameter instead of angle
        const x = 16 * Math.pow(Math.sin(th), 3);
        const y = -(13 * Math.cos(th) - 5 * Math.cos(2 * th) - 2 * Math.cos(3 * th) - Math.cos(4 * th));
        pts.push([x / 16, y / 16]);
        continue;
      }
    }
    pts.push([c * r, s * r]);
  }
  return pts;
}

/**
 * Jelly: a gentle, deterministic wobble so no edge is perfectly geometric, then normalized to a
 * shared optical size on the 24 grid (stars/diamonds get a little more room than round shapes).
 */
function jellyPath(shape: MarkerShape, seed: number): string {
  const raw = outline(shape);
  const cx0 = raw.reduce((a, p) => a + p[0], 0) / raw.length;
  const cy0 = raw.reduce((a, p) => a + p[1], 0) / raw.length;
  const wob = raw.map(([x, y]) => {
    const a = Math.atan2(y - cy0, x - cx0);
    const k = 1 + 0.022 * Math.sin(2 * a + seed) + 0.016 * Math.sin(3 * a + seed * 1.7);
    return [cx0 + (x - cx0) * k, cy0 + (y - cy0) * k] as Pt;
  });
  const xs = wob.map((p) => p[0]), ys = wob.map((p) => p[1]);
  const [x0, x1, y0, y1] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
  const target = shape === 'star' || shape === 'diamond' || shape === 'pebble' ? 19 : shape === 'heart' || shape === 'triangle' ? 18 : 17;
  const scale = target / Math.max(x1 - x0, y1 - y0);
  const mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
  return smooth(wob.map(([x, y]) => [12 + (x - mx) * scale, 12.2 + (y - my) * scale] as Pt));
}

/** Soft gloss: thin rim + a short highlight stroke near the upper-left edge. */
function jelly(shape: MarkerShape, seed: number, c: EmotionColors): string {
  return (
    `<path d="${jellyPath(shape, seed)}" fill="${c.fill}" stroke="${c.ink}" stroke-opacity="0.18" stroke-width="0.8"/>` +
    `<path d="M7.6 10.4q0.7 -2 2.7 -2.6" stroke="#FFFFFF" stroke-opacity="0.7" stroke-width="1.3" stroke-linecap="round" fill="none"/>`
  );
}

export const MIXED_SHAPES: Record<Emotion, MarkerShape> = {
  calm: 'circle', //       even, round, settled
  joy: 'heart', //         warm
  excited: 'star', //      open, bright
  neutral: 'roundSquare', // plain, steady
  tired: 'diamond', //     soft-cornered, quiet (not drooping, not energetic)
  anxious: 'triangle', //  a little edgy but rounded — never sharp
  sad: 'pebble', //        heavy, grounded, gentle; deliberately not a teardrop
};

/**
 * "내 감정 조각" — the user's personal marker shape (Same Shape system, adopted).
 *   Emotion = Color · Shape = personal preference · Label = explicit meaning · Motion = soft tactile feedback
 * Every emotion uses the chosen shape; only the color changes. All shapes share the jelly treatment
 * (slightly irregular silhouette, soft edge, small highlight) and are normalized to equal visual weight.
 * `motion` is the hook for per-shape interaction later (heart → pulse, star → pop); MVP uses 'squish' for all.
 */
export interface MarkerShapeDef {
  key: MarkerShapeKey;
  /** Name shown in Settings › 내 감정 조각. */
  userLabel: string;
  shape: MarkerShape;
  seed: number;
  motion: 'squish' | 'pulse' | 'pop';
}


export const MARKER_SHAPES: Record<MarkerShapeKey, MarkerShapeDef> = {
  jelly: { key: 'jelly', userLabel: '동글동글', shape: 'circle', seed: 0.6, motion: 'squish' },
  heart: { key: 'heart', userLabel: '하트', shape: 'heart', seed: 1.4, motion: 'squish' /* future: pulse */ },
  star: { key: 'star', userLabel: '별', shape: 'star', seed: 2.1, motion: 'squish' /* future: pop */ },
  roundSquare: { key: 'roundSquare', userLabel: '네모', shape: 'roundSquare', seed: 0.9, motion: 'squish' },
  pebble: { key: 'pebble', userLabel: '조약돌', shape: 'pebble', seed: 0.6, motion: 'squish' },
  diamond: { key: 'diamond', userLabel: '마름모', shape: 'diamond', seed: 1.7, motion: 'squish' },
};

export function sameShapeMarker(key: MarkerShapeKey): EmotionStyleDef {
  const d = MARKER_SHAPES[key] ?? MARKER_SHAPES.jelly; // tolerate stale saved configs
  return {
    key: `marker-${key}`,
    label: `Same Shape · ${key}`,
    userLabel: d.userLabel,
    description: '모든 감정이 같은 모양, 색으로 구분',
    draw: (_e, c) => jelly(d.shape, d.seed, c),
  };
}

export const markerSame: EmotionStyleDef = sameShapeMarker('jelly');

/** Mixed Shapes (B) — compared and not adopted; kept for the comparison record only. */
export const markerMixed: EmotionStyleDef = {
  key: 'markerMixed',
  label: 'Marker B · Mixed Shapes (not adopted)',
  userLabel: '감정 마커',
  description: '여러 말랑한 모양, 모양에 의미는 두지 않음',
  draw: (e, c) => jelly(MIXED_SHAPES[e], 0.6 + MIXED_SHAPES[e].length * 0.37, c),
};

export const MARKER_STYLES = { same: markerSame, mixed: markerMixed } as const;

/** Wraps marker art in a transform for motion-concept frames (scale about the marker center). */
export function transformed(inner: string, sx: number, sy: number, rotate = 0, dy = 0): string {
  return `<g transform="translate(12 ${12.2 + dy}) rotate(${rotate}) scale(${sx} ${sy}) translate(-12 -12.2)">${inner}</g>`;
}
