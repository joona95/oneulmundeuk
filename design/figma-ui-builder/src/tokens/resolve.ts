// Resolves a BuilderConfig into a concrete Theme. Components/screens read ONLY the Theme.
import type { BuilderConfig } from '../types';
import { border, elevation, iconSize, radius, size, spacing, typography } from './base';
import type { TypeKey, TypeStyle } from './base';
import { accents, backgrounds, emotionColors } from './palettes';
import { variants } from './variants';
import { MARKER_STYLES, emotionStyles, sameShapeMarker } from '../emotions';
import type { EmotionStyleDef } from '../emotions';
import type { VariantSpec } from './variants';

export interface ShadowToken {
  y: number;
  blur: number;
  opacity: number;
  color: string;
}

export interface Theme {
  config: BuilderConfig;
  /** Stable key: components are cached per signature. Bump LIBRARY_VERSION when generators change. */
  signature: string;
  color: {
    background: string;
    surface: string;
    surfaceSecondary: string;
    textPrimary: string;
    textSecondary: string;
    textTertiary: string;
    border: string;
    borderStrong: string;
    accent: string;
    accentContainer: string;
    onAccent: string;
    onAccentContainer: string;
    highlight: string;
    emotion: typeof emotionColors;
    shadow: string;
  };
  type: Record<TypeKey, TypeStyle>;
  space: typeof spacing;
  radius: typeof radius;
  border: typeof border;
  icon: typeof iconSize;
  size: typeof size;
  variant: VariantSpec;
  /** Semantic layout values derived from density. */
  layout: {
    screenPadding: number;
    sectionGap: number;
    cardPadding: number;
    cardGap: number;
    listGap: number;
  };
  card: {
    fill: string;
    strokeWeight: number;
    stroke: string;
    shadows: ShadowToken[];
    radius: number;
    heroRadius: number;
  };
  chipRadius: number;
  buttonRadius: number;
  inputRadius: number;
  /** true for v2 and v3. Prefer `role` tokens over branching on this. */
  refined: boolean;
  /** true for v3 (emotion markers, Thread B, no style picker). */
  v3: boolean;
  /** Opacity of the emotion wash on special surfaces (v3 only; 5–8% band). */
  emotionTint: number;
  /** Emotion drawing in effect for this theme (v3: the marker variant; earlier: config.emotionStyle). */
  emotionStyle: EmotionStyleDef;
  /**
   * Semantic color roles. v1 uses the accent everywhere; v2 keeps the accent for primary actions
   * and a few key highlights, and moves selection / focus / meta labels to charcoal + neutrals.
   */
  role: Record<RoleKey, string>;
}

export type RoleKey =
  | 'chipSelectedFill' | 'chipSelectedStroke' | 'onChipSelected'
  | 'navActiveFill' | 'navActiveIcon'
  | 'focusStroke' | 'searchIconActive'
  | 'metaLabel'
  | 'tonalFill' | 'onTonal'
  | 'optionSelectedFill' | 'optionSelectedStroke' | 'onOptionSelected'
  | 'radioOn'
  | 'calendarSelectedFill' | 'onCalendarSelected'
  | 'heroFill' | 'onHero'
  | 'subtleAction' | 'onSubtleAction'
  | 'appIcon'
  | 'matchInk';

export const LIBRARY_VERSION = 'v5';

export function resolveTheme(config: BuilderConfig): Theme {
  const n = backgrounds[config.background];
  const a = accents[config.accent];
  const v = variants[config.variant];
  const compact = config.density === 'compact';

  const shadowKey = config.cardStyle === 'elevation' ? 'e2' : 'e0';
  const shadows = elevation[shadowKey].map((s) => ({ ...s, color: n.shadow }));

  const cardFill = config.cardStyle === 'flat' ? n.surfaceSecondary : n.surface;
  // On a white background a flat white card would vanish: flat uses surfaceSecondary.
  const cardStroke = config.cardStyle === 'border' ? border.hairline : 0;

  return {
    config,
    signature: [
      LIBRARY_VERSION,
      config.variant,
      config.accent,
      config.background,
      config.cardStyle,
      config.density,
      config.emotionStyle,
      config.refine,
      config.refine === 'v3' ? (config.markerVariant === 'same' ? `shape-${config.markerShape}` : 'marker-mixed') : '',
    ].join('·'),
    color: {
      ...n,
      ...a,
      highlight: a.accentContainer,
      emotion: emotionColors,
    },
    type: typography,
    space: spacing,
    radius,
    border,
    icon: iconSize,
    size,
    variant: v,
    layout: {
      screenPadding: compact ? spacing.md : spacing.lg,
      // v3 "Soft & Clean": one more step of breathing room between sections.
      sectionGap: compact ? spacing.xl : config.refine === 'v3' ? spacing.xxxl : spacing.xxl,
      cardPadding: compact ? spacing.md : spacing.lg,
      cardGap: compact ? spacing.xs : spacing.sm,
      listGap: compact ? spacing.xs : spacing.sm,
    },
    card: {
      fill: cardFill,
      strokeWeight: cardStroke,
      stroke: n.border,
      shadows,
      // v3: generous, jelly-like corners (cards 20, hero surfaces 24).
      radius: config.refine === 'v3' && config.variant === 'soft' ? radius.xl : radius[v.cardRadius],
      heroRadius: config.refine === 'v3' && config.variant === 'soft' ? radius.xxl : radius[v.heroRadius],
    },
    chipRadius: radius[v.chipRadius],
    buttonRadius: radius[v.buttonRadius],
    inputRadius: config.refine === 'v3' && config.variant === 'soft' ? radius.xl : radius[v.inputRadius],
    refined: config.refine !== 'v1',
    v3: config.refine === 'v3',
    emotionTint: 0.07,
    emotionStyle:
      config.refine === 'v3'
        ? config.markerVariant === 'same' ? sameShapeMarker(config.markerShape) : MARKER_STYLES.mixed
        : emotionStyles(config.refine === 'v2')[config.emotionStyle],
    role: config.refine === 'v1' ? roleV1(n, a) : roleV2(n, a),
  };
}

type N = (typeof backgrounds)[keyof typeof backgrounds];
type A = (typeof accents)[keyof typeof accents];

function roleV1(n: N, a: A): Record<RoleKey, string> {
  return {
    chipSelectedFill: a.accentContainer, chipSelectedStroke: a.accentContainer, onChipSelected: a.onAccentContainer,
    navActiveFill: a.accentContainer, navActiveIcon: a.onAccentContainer,
    focusStroke: a.accent, searchIconActive: a.accent,
    metaLabel: a.accent,
    tonalFill: a.accentContainer, onTonal: a.onAccentContainer,
    optionSelectedFill: a.accentContainer, optionSelectedStroke: a.accentContainer, onOptionSelected: a.onAccentContainer,
    radioOn: a.accent,
    calendarSelectedFill: a.accent, onCalendarSelected: a.onAccent,
    heroFill: a.accentContainer, onHero: a.onAccentContainer,
    subtleAction: a.accentContainer, onSubtleAction: a.onAccentContainer,
    appIcon: a.accent,
    matchInk: a.onAccentContainer,
  };
}

/** Warm ivory + charcoal + neutral borders; the accent is reserved for primary actions and key highlights. */
function roleV2(n: N, a: A): Record<RoleKey, string> {
  return {
    chipSelectedFill: n.textPrimary, chipSelectedStroke: n.textPrimary, onChipSelected: n.background,
    navActiveFill: n.surfaceSecondary, navActiveIcon: n.textPrimary,
    focusStroke: n.borderStrong, searchIconActive: n.textPrimary,
    metaLabel: n.textSecondary,
    tonalFill: n.surfaceSecondary, onTonal: n.textPrimary,
    optionSelectedFill: n.surface, optionSelectedStroke: n.textPrimary, onOptionSelected: n.textPrimary,
    radioOn: n.textPrimary,
    calendarSelectedFill: n.textPrimary, onCalendarSelected: n.background,
    heroFill: n.surface, onHero: n.textPrimary,
    subtleAction: n.surfaceSecondary, onSubtleAction: n.textPrimary,
    appIcon: n.textPrimary,
    matchInk: a.onAccentContainer,
  };
}
