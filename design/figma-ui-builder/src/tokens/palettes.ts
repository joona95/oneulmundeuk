// Color palettes. Hex values live ONLY in this file.
import type { AccentKey, BackgroundKey } from '../types';

export interface NeutralPalette {
  background: string;
  surface: string;
  surfaceSecondary: string;
  textPrimary: string;
  textSecondary: string;
  textTertiary: string;
  border: string;
  borderStrong: string;
  shadow: string;
}

export const backgrounds: Record<BackgroundKey, NeutralPalette> = {
  ivory: {
    background: '#FAF7F2',
    surface: '#FFFFFF',
    surfaceSecondary: '#F3EEE6',
    textPrimary: '#26231F',
    textSecondary: '#6F6A62',
    textTertiary: '#A39D93',
    border: '#EAE3D8',
    borderStrong: '#D6CCBD',
    shadow: '#3B2F1E',
  },
  white: {
    background: '#FFFFFF',
    surface: '#FFFFFF',
    surfaceSecondary: '#F5F4F2',
    textPrimary: '#1F1F1F',
    textSecondary: '#6B6B6B',
    textTertiary: '#A3A3A3',
    border: '#ECECEC',
    borderStrong: '#D4D4D4',
    shadow: '#1F1F1F',
  },
  coolGray: {
    background: '#F4F5F7',
    surface: '#FFFFFF',
    surfaceSecondary: '#ECEEF2',
    textPrimary: '#1E2126',
    textSecondary: '#646A73',
    textTertiary: '#9EA4AD',
    border: '#E1E4E9',
    borderStrong: '#CCD1D9',
    shadow: '#1E2126',
  },
};

export interface AccentPalette {
  accent: string;
  accentContainer: string;
  onAccent: string;
  onAccentContainer: string;
}

export const accents: Record<AccentKey, AccentPalette> = {
  sage: { accent: '#5F8F76', accentContainer: '#E3EFE7', onAccent: '#FFFFFF', onAccentContainer: '#2E4F3E' },
  coral: { accent: '#D9705A', accentContainer: '#FBE6DF', onAccent: '#FFFFFF', onAccentContainer: '#6B2E20' },
  sky: { accent: '#4F86BA', accentContainer: '#E0ECF7', onAccent: '#FFFFFF', onAccentContainer: '#1F3E5C' },
  lavender: { accent: '#7F78BC', accentContainer: '#ECEAF8', onAccent: '#FFFFFF', onAccentContainer: '#3A3570' },
};

/** Emotion palette: one fill + one darker ink per emotion. */
export const emotionColors = {
  calm: { fill: '#9CC9B4', ink: '#4E8A70' },
  joy: { fill: '#F6C76B', ink: '#B98A25' },
  excited: { fill: '#F49A7E', ink: '#C25E42' },
  neutral: { fill: '#CFC9C0', ink: '#8C857A' },
  tired: { fill: '#B7B3D9', ink: '#706BA6' },
  anxious: { fill: '#8FB8DE', ink: '#4D7FAE' },
  sad: { fill: '#7F9CC2', ink: '#48668F' },
} as const;
