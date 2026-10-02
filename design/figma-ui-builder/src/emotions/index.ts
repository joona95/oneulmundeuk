// Emotion style registry. Add a new style: implement EmotionStyleDef, register here,
// add its key to EmotionStyleKey in types.ts and an <option> in ui.html.
import type { EmotionStyleKey } from '../types';
import { blobStyle, blobStyleV1 } from './blob';
import { creatureStyle, creatureStyleV1 } from './creature';
import { doodleStyle } from './doodle';
import { geometricStyle } from './geometric';
import type { EmotionStyleDef } from './types';

export const EMOTION_STYLES: Record<EmotionStyleKey, EmotionStyleDef> = {
  blob: blobStyle,
  creature: creatureStyle,
  doodle: doodleStyle,
  geometric: geometricStyle,
};

/** v1 blob-based styles, used when config.refine === 'v1' and in comparison frames. */
export const EMOTION_STYLES_V1: Record<EmotionStyleKey, EmotionStyleDef> = {
  ...EMOTION_STYLES,
  blob: blobStyleV1,
  creature: creatureStyleV1,
};

export function emotionStyles(refined: boolean): Record<EmotionStyleKey, EmotionStyleDef> {
  return refined ? EMOTION_STYLES : EMOTION_STYLES_V1;
}

export { MARKER_SHAPES, MARKER_STYLES, MIXED_SHAPES, markerMixed, markerSame, sameShapeMarker, transformed } from './markers';
export type { MarkerShapeDef, MarkerShapeKey } from './markers';
export * from './types';
