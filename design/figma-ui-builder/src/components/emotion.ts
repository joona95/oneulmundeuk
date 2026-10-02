// Emotion Indicator — component set (emotion × size) drawn by the active emotion style.
import type { ComponentDef } from '../core/library';
import { EMOTIONS, wrapSvg } from '../emotions';
import type { Emotion } from '../emotions';
import { comp, variantSet } from './_util';

export const EmotionIndicator: ComponentDef = {
  name: 'Emotion',
  build(t, lib) {
    const style = t.emotionStyle;
    const sizes = t.v3 ? ['xs', 'sm', 'md', 'lg'] : ['sm', 'md'];
    return variantSet(lib, t, { size: sizes, emotion: EMOTIONS }, (p) => {
      const px = { xs: t.size.emotionMarker, sm: t.size.emotionSm, md: t.size.emotionMd, lg: t.size.emotionSelected }[p.size as 'xs' | 'sm' | 'md' | 'lg'];
      const e = p.emotion as Emotion;
      const c = comp({ name: p.emotion, width: px, height: px });
      c.layoutMode = 'NONE';
      c.fills = [];
      const art = figma.createNodeFromSvg(wrapSvg(style.draw(e, t.color.emotion[e]), px));
      art.name = 'art';
      art.fills = [];
      c.appendChild(art);
      art.x = 0;
      art.y = 0;
      return c;
    }, { columns: 7 });
  },
};
