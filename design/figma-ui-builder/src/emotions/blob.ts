// Abstract Blob ("몽글몽글") — the default emotion style. Pure silhouettes, no faces.
import { BLOB_V1, BLOB_V2 } from './shapes';
import type { BlobGeometry } from './shapes';
import type { EmotionStyleDef } from './types';

export function makeBlobStyle(g: BlobGeometry, refined: boolean): EmotionStyleDef {
  return {
    key: 'blob',
    label: 'Abstract Blob',
    userLabel: '몽글몽글',
    description: '말랑하고 둥근 모양',
    draw(e, c) {
      const [cx, cy] = g.center(e);
      if (!refined) {
        const hx = cx - 3, hy = cy - (e === 'calm' || e === 'tired' ? 1.6 : 3);
        return (
          `<path d="${g.path(e)}" fill="${c.fill}" stroke="${c.ink}" stroke-opacity="0.28" stroke-width="1"/>` +
          `<ellipse cx="${hx}" cy="${hy}" rx="2" ry="1.3" transform="rotate(-30 ${hx} ${hy})" fill="#FFFFFF" fill-opacity="0.55"/>`
        );
      }
      // v2: thinner rim, a small crescent highlight that follows the upper-left contour.
      const hx = cx - 3.2, hy = cy - (e === 'calm' ? 1.4 : e === 'tired' ? 1.8 : 3.1);
      return (
        `<path d="${g.path(e)}" fill="${c.fill}" stroke="${c.ink}" stroke-opacity="0.22" stroke-width="0.9"/>` +
        `<path d="M${hx - 1.3} ${hy + 1.1}q0.6 -1.7 2.4 -2.2" stroke="#FFFFFF" stroke-opacity="0.75" stroke-width="1.3" stroke-linecap="round" fill="none"/>`
      );
    },
  };
}

export const blobStyle = makeBlobStyle(BLOB_V2, true);
export const blobStyleV1 = makeBlobStyle(BLOB_V1, false);
