// Geometric Symbol — flat primitive shapes. Best fit for the Minimal variant.
import { DROP_PATH } from './shapes';
import type { Emotion, EmotionStyleDef } from './types';

function shape(e: Emotion, fill: string, ink: string): string {
  switch (e) {
    case 'calm':
      return `<path d="M3.6 16.4a8.4 8.4 0 0 1 16.8 0Z" fill="${fill}"/>`;
    case 'joy':
      return `<circle cx="12" cy="12" r="8" fill="${fill}"/><circle cx="12" cy="12" r="3.2" fill="#FFFFFF" fill-opacity="0.7"/>`;
    case 'excited':
      return `<path d="M12 4.2 20.2 18.6H3.8Z" fill="${fill}" stroke="${fill}" stroke-width="1.5" stroke-linejoin="round"/>`;
    case 'neutral':
      return `<rect x="5" y="5" width="14" height="14" rx="3.5" fill="${fill}"/>`;
    case 'tired':
      return `<path d="M3.6 8.6a8.4 8.4 0 0 0 16.8 0Z" fill="${fill}"/>`;
    case 'anxious':
      return `<path d="M12 3.4 20.6 12 12 20.6 3.4 12Z" fill="${fill}" stroke="${fill}" stroke-width="1.5" stroke-linejoin="round"/><path d="M8.5 12h7" stroke="${ink}" stroke-width="1.4" stroke-linecap="round" stroke-dasharray="1.6 1.6"/>`;
    case 'sad':
      return `<path d="${DROP_PATH}" fill="${fill}"/>`;
  }
}

export const geometricStyle: EmotionStyleDef = {
  key: 'geometric',
  label: 'Geometric Symbol',
  userLabel: '반듯반듯',
  description: '단정한 기본 도형',
  draw(e, c) {
    return shape(e, c.fill, c.ink);
  },
};
