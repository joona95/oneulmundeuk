// Component registry. Screens ask the Library for components by these names.
// To add a component: write a ComponentDef (see controls.ts) and add it below.
import type { ComponentDef } from '../core/library';
import { Icon } from './icon';
import { EmotionIndicator } from './emotion';
import { AppBar, BottomNav, StatusBar } from './chrome';
import { Button, CategoryChip, CategoryTag, EmotionOption, EmptyState, Fab, SearchBar, SectionHeader, Segmented, SettingsRow } from './controls';
import { Notification, Photo, QuickEntry, RecordCard, RediscoveryCard, RelatedMemoryCard } from './cards';
import { EmotionStyleOption, MemoryEntry, MemoryQuote, MoodPill, ShapeOption, ThreadItem, TimeGap } from './refine';

const ALL: ComponentDef[] = [
  Icon,
  EmotionIndicator,
  StatusBar,
  AppBar,
  BottomNav,
  CategoryChip,
  CategoryTag,
  Button,
  Fab,
  SearchBar,
  Segmented,
  SectionHeader,
  EmotionOption,
  SettingsRow,
  EmptyState,
  MoodPill,
  RecordCard,
  RediscoveryCard,
  RelatedMemoryCard,
  QuickEntry,
  Notification,
  Photo,
  EmotionStyleOption,
  ThreadItem,
  TimeGap,
  MemoryQuote,
  MemoryEntry,
  ShapeOption,
];

export const COMPONENTS: Record<string, ComponentDef> = Object.fromEntries(ALL.map((d) => [d.name, d]));

/** Build order for "Rebuild component library" (dependencies first). */
export const COMPONENT_ORDER = ALL.map((d) => d.name);

export { NAV_ITEMS } from './chrome';
export type { NavKey } from './chrome';
export { REDISCOVERY_STYLES } from './cards';
