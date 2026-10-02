// Doodle — one hand-drawn ink gesture over a soft color dab (like a highlighter mark).
import type { Emotion, EmotionStyleDef } from './types';

const GESTURE: Record<Emotion, string> = {
  // gentle double wave
  calm: 'M4 12.6c2.6-2.6 5.2-2.6 8 0s5.4 2.6 8 0',
  // little sun
  joy: 'M14.9 12a2.9 2.9 0 1 1-5.8 0 2.9 2.9 0 0 1 5.8 0ZM12 4.6v2.1M12 17.3v2.1M4.6 12h2.1M17.3 12h2.1M6.8 6.8l1.4 1.4M15.8 15.8l1.4 1.4M6.8 17.2l1.4-1.4M15.8 8.2l1.4-1.4',
  // uneven burst
  excited: 'M12 4.2v5.2M12 14.6v5.2M4.9 8.4l4.4 2.5M14.7 13.1l4.4 2.5M4.9 15.6l4.4-2.5M14.7 10.9l4.4-2.5',
  // imperfect open circle
  neutral: 'M17.6 10.4c.8 3.7-1.6 7-5.3 7.4-3.8.4-7-2.3-6.8-5.9.2-3.4 2.9-6.2 6.4-6.3 2.3-.1 4.2 1.1 5.1 3',
  // drooping line + a small z
  tired: 'M4.6 11.4c2.5 3.6 5 4.6 7.4 4.6s4.9-1 7.4-4.6M14.8 4.6h3.3l-3.3 3.3h3.3',
  // tangled scribble
  anxious: 'M4.2 14.6c1.3-4.4 4-4.6 3.6-1.3-.4 3.1-.4 4.4 1.8 3.2 2.3-1.2 1.2-6.2 3.9-6.2 2.5 0 .7 5.2 3 5.1 1.9-.1 2.1-2.7 3.3-3.9',
  // drop outline + a tiny tear line
  sad: 'M12 5c2.4 3.2 5 5.8 5 8.6a5 5 0 0 1-10 0C7 10.8 9.6 8.2 12 5Z',
};

export const doodleStyle: EmotionStyleDef = {
  key: 'doodle',
  label: 'Doodle',
  userLabel: '끄적끄적',
  description: '손으로 그린 한 획',
  draw(e, c) {
    return (
      `<circle cx="13.2" cy="13.2" r="7.6" fill="${c.fill}" fill-opacity="0.55"/>` +
      `<path d="${GESTURE[e]}" stroke="${c.ink}" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" fill="none"/>`
    );
  },
};
