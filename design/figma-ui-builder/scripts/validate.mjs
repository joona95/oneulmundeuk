// Offline validation: runs dist/code.js against the Figma API mock for many Builder configurations.
// - fails on any thrown error or error status
// - checks every screen is 360×800, uses component instances, and has no empty text
// - writes HTML previews to preview/ for visual QA (`npm run preview` screenshots them if Playwright exists)
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import vm from 'node:vm';
import { createFigma, warnings } from './figma-mock.mjs';
import { page } from './render-html.mjs';

const code = await readFile(new URL('../dist/code.js', import.meta.url), 'utf8');
const html = await readFile(new URL('../dist/ui.html', import.meta.url), 'utf8');
if (!html.includes('Generate Selected Screen') || !html.includes('<script>')) throw new Error('ui.html missing script or controls');
const manifest = JSON.parse(await readFile(new URL('../manifest.json', import.meta.url), 'utf8'));
for (const k of ['name', 'id', 'api', 'main', 'ui', 'editorType']) if (!manifest[k]) throw new Error(`manifest.${k} missing`);

const BASE = { screen: 'home', variant: 'soft', accent: 'sage', background: 'ivory', cardStyle: 'border', density: 'comfortable', emotionStyle: 'blob', homeVariant: 'B', refine: 'v1', relatedVariant: 'current', markerVariant: 'same', markerShape: 'jelly', shapePicker: 'grid' };

async function boot() {
  const env = createFigma();
  const ctx = vm.createContext({ figma: env.figma, __html__: html, console, setTimeout, structuredClone });
  vm.runInContext(code, ctx);
  await new Promise((r) => setTimeout(r, 0));
  const init = env.posted.find((m) => m.type === 'init');
  if (!init) throw new Error('plugin did not post init');
  return { ...env, init };
}

async function send(env, msg) {
  const before = env.posted.length;
  await env.figma.ui.onmessage(msg);
  const out = env.posted.slice(before).filter((m) => m.type === 'status' && m.level !== 'info');
  const last = out[out.length - 1];
  if (!last) throw new Error(`${msg.type}: no final status`);
  if (last.level === 'error') throw new Error(`${msg.type}: ${last.message}`);
  return last.message;
}

function screensPage(env) {
  return env.figma.root.children.find((p) => p.name === 'Echo Screens');
}

function checkScreen(f) {
  const problems = [];
  if (f.width !== 360 || f.height !== 800) problems.push(`size ${f.width}×${f.height}`);
  const instances = f.findAll((n) => n.type === 'INSTANCE');
  if (instances.length < 3) problems.push(`only ${instances.length} instances`);
  const empty = f.findAll((n) => n.type === 'TEXT' && n.visible !== false && !n.characters && n.name !== 'title');
  if (empty.length) problems.push(`empty text: ${empty.map((e) => e.name).join(',')}`);
  if (!f.findOne((n) => n.name === 'Status Bar' || n.mainComponent?.name === 'Status Bar') && !instances.some((i) => i.mainComponent?.parent?.name === 'Status Bar' || i.mainComponent?.name === 'Status Bar')) problems.push('no status bar');
  return problems;
}

let failures = 0;
const ok = (m) => console.log(`  ✓ ${m}`);
const fail = (m) => { failures++; console.log(`  ✗ ${m}`); };

await mkdir(new URL('../preview/', import.meta.url), { recursive: true });

// 1) Default config: all screens + previews
{
  console.log('Default (Soft · Sage · Ivory · Border · Blob · Home B)');
  const env = await boot();
  ok(`init with ${env.init.screens.length} screens`);
  const d = env.init.config;
  d.refine === 'v3' && d.markerVariant === 'same' && d.markerShape === 'jelly' && d.relatedVariant === 'threadB' ? ok('DEFAULT_CONFIG = v3 · Same Shape (동글동글) · Thread B') : fail(`DEFAULT_CONFIG is ${d.refine}/${d.markerVariant}/${d.markerShape}/${d.relatedVariant}`);
  ok(await send(env, { type: 'generate', config: BASE, all: true }));
  const sec = screensPage(env).children.at(-1);
  for (const f of sec.children) {
    const p = checkScreen(f);
    p.length ? fail(`${f.name}: ${p.join('; ')}`) : ok(`${f.name}`);
  }
  await writeFile(new URL('../preview/default.html', import.meta.url), page('default', sec.children));
  ok(await send(env, { type: 'generate-home-variants', config: BASE }));
  await writeFile(new URL('../preview/home-variants.html', import.meta.url), page('home', screensPage(env).children.at(-1).children));
  ok(await send(env, { type: 'generate-emotion-sheet', config: BASE }));
  await writeFile(new URL('../preview/emotions.html', import.meta.url), page('emotions', screensPage(env).children.at(-1).children, '#FFFFFF'));
  ok(await send(env, { type: 'rebuild-library', config: BASE }));
  const lib = env.figma.root.children.find((p) => p.name === 'Echo Components');
  const sections = lib.children.filter((n) => n.type === 'SECTION');
  sections.length === 1 ? ok('library rebuilt in place (1 section)') : fail(`expected 1 library section, got ${sections.length}`);
  await writeFile(new URL('../preview/components.html', import.meta.url), page('components', sections[0].children, '#FFFFFF'));
  // cache reuse: a second generate must not create a second library section
  await send(env, { type: 'generate', config: { ...BASE, screen: 'explore' }, all: false });
  lib.children.filter((n) => n.type === 'SECTION').length === 1 ? ok('library reused across runs') : fail('library duplicated');
}

// 1b) Refinement v2: all screens, every Related variant, and the comparison boards
{
  console.log('Refinement v2 (+ comparisons)');
  const env = await boot();
  const V2 = { ...BASE, refine: 'v2' };
  ok(await send(env, { type: 'generate', config: V2, all: true }));
  const sec = screensPage(env).children.at(-1);
  for (const f of sec.children) { const p = checkScreen(f); p.length ? fail(`${f.name}: ${p.join('; ')}`) : ok(f.name); }
  const userFacing = sec.findAll((n) => n.type === 'TEXT' && n.visible !== false).map((n) => n.characters).join('\n');
  for (const banned of ['Echo', 'Abstract Blob', 'Small Creature', 'Geometric Symbol', '#성장', '기존 메모 가져오기']) {
    userFacing.includes(banned) ? fail(`v2 screens show "${banned}"`) : ok(`v2 screens never show "${banned}"`);
  }
  await writeFile(new URL('../preview/v2.html', import.meta.url), page('v2', sec.children));
  for (const rv of ['threadA', 'threadB']) {
    ok(await send(env, { type: 'generate', config: { ...V2, screen: 'relatedMemories', relatedVariant: rv }, all: false }));
  }
}

// 1c) v3: both marker variants, all screens, and the v3 comparison boards
for (const mv of ['same', 'mixed']) {
  // markerShape stays at the default (jelly); every shape is exercised in the shape loop below.
  console.log(`v3 · marker ${mv}`);
  const env = await boot();
  const V3 = { ...BASE, refine: 'v3', relatedVariant: 'threadB', markerVariant: mv };
  ok(await send(env, { type: 'generate', config: V3, all: true }));
  const sec = screensPage(env).children.at(-1);
  for (const f of sec.children) { const p = checkScreen(f); p.length ? fail(`${f.name}: ${p.join('; ')}`) : ok(f.name); }
  // UI copy only — the user's own sample records (body/quote) may contain any words.
  const userFacing = sec.findAll((n) => n.type === 'TEXT' && n.visible !== false && !['body', 'quote', 'text'].includes(n.name)).map((n) => n.characters).join('\n');
  for (const banned of ['Echo', '몽글몽글', '감정 표현', '비슷한', '#성장', '기존 메모 가져오기', '%']) {
    userFacing.includes(banned) ? fail(`v3 screens show "${banned}"`) : ok(`v3 screens never show "${banned}"`);
  }
  await writeFile(new URL(`../preview/v3-${mv}.html`, import.meta.url), page(`v3 ${mv}`, sec.children));
  if (mv === 'same') {
    ok(await send(env, { type: 'generate-comparisons', config: V3 }));
    const p = screensPage(env);
    const boards = p.children.slice(-4);
    const names = boards.map((b) => b.name);
    names.length === 4 && names.every((n) => /^(Comparison|Example) ·/.test(n)) ? ok(`sections: ${names.join(' | ')}`) : fail(`sections: ${names}`);
    for (const [i, b] of boards.entries()) await writeFile(new URL(`../preview/v3-compare-${['shape-picker', 'related', 'shape-applied', 'motion'][i]}.html`, import.meta.url), page(b.name, b.children, '#FFFFFF'));
  }
  const lib = env.figma.root.children.find((x) => x.name === 'Echo Components');
  ok(`library sections: ${lib.children.filter((n) => n.type === 'SECTION').map((n) => n.name.split('·').slice(-1)[0]).join(', ')}`);
}

// 1c') every marker shape × both picker layouts builds cleanly
for (const shape of ['jelly', 'heart', 'star', 'roundSquare', 'pebble', 'diamond']) {
  const env = await boot();
  try {
    await send(env, { type: 'generate', config: { ...env.init.config, markerShape: shape, shapePicker: shape === 'heart' ? 'list' : 'grid' }, all: true });
    ok(`shape ${shape}: all screens`);
  } catch (e) { fail(`shape ${shape}: ${e.message}`); }
}

// 1d) Final set — exactly what "Generate All Screens" produces with DEFAULT_CONFIG (Design Freeze)
const FINAL_SCREENS = ['Home', 'Record Editor', 'Related Memories', 'Records — List', 'Records — Calendar', 'Explore', 'Semantic Search Results', 'Record Detail', 'Reminder Notification', 'Settings'];
const emotionArtPaths = (frameNode) => {
  // every Emotion marker instance → (rendered size, first path outline)
  const out = [];
  for (const inst of frameNode.findAll((n) => n.type === 'INSTANCE' && n.mainComponent?.parent?.name === 'Emotion')) {
    const art = inst.findOne((n) => n._svg);
    const d = /<path d="([^"]+)"/.exec(art?._svg ?? '')?.[1];
    if (d) out.push({ size: art.width, d });
  }
  return out;
};
const shapeSignature = (frames) => {
  const bySize = new Map();
  for (const f of frames) for (const { size, d } of emotionArtPaths(f)) bySize.set(size, new Set([...(bySize.get(size) ?? []), d]));
  return bySize;
};
{
  console.log('Final set (DEFAULT_CONFIG · Design Freeze)');
  const env = await boot();
  const d = env.init.config;
  const expect = { refine: 'v3', variant: 'soft', background: 'ivory', accent: 'sage', markerVariant: 'same', markerShape: 'jelly', shapePicker: 'grid', relatedVariant: 'threadB', relatedSubtitle: 'none' };
  const bad = Object.entries(expect).filter(([k, v]) => d[k] !== v);
  bad.length ? fail(`DEFAULT_CONFIG mismatch: ${bad.map(([k]) => `${k}=${d[k]}`).join(', ')}`) : ok('DEFAULT_CONFIG matches the Design Freeze');
  ok(await send(env, { type: 'generate', config: { ...d }, all: true }));
  const sec = screensPage(env).children.at(-1);
  const names = sec.children.map((f) => f.name);
  FINAL_SCREENS.every((n, i) => names[i]?.startsWith(n)) && names.length === 10 ? ok(`10 final screens: ${names.join(' | ')}`) : fail(`screens: ${names.join(' | ')}`);
  for (const f of sec.children) { const p = checkScreen(f); if (p.length) fail(`${f.name}: ${p.join('; ')}`); }
  const allText = sec.findAll((n) => n.type === 'TEXT' && n.visible !== false).map((n) => n.characters).join('\n');
  allText.includes('Echo') ? fail('user-facing UI contains "Echo"') : ok('no "Echo" in user-facing UI');
  const related = sec.children.find((f) => f.name.startsWith('Related Memories'));
  const relText = related.findAll((n) => n.type === 'TEXT' && n.visible !== false).map((n) => n.characters);
  relText.includes('문득,\n예전의 생각이 떠올랐어요') ? ok('Related title "문득, 예전의 생각이 떠올랐어요"') : fail('Related title missing');
  related.findOne((n) => n.name === 'sub') ? fail('Related still has a subtitle') : ok('Related has no subtitle');
  const comparisonsMade = screensPage(env).children.some((n) => /^(Comparison|Example) ·/.test(n.name));
  comparisonsMade ? fail('Generate All produced comparison frames') : ok('Generate All produced only final screens');
  await writeFile(new URL('../preview/final.html', import.meta.url), page('final', sec.children));
}

// 1e) Shape changes propagate: Editor / List / Related / Rediscovery (Home) use one shape; Calendar uses dots only
{
  console.log('Shape propagation');
  const sigs = {};
  for (const shape of ['jelly', 'heart']) {
    const env = await boot();
    await send(env, { type: 'generate', config: { ...env.init.config, markerShape: shape }, all: true });
    const sec = screensPage(env).children.at(-1);
    const pick = (prefix) => sec.children.find((f) => f.name.startsWith(prefix));
    const targets = ['Record Editor', 'Records — List', 'Related Memories', 'Home'].map(pick);
    const sig = shapeSignature(targets);
    const oneShape = [...sig.values()].every((set) => set.size === 1);
    oneShape ? ok(`${shape}: every emotion uses the same shape in Editor/List/Related/Rediscovery (sizes ${[...sig.keys()].join('/')}px)`) : fail(`${shape}: more than one shape per size: ${[...sig.entries()].map(([k, v]) => `${k}px×${v.size}`).join(', ')}`);
    sigs[shape] = [...sig.values()].map((set) => [...set][0]).join('|');
    const cal = pick('Records — Calendar').findOne((n) => n.name === 'grid');
    const calMarkers = cal.findAll((n) => n.type === 'INSTANCE' && n.mainComponent?.parent?.name === 'Emotion').length;
    const dots = cal.findAll((n) => n.type === 'ELLIPSE').length;
    calMarkers === 0 && dots > 0 ? ok(`${shape}: Calendar shows ${dots} color dots, no shape markers`) : fail(`${shape}: calendar markers=${calMarkers} dots=${dots}`);
  }
  sigs.jelly !== sigs.heart ? ok('changing 내 감정 조각 changes the drawn shape') : fail('jelly and heart produced identical shapes');
}

// 2) Option matrix: every value of every option is exercised at least once
const MATRIX = [
  { variant: 'minimal', accent: 'coral', background: 'white', cardStyle: 'flat', density: 'compact', emotionStyle: 'geometric', homeVariant: 'A' },
  { variant: 'playful', accent: 'lavender', background: 'coolGray', cardStyle: 'elevation', density: 'comfortable', emotionStyle: 'doodle', homeVariant: 'C' },
  { variant: 'soft', accent: 'sky', background: 'ivory', cardStyle: 'border', density: 'compact', emotionStyle: 'creature', homeVariant: 'B', refine: 'v2', relatedVariant: 'threadB' },
  { variant: 'playful', accent: 'sage', background: 'white', cardStyle: 'flat', density: 'comfortable', emotionStyle: 'doodle', homeVariant: 'A', refine: 'v2', relatedVariant: 'threadA' },
];
for (const [i, m] of MATRIX.entries()) {
  const config = { ...BASE, ...m };
  console.log(`Matrix ${i + 1}: ${Object.values(m).join(' · ')}`);
  const env = await boot();
  try {
    ok(await send(env, { type: 'generate', config, all: true }));
    const sec = screensPage(env).children.at(-1);
    for (const f of sec.children) { const p = checkScreen(f); if (p.length) fail(`${f.name}: ${p.join('; ')}`); }
    await writeFile(new URL(`../preview/matrix-${i + 1}.html`, import.meta.url), page(`matrix ${i + 1}`, sec.children));
  } catch (e) {
    fail(e.message);
  }
}

// 3) Font fallback: Pretendard missing, only Inter available
{
  console.log('Font fallback (Inter only)');
  const env = createFigma({ fonts: [['Inter', ['Regular', 'Medium', 'Semi Bold', 'Bold']]] });
  const ctx = vm.createContext({ figma: env.figma, __html__: html, console, setTimeout, structuredClone });
  vm.runInContext(code, ctx);
  await new Promise((r) => setTimeout(r, 0));
  try { ok(await send(env, { type: 'generate', config: BASE, all: false })); } catch (e) { fail(e.message); }
}

const uniq = [...new Set(warnings)];
if (uniq.length) {
  console.log(`\nLayout warnings (${uniq.length}):`);
  for (const w of uniq.slice(0, 30)) console.log(`  ! ${w}`);
}
console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exit(failures ? 1 : 0);
