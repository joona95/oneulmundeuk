// Screen registry — order here is the order of "Generate All Screens".
// Add a screen: write a ScreenDef, register it here, add its key to ScreenKey (types.ts).
import type { ScreenKey } from '../types';
import { HomeScreen } from './home';
import { RecordEditorScreen, RelatedMemoriesScreen } from './write';
import { RecordsCalendarScreen, RecordsListScreen } from './records';
import { ExploreScreen, SearchResultsScreen } from './explore';
import { RecordDetailScreen, ReminderScreen } from './detail';
import { SettingsScreen } from './settings';
import type { ScreenDef } from './shared';

export const SCREENS: ScreenDef[] = [
  HomeScreen,
  RecordEditorScreen,
  RelatedMemoriesScreen,
  RecordsListScreen,
  RecordsCalendarScreen,
  ExploreScreen,
  SearchResultsScreen,
  RecordDetailScreen,
  ReminderScreen,
  SettingsScreen,
];

export const SCREEN_BY_KEY = Object.fromEntries(SCREENS.map((s) => [s.key, s])) as Record<ScreenKey, ScreenDef>;

export { HOME_VARIANTS } from './home';
export type { ScreenContext, ScreenDef } from './shared';
