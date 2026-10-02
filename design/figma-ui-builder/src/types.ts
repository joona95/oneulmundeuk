// Shared types between the plugin sandbox (code.ts) and the Builder panel (ui.ts).

export type ScreenKey =
  | 'home'
  | 'recordEditor'
  | 'relatedMemories'
  | 'recordsList'
  | 'recordsCalendar'
  | 'explore'
  | 'searchResults'
  | 'recordDetail'
  | 'reminder'
  | 'settings';

export type DesignVariant = 'minimal' | 'soft' | 'playful';
export type AccentKey = 'coral' | 'sage' | 'sky' | 'lavender';
export type BackgroundKey = 'white' | 'ivory' | 'coolGray';
export type CardStyle = 'flat' | 'border' | 'elevation';
export type Density = 'comfortable' | 'compact';
export type EmotionStyleKey = 'blob' | 'creature' | 'doodle' | 'geometric';
export type HomeVariant = 'A' | 'B' | 'C';
/**
 * v1 = first pass · v2 = restrained accent, polished blob, label-first emotion picking
 * v3 = emotions become soft color markers (no per-emotion meaning), Thread B related memories,
 *      no user-selectable emotion style, 5–8% emotion tints only in special surfaces.
 */
export type Refinement = 'v1' | 'v2' | 'v3';
/** v3 emotion marker comparison: A = one soft shape for all emotions, B = mixed soft shapes. */
export type MarkerVariant = 'same' | 'mixed';
export type MarkerShapeKey = 'jelly' | 'heart' | 'star' | 'roundSquare' | 'pebble' | 'diamond';
export type ShapePickerLayout = 'list' | 'grid';
export type RelatedVariant = 'current' | 'threadA' | 'threadB';

export interface BuilderConfig {
  screen: ScreenKey | 'all';
  variant: DesignVariant;
  accent: AccentKey;
  background: BackgroundKey;
  cardStyle: CardStyle;
  density: Density;
  emotionStyle: EmotionStyleKey;
  homeVariant: HomeVariant;
  refine: Refinement;
  relatedVariant: RelatedVariant;
  markerVariant: MarkerVariant;
  /** "내 감정 조각": the user's marker shape (v3, Same Shape system). */
  markerShape: MarkerShapeKey;
  /** Settings › 내 감정 조각 layout. Final: grid (list kept for reference only). */
  shapePicker: ShapePickerLayout;
  /** Related Memories subtitle. Final: none. */
  relatedSubtitle: 'none';
}

/**
 * DESIGN FREEZE (2026-10-02) — final configuration. "Generate All Screens" with these values produces the
 * final screen set. v1/v2/mixed options remain only for reference/debug via the Builder panel and
 * "Generate Comparisons". Motion = soft interaction (squish→settle, tap, press, fade+rise; no idle).
 */
export const DEFAULT_CONFIG: BuilderConfig = {
  screen: 'all',
  variant: 'soft', // Visual: Soft & Clean
  accent: 'sage',
  background: 'ivory', // Warm Ivory
  cardStyle: 'border',
  density: 'comfortable',
  emotionStyle: 'blob', // v1/v2 only — ignored in v3
  homeVariant: 'B',
  refine: 'v3',
  relatedVariant: 'threadB',
  markerVariant: 'same', // Emotion Marker: Same Shape
  markerShape: 'jelly', // Default Shape: 동글동글
  shapePicker: 'grid', // 2-column grid
  relatedSubtitle: 'none',
};

export type UiToPlugin =
  | { type: 'generate'; config: BuilderConfig; all: boolean }
  | { type: 'generate-home-variants'; config: BuilderConfig }
  | { type: 'generate-emotion-sheet'; config: BuilderConfig }
  | { type: 'generate-comparisons'; config: BuilderConfig }
  | { type: 'rebuild-library'; config: BuilderConfig }
  | { type: 'save-config'; config: BuilderConfig }
  | { type: 'close' };

export type PluginToUi =
  | { type: 'init'; config: BuilderConfig; screens: { key: ScreenKey; label: string }[] }
  | { type: 'status'; level: 'info' | 'error' | 'done'; message: string };
