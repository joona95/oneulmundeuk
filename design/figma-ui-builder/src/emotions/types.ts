
export const EMOTIONS = ['calm', 'joy', 'excited', 'neutral', 'tired', 'anxious', 'sad'] as const;
export type Emotion = (typeof EMOTIONS)[number];

export const EMOTION_LABEL: Record<Emotion, string> = {
  calm: '평온',
  joy: '기쁨',
  excited: '설렘',
  neutral: '그냥 그래',
  tired: '지침',
  anxious: '불안',
  sad: '속상함',
};

/** xs = list/timeline marker (v3), sm = card/inline, md = pickers, lg = the selected marker in a picker (v3). */
export type EmotionSize = 'xs' | 'sm' | 'md' | 'lg';

export interface EmotionColors {
  fill: string;
  ink: string;
}

/**
 * An emotion style turns (emotion, colors) into an SVG drawn on a 24×24 grid.
 * Keep shapes simple: they must stay recognizable at 16–28px.
 * To add a style: create a file exporting an EmotionStyleDef and register it in emotions/index.ts.
 */
export interface EmotionStyleDef {
  key: string;
  /** Design/dev name (Builder panel, layer names). Never shown in the app UI. */
  label: string;
  /** Name shown to app users (Settings). */
  userLabel: string;
  /** One-line description shown under the user label. */
  description: string;
  /** Inner SVG markup (no <svg> wrapper) on a 24×24 viewBox. */
  draw(emotion: Emotion, c: EmotionColors): string;
}

export function wrapSvg(inner: string, size: number): string {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">${inner}</svg>`;
}
