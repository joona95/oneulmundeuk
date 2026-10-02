// Base (variant-independent) design tokens.
// Every number used by components/screens must come from here (via the resolved Theme).

export const spacing = {
  hair: 2,
  xxs: 4,
  xs: 8,
  sm: 12,
  md: 16,
  lg: 20,
  xl: 24,
  xxl: 32,
  xxxl: 40,
} as const;
export type SpaceKey = keyof typeof spacing;

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
  full: 999,
} as const;
export type RadiusKey = keyof typeof radius;

export const border = {
  hairline: 1,
  focus: 1.5,
  doodleStroke: 1.75,
} as const;

/** Drop-shadow definitions. Colors are applied at resolve time. */
export const elevation = {
  e0: [] as { y: number; blur: number; opacity: number }[],
  e1: [{ y: 1, blur: 3, opacity: 0.06 }],
  e2: [
    { y: 4, blur: 16, opacity: 0.06 },
    { y: 1, blur: 2, opacity: 0.04 },
  ],
};
export type ElevationKey = keyof typeof elevation;

export const iconSize = {
  sm: 16,
  md: 20,
  lg: 24,
} as const;

export const size = {
  viewportWidth: 360,
  viewportHeight: 800,
  statusBar: 24,
  appBar: 56,
  bottomNav: 64,
  gestureInset: 16,
  button: 48,
  buttonSmall: 36,
  iconButton: 40,
  fab: 56,
  chip: 32,
  searchBar: 52,
  segmented: 40,
  emotionXs: 8,
  emotionSm: 20,
  emotionMd: 28,
  calendarCell: 44,
  rediscoveryCardWidth: 272,
  timelineRail: 20,
  photoThumb: 72,
  navIndicatorWidth: 56,
  navIndicatorHeight: 28,
  toggleWidth: 44,
  toggleHeight: 26,
  radioOuter: 20,
  toggleThumb: 20,
  sendButton: 36,
  quickEntryRegular: 56,
  quickEntryLarge: 168,
  photo: 180,
  threadRail: 28,
  threadNode: 14,
  checkBadge: 22,
  stylePreviewIcon: 28,
  emotionMarker: 14,
  emotionSelected: 32,
  categoryTagMax: 120,
  shapePreviewList: 18,
  shapePreviewGrid: 14,
  selectedBadge: 18,
  shapeNameColumn: 64,
} as const;

export type Weight = 400 | 500 | 600 | 700;

export interface TypeStyle {
  size: number;
  lineHeight: number;
  weight: Weight;
  letterSpacing: number; // percent
}

/** Typography scale. Font family is resolved at runtime (see core/fonts.ts). */
export const typography = {
  display: { size: 28, lineHeight: 36, weight: 700, letterSpacing: -2 },
  title: { size: 22, lineHeight: 30, weight: 600, letterSpacing: -1.5 },
  heading: { size: 17, lineHeight: 24, weight: 600, letterSpacing: -1 },
  memory: { size: 17, lineHeight: 28, weight: 400, letterSpacing: -0.5 },
  bodyLarge: { size: 17, lineHeight: 30, weight: 400, letterSpacing: -0.5 },
  body: { size: 15, lineHeight: 24, weight: 400, letterSpacing: -0.3 },
  bodySmall: { size: 14, lineHeight: 21, weight: 400, letterSpacing: -0.2 },
  label: { size: 13, lineHeight: 18, weight: 600, letterSpacing: 0 },
  caption: { size: 12, lineHeight: 16, weight: 500, letterSpacing: 0 },
} satisfies Record<string, TypeStyle>;
export type TypeKey = keyof typeof typography;
