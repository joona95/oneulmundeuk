// Design-variant overrides. Each variant only changes token *choices*, never component structure.
import type { DesignVariant } from '../types';
import type { RadiusKey, TypeKey } from './base';

export interface VariantSpec {
  label: string;
  cardRadius: RadiusKey;
  heroRadius: RadiusKey; // quick entry, rediscovery cards
  chipRadius: RadiusKey;
  buttonRadius: RadiusKey;
  inputRadius: RadiusKey;
  /** Greeting / screen hero type style. */
  heroType: TypeKey;
  /** Small accent dot before section titles. */
  sectionDot: boolean;
  /** Big opening quote glyph on memory cards. */
  quoteGlyph: boolean;
  /** Tint rediscovery cards with accentContainer instead of surface. */
  tintedRediscovery: boolean;
  /** Bottom-nav active indicator shape. */
  navIndicator: 'pill' | 'dot';
}

export const variants: Record<DesignVariant, VariantSpec> = {
  minimal: {
    label: 'Minimal',
    cardRadius: 'md',
    heroRadius: 'md',
    chipRadius: 'sm',
    buttonRadius: 'md',
    inputRadius: 'md',
    heroType: 'title',
    sectionDot: false,
    quoteGlyph: false,
    tintedRediscovery: false,
    navIndicator: 'dot',
  },
  soft: {
    label: 'Soft',
    cardRadius: 'lg',
    heroRadius: 'xl',
    chipRadius: 'full',
    buttonRadius: 'full',
    inputRadius: 'lg',
    heroType: 'display',
    sectionDot: false,
    quoteGlyph: false,
    tintedRediscovery: false,
    navIndicator: 'pill',
  },
  playful: {
    label: 'Slightly Playful',
    cardRadius: 'xl',
    heroRadius: 'xl',
    chipRadius: 'full',
    buttonRadius: 'full',
    inputRadius: 'xl',
    heroType: 'display',
    sectionDot: true,
    quoteGlyph: true,
    tintedRediscovery: true,
    navIndicator: 'pill',
  },
};
