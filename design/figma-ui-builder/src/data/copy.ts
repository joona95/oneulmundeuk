// Product copy roles. Three moments, three different verbs — deliberately NOT unified:
//   Home       · passive rediscovery   · 다시 만남  → "다시 만난 생각"
//   After save · contextual connection · 떠오름     → "문득, 예전의 생각이 떠올랐어요"
//   Explore    · active exploration    · 찾아봄     → "과거의 나에게 물어보세요."
// Never say "비슷한": related retrieval may surface similar thoughts, the same topic, a different
// view at the time, or how a thought changed later.
export const COPY = {
  homeRediscovery: { title: '다시 만난 생각', hint: '시간이 지나 다시 나타난 지난 기록이에요.' },
  // Design Freeze: no subtitle — the title alone carries the meaning and keeps the whitespace.
  afterSave: { title: '문득,\n예전의 생각이 떠올랐어요' },
  explore: { title: '과거의 나에게\n물어보세요.' },
} as const;

/**
 * Related Memories header copy (final). Earlier candidates ("비슷한 생각", "닿아 있는 기록",
 * "이어지는 생각") and the subtitle were dropped: the copy describes re-finding, not the retrieval method.
 */
export const RELATED_COPY = {
  surfaced: { label: '최종', title: COPY.afterSave.title, sub: '' },
} as const;
export type RelatedCopyKey = keyof typeof RELATED_COPY;

export const DEFAULT_RELATED_COPY: RelatedCopyKey = 'surfaced';
