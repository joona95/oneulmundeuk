// Plugin entry (Figma sandbox). Routes Builder panel messages to the generators.
import { COMPONENT_ORDER, COMPONENTS } from './components';
import { loadFonts } from './core/fonts';
import { Library } from './core/library';
import { add, frame, solid, text } from './core/layout';
import { EMOTION_STYLES, EMOTIONS, EMOTION_LABEL, wrapSvg } from './emotions';
import { HOME_VARIANTS, SCREENS, SCREEN_BY_KEY } from './screens';
import type { ScreenDef } from './screens';
import { resolveTheme } from './tokens/resolve';
import type { Theme } from './tokens/resolve';
import { variants } from './tokens/variants';
import { DEFAULT_CONFIG } from './types';
import { motionBoard, relatedCopyComparison, shapeAppliedExamples, shapePickerComparison } from './comparisons/v3';
import type { BuilderConfig, HomeVariant, PluginToUi, UiToPlugin } from './types';

const SCREENS_PAGE = 'Echo Screens';
// Versioned: configs saved before the Design Freeze are ignored, so the panel opens on the final defaults.
const STORAGE_KEY = 'echo-builder-config@freeze-2026-10-02';
const FRAME_GAP = 80;
const SECTION_PAD = 80;

const post = (m: PluginToUi) => figma.ui.postMessage(m);
const status = (message: string, level: 'info' | 'error' | 'done' = 'info') => post({ type: 'status', level, message });

figma.showUI(__html__, { width: 340, height: 760, themeColors: true, title: 'Echo UI Builder' });

(async () => {
  const saved = (await figma.clientStorage.getAsync(STORAGE_KEY)) as Partial<BuilderConfig> | undefined;
  post({
    type: 'init',
    config: { ...DEFAULT_CONFIG, ...(saved ?? {}) },
    screens: SCREENS.map((s) => ({ key: s.key, label: s.label })),
  });
})();

figma.ui.onmessage = async (msg: UiToPlugin) => {
  try {
    switch (msg.type) {
      case 'generate':
        await figma.clientStorage.setAsync(STORAGE_KEY, msg.config);
        await generate(msg.config, msg.all ? SCREENS.map((s) => ({ def: s, config: msg.config })) : [{ def: SCREEN_BY_KEY[msg.config.screen === 'all' ? 'home' : msg.config.screen], config: msg.config }]);
        break;
      case 'generate-home-variants': {
        const jobs = (Object.keys(HOME_VARIANTS) as HomeVariant[]).map((v) => ({ def: SCREEN_BY_KEY.home, config: { ...msg.config, homeVariant: v } }));
        await generate(msg.config, jobs, 'Home A / B / C');
        break;
      }
      case 'generate-comparisons':
        await comparisons(msg.config);
        break;
      case 'generate-emotion-sheet':
        await emotionSheet(msg.config);
        break;
      case 'rebuild-library':
        await rebuildLibrary(msg.config);
        break;
      case 'save-config':
        await figma.clientStorage.setAsync(STORAGE_KEY, msg.config);
        break;
      case 'close':
        figma.closePlugin();
        break;
    }
  } catch (e) {
    console.error(e);
    status(e instanceof Error ? e.message : String(e), 'error');
  }
};

async function screensPage(): Promise<PageNode> {
  let page = figma.root.children.find((p) => p.name === SCREENS_PAGE);
  if (!page) {
    page = figma.createPage();
    page.name = SCREENS_PAGE;
  }
  await page.loadAsync();
  return page;
}

function describe(c: BuilderConfig): string {
  return [c.refine, variants[c.variant].label, c.accent, c.background, c.cardStyle, c.density, c.emotionStyle, `related:${c.relatedVariant}`].join(' · ');
}

/** New section stacked below everything already on the page. */
function newSection(page: PageNode, name: string, t: Theme): SectionNode {
  const bottom = page.children.reduce((m, n) => Math.max(m, n.y + n.height), 0);
  const s = figma.createSection();
  s.name = name;
  s.fills = [solid(t.color.surfaceSecondary)];
  page.appendChild(s);
  s.x = 0;
  s.y = page.children.length > 1 ? bottom + SECTION_PAD * 2 : 0;
  return s;
}

function layoutInSection(section: SectionNode, nodes: SceneNode[]) {
  let x = SECTION_PAD;
  let maxH = 0;
  for (const n of nodes) {
    n.x = x;
    n.y = SECTION_PAD;
    x += n.width + FRAME_GAP;
    maxH = Math.max(maxH, n.height);
  }
  section.resizeWithoutConstraints(x - FRAME_GAP + SECTION_PAD, maxH + SECTION_PAD * 2);
}

async function generate(base: BuilderConfig, jobs: { def: ScreenDef; config: BuilderConfig }[], title?: string) {
  status('폰트 불러오는 중…');
  const family = await loadFonts();
  const theme = resolveTheme(base);
  status(`컴포넌트 준비 중… (${family})`);
  const lib = new Library(theme, COMPONENTS);
  await lib.init(false);

  const page = await screensPage();
  const section = newSection(page, `${title ?? (jobs.length > 1 ? 'All Screens' : jobs[0].def.label)} — ${describe(base)}`, theme);
  const made: FrameNode[] = [];
  const failed: string[] = [];
  for (const job of jobs) {
    status(`${job.def.label} 생성 중…`);
    try {
      const frameNode = job.def.build({ t: theme, lib, config: job.config });
      section.appendChild(frameNode);
      made.push(frameNode);
    } catch (e) {
      console.error(job.def.label, e);
      failed.push(`${job.def.label}: ${e instanceof Error ? e.message : String(e)}`);
    }
  }
  layoutInSection(section, made);
  await figma.setCurrentPageAsync(page);
  page.selection = made;
  figma.viewport.scrollAndZoomIntoView([section]);
  if (failed.length) status(`${made.length}개 생성, ${failed.length}개 실패 — ${failed.join(' / ')}`, 'error');
  else status(`${made.length}개 화면을 생성했어요. (${family})`, 'done');
}

async function rebuildLibrary(config: BuilderConfig) {
  await loadFonts();
  const theme = resolveTheme(config);
  const lib = new Library(theme, COMPONENTS);
  await lib.init(true);
  for (const name of COMPONENT_ORDER) {
    status(`${name} 생성 중…`);
    lib.get(name);
  }
  const page = figma.root.children.find((p) => p.name === 'Echo Components');
  if (page) {
    await figma.setCurrentPageAsync(page);
    figma.viewport.scrollAndZoomIntoView([lib.container]);
  }
  status(`컴포넌트 ${COMPONENT_ORDER.length}종을 다시 만들었어요. 기존 화면은 이전 컴포넌트를 계속 참조해요.`, 'done');
}

/** Side-by-side comparison of every emotion style at 28 / 20 / 16 px, plus 8px calendar dots. */
async function emotionSheet(config: BuilderConfig) {
  await loadFonts();
  const t = resolveTheme(config);
  const page = await screensPage();
  const section = newSection(page, 'Emotion Visual System', t);
  const sheet = frame({ name: 'Emotion Styles', gap: t.space.xxl, pad: t.space.xxl, fill: t.color.surface, radius: t.radius.xl });
  section.appendChild(sheet);
  add(sheet, text(t, 'title', 'Emotion Visual System', { name: 'title' }));
  const sizes = [t.size.emotionMd, t.size.emotionSm, t.icon.sm];
  for (const style of Object.values(EMOTION_STYLES)) {
    const block = add(sheet, frame({ name: style.label, gap: t.space.sm }));
    add(block, text(t, 'heading', style.label, { name: 'style' }));
    const row = add(block, frame({ name: 'emotions', dir: 'H', gap: t.space.xl }));
    for (const e of EMOTIONS) {
      const col = add(row, frame({ name: e, gap: t.space.xs, align: 'CENTER' }));
      const big = figma.createNodeFromSvg(wrapSvg(style.draw(e, t.color.emotion[e]), t.size.emotionMd * 2));
      big.name = `${e}/56`;
      add(col, big);
      const small = add(col, frame({ name: 'sizes', dir: 'H', gap: t.space.xs, align: 'CENTER' }));
      for (const px of sizes) {
        const n = figma.createNodeFromSvg(wrapSvg(style.draw(e, t.color.emotion[e]), px));
        n.name = `${e}/${px}`;
        add(small, n);
      }
      const dot = figma.createEllipse();
      dot.resize(t.size.emotionXs, t.size.emotionXs);
      dot.fills = [solid(t.color.emotion[e].fill)];
      dot.name = `${e}/dot`;
      add(small, dot);
      add(col, text(t, 'caption', EMOTION_LABEL[e], { name: 'label', color: t.color.textSecondary }));
    }
  }
  layoutInSection(section, [sheet]);
  await figma.setCurrentPageAsync(page);
  figma.viewport.scrollAndZoomIntoView([section]);
  status('감정 스타일 비교 시트를 만들었어요.', 'done');
}

/**
 * v3 comparison Sections (Emotion marker A/B · Motion concept · Related copy). Existing screens are untouched.
 * The v2 boards (emotion styles / related variants / settings picker) remain in comparisons/index.ts.
 */
async function comparisons(config: BuilderConfig) {
  status('폰트 불러오는 중…');
  await loadFonts();
  const mk = async (c: Partial<BuilderConfig>) => {
    const t = resolveTheme({ ...config, ...c });
    const lib = new Library(t, COMPONENTS);
    await lib.init(false);
    return { t, lib };
  };
  status('컴포넌트 준비 중…');
  const v3 = await mk({ refine: 'v3', markerVariant: 'same' });
  const heart = await mk({ refine: 'v3', markerVariant: 'same', markerShape: 'heart' });
  const current = v3;
  const page = await screensPage();
  const boards = [
    () => shapePickerComparison(config, v3),
    () => relatedCopyComparison(config, v3, v3),
    () => shapeAppliedExamples(config, heart, '하트'),
    () => motionBoard(v3.t, v3.t.emotionStyle),
  ];
  const sections: SectionNode[] = [];
  for (const make of boards) {
    const board = make();
    status(`${board.title} 생성 중…`);
    const section = newSection(page, board.title, current.t);
    for (const f of board.frames) section.appendChild(f);
    layoutInSection(section, board.frames);
    sections.push(section);
  }
  await figma.setCurrentPageAsync(page);
  page.selection = sections;
  figma.viewport.scrollAndZoomIntoView(sections);
  status('비교·예시 섹션 4개를 만들었어요. 기존 화면은 그대로예요.', 'done');
}
