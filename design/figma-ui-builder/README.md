# Echo UI Builder

> `Echo` is only the internal project name. The app's real name hasn't been decided, so the generated UI never shows an app name.

A Figma plugin that generates the **Echo** Android UI (a local-first AI journal) from design tokens. It is a reusable design tool, not a one-off script: you change options in the Builder panel, generate again, and compare.

- Real Figma layers only: text is `TextNode`, cards are auto-layout `Frame`s, icons and emotions are vector `Component`s. Every screen is assembled from **component instances**.
- It uses only the Figma Plugin API. It does not depend on Figma MCP or any other external tool, and it makes no network calls (`networkAccess: none`).
- Viewport: 360 × 800 (Android, dp = px). The status bar (24) and bottom nav (64 + 16 gesture inset) are part of every screen.

---

## 1. Install and run in Figma Desktop

Requirements: Figma **Desktop app** and Node 18+.

```bash
cd echo-ui-builder
npm install
npm run build        # → dist/code.js, dist/ui.html
```

Then in Figma Desktop:

1. Open any Design file (a new, empty file is fine).
2. Menu **Plugins → Development → Import plugin from manifest…**
3. Select `echo-ui-builder/manifest.json`.
4. Run it from **Plugins → Development → Echo UI Builder**. The Builder panel opens on the right.
5. Choose your options and click **Generate Selected Screen** or **Generate All Screens**.

What the plugin creates in your file:

| Page | Contents |
| --- | --- |
| `Echo Components` | One Section per theme (`Library · v3·soft·sage·…`) holding every component and variant set |
| `Echo Screens` | One Section per generate run, named after the screen(s) and options, with the frames side by side |

**After you change the code:** run `npm run build` again (or keep `npm run watch` running), then re-run the plugin. You don't need to import it again. If a component's design changed, also click **Rebuild Component Library**, or bump `LIBRARY_VERSION` in `src/tokens/resolve.ts` (see §5).

**Fonts:** the plugin uses **Pretendard** if it is installed on your machine. Otherwise it falls back to **Noto Sans KR** (bundled with Figma), then IBM Plex Sans KR, then Inter. The status line shows which font family was used. For final designs, install Pretendard so it matches the Android app.

---

## 2. Builder panel

| Option | Values |
| --- | --- |
| Screen | All Screens, Home, Record Editor, Related Memories, Records — List, Records — Calendar, Explore, Semantic Search Results, Record Detail, Reminder Notification, Settings |
| Design Variant | Minimal · **Soft** · Slightly Playful |
| Accent | Coral · **Sage** · Sky · Lavender |
| Background | White · **Warm Ivory** · Cool Gray |
| Card Style | Flat · **Subtle Border** · Soft Elevation |
| Density | **Comfortable** · Compact |
| Emotion Style | **Abstract Blob** · Small Creature · Doodle · Geometric Symbol |
| Home Variant | A 기록 입력 중심 · **B 균형 (default)** · C Rediscovery 중심 |

Bold values are the defaults. The plugin remembers your last options (`figma.clientStorage`).

Buttons:

- **Generate Selected Screen**: builds the screen chosen in the dropdown. If "All Screens" is chosen, it builds all of them.
- **Generate All Screens**: builds all 10 screens with the current options.
- **Compare Home A/B/C**: builds the three Home variants side by side.
- **Emotion Sheet**: shows all 4 emotion styles × 7 emotions at 56 / 28 / 20 / 16 px, plus the 8 px calendar dots.
- **Rebuild Component Library**: discards and rebuilds the component Section for the current theme.

---

## 3. Architecture

```
 Builder panel (ui.html)                     Plugin sandbox (code.ts)
 ┌──────────────────────┐  BuilderConfig   ┌──────────────────────────────────────────┐
 │ options → config     │ ───────────────▶ │ resolveTheme(config)  → Theme (tokens)   │
 │ status ◀──────────── │ ◀─────────────── │ Library(theme)        → components, cached│
 └──────────────────────┘   status msgs    │ SCREENS[key].build(ctx) → FrameNode      │
                                           └──────────────────────────────────────────┘
```

The layers depend in one direction only:

```
tokens  →  core (layout helpers, fonts, icons, Library)  →  components  →  screens  →  code.ts
emotions (pure SVG generators) ─────────────────────────────┘
data/sample.ts (Korean sample records + formatters) ─────────────────────────┘
```

- **Tokens never appear as literals in components/screens.** Everything reads `t.space.md`, `t.color.textPrimary`, `t.type.body`, `t.size.fab`, `t.layout.cardPadding`, and so on.
- **Components** are built once per theme signature and cached on the `Echo Components` page. Screens never draw a card themselves. They call `lib.instance('Record Card', { text, swap, hide, show, variant })`.
- **Screens** only compose instances and layout frames, and fill them with sample data.

### File structure

```
manifest.json                Figma plugin manifest (main: dist/code.js, ui: dist/ui.html)
build.mjs                    esbuild: bundles code.ts; bundles ui.ts and inlines it into ui.html
src/
  code.ts                    message router: generate / home variants / emotion sheet / rebuild
  types.ts                   BuilderConfig, option unions, UI⇄plugin message types
  tokens/
    base.ts                  spacing, radius, border, elevation, icon size, component size, typography
    palettes.ts              background × 3, accent × 4, emotion × 7 (the only file with hex colors)
    variants.ts              Minimal / Soft / Playful token choices
    resolve.ts               config → Theme (+ density, card style, LIBRARY_VERSION)
  core/
    fonts.ts                 font family detection + weight → style mapping
    layout.ts                frame(), add(), text(), para(), cardSurface(), topBorder() …
    icons.ts                 30 line icons (24 grid) as SVG
    library.ts               Library: get-or-build components, instance(), setText/hide/show/swap/tint
  emotions/
    types.ts                 EMOTIONS, labels, EmotionStyleDef
    shapes.ts                polar blob silhouettes (Catmull-Rom → Bézier)
    blob.ts creature.ts doodle.ts geometric.ts
    index.ts                 EMOTION_STYLES registry
  components/
    _util.ts                 comp(), variantSet(), icon(), emotionInst()
    icon.ts emotion.ts       Icon set, Emotion set (emotion × size)
    chrome.ts                Status Bar, App Bar (title/back/close), Bottom Nav (active=…)
    controls.ts              Category Chip/Tag, Button, FAB, Search Bar, Segmented, Section Header,
                             Emotion Option, Settings Row, Empty State
    cards.ts                 Record Card, Rediscovery Card (style registry), Related Memory Card,
                             Quick Record Entry (regular/large/bar), Notification, Photo
    index.ts                 COMPONENTS registry
  screens/
    shared.ts                scaffold(), section(), hScroll(), recordCard(), relatedCard() …
    home.ts                  HOME_VARIANTS A/B/C
    write.ts                 Record Editor, Related Memories
    records.ts               Records List, Records Calendar
    explore.ts               Explore, Semantic Search Results
    detail.ts                Record Detail, Reminder Notification
    settings.ts              Settings (Category / Emotion / Reminder / Privacy)
    index.ts                 SCREENS registry (order = Generate All order)
  data/sample.ts             realistic Korean records, related sets, search results, calendar
  ui/ui.html, ui/ui.ts       Builder panel
scripts/
  figma-mock.mjs             offline Figma API mock enforcing common runtime rules
  render-html.mjs            mock tree → HTML/CSS (auto layout → flexbox) for visual QA
  validate.mjs               runs the plugin across an option matrix, checks screens, writes previews
  screenshot.py              (optional) Playwright screenshots of preview/*.html
```

### Token structure

| Group | Tokens |
| --- | --- |
| color | background, surface, surfaceSecondary, textPrimary, textSecondary, textTertiary, border, accent, accentContainer, onAccent, onAccentContainer, emotion.{calm, joy, excited, neutral, tired, anxious, sad}.{fill, ink} |
| typography | display 28/36·700, title 22/30·600, heading 17/24·600, memory 17/28·400, bodyLarge 17/30·400, body 15/24·400, bodySmall 14/21·400, label 13/18·600, caption 12/16·500 |
| spacing | hair 2, xxs 4, xs 8, sm 12, md 16, lg 20, xl 24, xxl 32, xxxl 40 |
| radius | sm 8, md 12, lg 16, xl 20, full 999 |
| border | hairline 1, focus 1.5, doodleStroke 1.75 |
| elevation | e0 none, e1 y1 b3 6%, e2 y4 b16 6% + y1 b2 4% |
| icon size | sm 16, md 20, lg 24 |
| component size | statusBar 24, appBar 56, bottomNav 64 + gestureInset 16, button 48, fab 56, chip 32, searchBar 52, emotion xs 8 / sm 20 / md 28, calendarCell 44 … |
| derived (resolve.ts) | layout.screenPadding / sectionGap / cardPadding / listGap (density), card.fill / stroke / shadows / radius (card style + variant), chip / button / input radius (variant) |

### Component hierarchy

```
Icon (set: name=…)                     Emotion (set: size × emotion, drawn by the active style)
   │                                       │
   ├─ Status Bar  ├─ App Bar (kind)  ├─ Bottom Nav (active)
   ├─ Button (kind) ├─ FAB ├─ Search Bar (state) ├─ Segmented (active) ├─ Settings Row (control)
   ├─ Category Chip (state) ├─ Category Tag ├─ Section Header ├─ Emotion Option (state)
   └─ Record Card ─────────┬─ uses Emotion + Category Tag instances
      Rediscovery Card (style=card|hero|compact)
      Related Memory Card (timeline rail + card)
      Quick Record Entry (size=regular|large|bar)
      Notification, Empty State, Photo
```

Instance overrides go by layer name: text layers (`title`, `body`, `time`, `quote`, `period`, `date`, `label` …) and nested instances (`emotion`, `category`, `leading`). Keep these names stable when you edit a component, because screens rely on them.

---

## 4. Screens and the product flow

| Screen | What it shows |
| --- | --- |
| Home A / **B** / C | today's date, "오늘은 어떤 생각을 하고 있나요?", Quick Record Entry, "다시 만난 생각" rediscovery cards (한 달 전 / 6개월 전 / 1년 전 이맘때), 최근 기록. In B, writing and rediscovery have equal visual weight. |
| Record Editor | long-form text with a comfortable 17/30 type style; a bottom sheet with emotion (7), category, tags, photo, Save |
| Related Memories | "이 생각과 이어지는 기록을 찾았어요" plus 4 past records on a timeline. The user's own sentences are the hero, with no AI prose. |
| Records — List / Calendar | segmented switch, category filter, date groups / month grid with ≤3 emotion dots per day |
| Explore | "과거의 나에게 물어보세요.", semantic search, example questions, topics |
| Semantic Search Results | the query, relevance/time sort, cards with the matched phrase emphasized |
| Record Detail | the full text, emotion, category, tags, "이어지는 기록", "지금의 생각 덧붙이기" |
| Reminder Notification | lock-screen notification with the past sentence and a privacy note |
| Settings | categories, emotion style (with preview), reminder frequency / day / time, lock, backup, import |

All sample content is in `src/data/sample.ts`. "Today" is 2026-10-02.

---

## 4b. Refinement v2 and comparison frames

v2 is a second-pass refinement. It keeps the IA and layouts and changes only details:

- **Restrained accent.** Selection states (chips, nav, calendar day, radios) use charcoal. Focus borders are neutral. Sage is reserved for primary actions, the selected emotion style, the "지금" node, and search-match text. All of this is driven by `theme.role.*` in `tokens/resolve.ts` (`roleV1` / `roleV2`), so no component branches on colors.
- **Polished blob v2** (`emotions/shapes.ts → BLOB_V2`). The 7 shapes have matched optical size, the neutral emotion is a squircle (not a circle), joy has a crown that lifts upward, and anxious has an irregular tremble. v1 is kept as `BLOB_V1` for comparison.
- **User-facing style names:** 몽글몽글 / 꼬물꼬물 / 끄적끄적 / 반듯반듯 (`EmotionStyleDef.userLabel`). Design names are used only in the Builder and in layer names.
- **Emotions are picked by icon + label.** Home's Quick Entry shows a `+ 기분` pill (`Mood Pill`), and the Editor uses a halo + label (`Emotion Option`). Places you only scan (calendar, cards, timeline) show the icon alone.
- **Settings:** `Emotion Style Option` cards show the 7 emotions in each style.
- **Related Memories:** `relatedVariant = current | threadA | threadB` (`Thread Item`, `Time Gap`, `Memory Quote`).
- **MVP scope:** tags and "기존 메모 가져오기" are hidden in v2.

**Generate Comparisons** creates three Sections on the `Echo Screens` page and never touches existing screens:

1. **Emotion:** v1 blob, v2 blob, Creature, Doodle and Geometric, in color and as mono silhouettes.
2. **Related Memories:** the current version (v1), Thread A and Thread B (v2).
3. **Settings:** the radio list (v1) next to the preview cards (v2).

**Adopting v2:** in `src/types.ts`, set `DEFAULT_CONFIG.refine = 'v2'` and `relatedVariant` to the chosen variant, then rebuild. Remove the v1 branches whenever you like (search for `t.refined` / `roleV1`).

`node scripts/emotion-lab.mjs` renders every emotion style to `preview/emotion-lab.html` without Figma, for fast shape iteration.

## 4c. Design Freeze (2026-10-02) — final defaults

**Design Freeze.** No new variants or comparison frames are added after this point. "Generate All Screens" with `DEFAULT_CONFIG` produces the final 10 screens; comparisons are generated only by **Generate Comparisons** and are kept as reference. Configs saved before the freeze are ignored, because the clientStorage key is versioned.

```ts
DEFAULT_CONFIG = {
  screen: 'all', variant: 'soft', accent: 'sage', background: 'ivory', cardStyle: 'border', density: 'comfortable',
  emotionStyle: 'blob' /* v1·v2 only */, homeVariant: 'B',
  refine: 'v3', relatedVariant: 'threadB', markerVariant: 'same', markerShape: 'jelly', shapePicker: 'grid', relatedSubtitle: 'none',
}
```

The principle: "보기에는 차분하고, 만지면 말랑한 앱." Motion is soft interaction only.

- **Emotion = Color · Shape = personal preference · Label = meaning · Motion = soft tactile feedback.**
- **내 감정 조각** (`emotions/markers.ts → MARKER_SHAPES`): the user picks one of 6 jelly shapes — 동글동글, 하트, 별, 네모, 조약돌, 마름모. Every emotion uses that shape; only the color changes.
  - All shapes share the same jelly treatment: a slightly irregular silhouette, soft edges, a small highlight, and normalized visual weight.
  - The Builder exposes the choice as "내 감정 조각 · Shape".
- **Settings › 내 감정 조각** (`Shape Option`): the final layout is the 2-column grid; the `list` layout is kept for reference only. Each option previews all 7 emotion colors in its shape. The selected option gets a small check badge and a slightly stronger neutral border.
- **Editor selected emotion:** a very subtle surface halo with a faint neutral edge, the marker one step larger (32px, `size=lg`), and a semibold label. There is no black ring.
- **Related metadata:** "6개월 전" (secondary text, semibold), then "·" and the date (muted), then the category (muted, one line). The user's sentence in memory type is the hero.
- **Responsive structure:** chips and buttons hug with `minHeight` so they grow with font scale. The Quick Entry input uses `minHeight`. The Category Tag caps its width (`categoryTagMax` 120) and its label is one line. Category chips and Home rediscovery cards scroll horizontally. Long text either wraps or is truncated with `maxLines`. The FAB is absolutely positioned above the bottom nav.
- **Display rules:**

  | Context | What is shown |
  | --- | --- |
  | Editor, Detail | shape + color + label |
  | List, Related, Rediscovery | 14px marker |
  | Calendar | color dot only, whatever the shape |

- **Copy roles** (`data/copy.ts`), never unified:

  | Screen | Action | Copy |
  | --- | --- | --- |
  | Home | 다시 만남 | "다시 만난 생각" |
  | After save | 떠오름 | "문득, 예전의 생각이 떠올랐어요" (no subtitle) |
  | Explore | 찾아봄 | "과거의 나에게 물어보세요." |

- **Motion:** squish → settle on select/tap and a soft give on press, the same for every shape. Past records surface with a fade and a 4–6px rise, once. There is no idle motion. `MarkerShapeDef.motion` is the hook for future per-shape motion (heart pulse, star pop), which is not in the MVP.
- **Mixed Shapes (B)** was compared and not adopted. Its code remains for the record only.

**Generate Comparisons** creates four Sections:

1. **Shape Picker:** Settings with the 2-column grid, Settings with compact cards, and a shape × palette sheet.
2. **Related Memories:** the final screen, as reference.
3. **Example:** Record Editor and Record List with the heart shape.
4. **Motion (final).**

**Generate All Screens** with the default options produces the final screen set.

## 5. Extending the Builder (recipes)

These recipes are written so you can hand them straight to Claude Code.

**"Home Rediscovery Card variant 하나 추가해줘."**
Add a builder function to `REDISCOVERY_STYLES` in `src/components/cards.ts`. It becomes a `style=<key>` variant automatically. Then use it from a Home variant with `rediscoveryCard(ctx, '<key>', period, record)`.

**"Record Card 디자인을 변경해줘."**
Edit `RecordCard` in `src/components/cards.ts`. Keep the layer names `emotion`, `time`, `category`, `body`, `photo`, or update `recordCard()` in `screens/shared.ts`. Then click **Rebuild Component Library**, or bump `LIBRARY_VERSION`.

**"새로운 emotion style을 추가해줘."**
1. Create `src/emotions/<name>.ts` that exports an `EmotionStyleDef` (`draw(emotion, {fill, ink})` returns SVG on a 24 grid).
2. Register it in `src/emotions/index.ts`.
3. Add the key to `EmotionStyleKey` in `src/types.ts`.
4. Add an option to `OPTIONS.emotionStyle` in `src/ui/ui.ts`.

**New Home variant:** add a builder to `HOME_VARIANTS` in `screens/home.ts`, add its key to `HomeVariant`, and add a UI option.

**New screen:** write a `ScreenDef` in `screens/`, register it in `screens/index.ts`, and add its key to `ScreenKey`.

**New component:** write a `ComponentDef` and add it to `ALL` in `components/index.ts`.

**New token / accent / background:** edit `tokens/palettes.ts` or `tokens/base.ts`. TypeScript then points you to every place that needs a value.

**Library cache:** components are cached per signature: `LIBRARY_VERSION · variant · accent · background · cardStyle · density · emotionStyle`. After changing a component generator, either click **Rebuild Component Library** (current theme) or bump `LIBRARY_VERSION` (all themes). Screens generated earlier keep pointing at the old components.

---

## 6. Build, type check, validation

```bash
npm run typecheck   # tsc --noEmit with @figma/plugin-typings (strict)
npm run build       # esbuild → dist/
npm run validate    # typecheck + build + offline run against the Figma API mock
npm run preview     # validate + Playwright screenshots of preview/*.html (needs python playwright)
```

`npm run validate` runs `dist/code.js` against `scripts/figma-mock.mjs` with these scenarios:

- the default theme: all screens, Home A/B/C, the Emotion Sheet, Rebuild, and library reuse
- a 4-config matrix that exercises every option value at least once
- font fallback when only Inter is available

It fails on any error thrown by the plugin, on any screen that isn't 360×800, on screens with almost no instances, and on empty text. The mock enforces the rules that most often break plugins in real Figma:

- FILL/HUG and ABSOLUTE need an auto-layout parent.
- Text can't be edited before its font is loaded.
- Nodes inside an instance can't be added or removed.
- Variant names must be valid.
- SVGs must not contain NaN.

It also writes HTML previews (auto layout mapped to flexbox) to `preview/` for a quick visual check. They are approximate; the Figma layers are the source of truth.

The mock does not replace a real run in Figma. Rendering, text metrics and some API edge cases can only be confirmed in Figma Desktop.

---

## 7. Troubleshooting

| Symptom | Fix |
| --- | --- |
| "Import plugin from manifest" is missing | Use the Figma **Desktop** app; development plugins can't be imported in the browser. |
| Plugin opens but nothing happens | Check the status line. Open **Plugins → Development → Show/Hide console** for the stack trace. |
| Korean text uses a fallback font | Install Pretendard, then regenerate. The status line shows the family that was used. |
| A code change doesn't show up | Run `npm run build`, then click **Rebuild Component Library** (components are cached). |
| Old screens look stale after a rebuild | Expected: they reference the previous components. Generate again. |
