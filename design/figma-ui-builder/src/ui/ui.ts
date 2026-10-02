// Builder panel (iframe). Keeps a BuilderConfig and sends it to the plugin sandbox.
import type { BuilderConfig, PluginToUi, UiToPlugin } from '../types';

type PillKey = Exclude<keyof BuilderConfig, 'screen' | 'relatedSubtitle'>;

/** Options per config key. Swatch colors are display-only previews (tokens live in src/tokens). */
const OPTIONS: Record<PillKey, { value: string; label: string; swatch?: string }[]> = {
  variant: [
    { value: 'minimal', label: 'Minimal' },
    { value: 'soft', label: 'Soft' },
    { value: 'playful', label: 'Slightly Playful' },
  ],
  accent: [
    { value: 'coral', label: 'Coral', swatch: '#D9705A' },
    { value: 'sage', label: 'Sage', swatch: '#5F8F76' },
    { value: 'sky', label: 'Sky', swatch: '#4F86BA' },
    { value: 'lavender', label: 'Lavender', swatch: '#7F78BC' },
  ],
  background: [
    { value: 'white', label: 'White', swatch: '#FFFFFF' },
    { value: 'ivory', label: 'Warm Ivory', swatch: '#FAF7F2' },
    { value: 'coolGray', label: 'Cool Gray', swatch: '#F4F5F7' },
  ],
  cardStyle: [
    { value: 'flat', label: 'Flat' },
    { value: 'border', label: 'Subtle Border' },
    { value: 'elevation', label: 'Soft Elevation' },
  ],
  density: [
    { value: 'comfortable', label: 'Comfortable' },
    { value: 'compact', label: 'Compact' },
  ],
  emotionStyle: [
    { value: 'blob', label: 'Abstract Blob · 몽글몽글' },
    { value: 'creature', label: 'Small Creature · 꼬물꼬물' },
    { value: 'doodle', label: 'Doodle · 끄적끄적' },
    { value: 'geometric', label: 'Geometric · 반듯반듯' },
  ],
  homeVariant: [
    { value: 'A', label: 'A · 기록 입력 중심' },
    { value: 'B', label: 'B · 균형' },
    { value: 'C', label: 'C · Rediscovery 중심' },
  ],
  refine: [
    { value: 'v1', label: 'v1 · 현재' },
    { value: 'v2', label: 'v2' },
    { value: 'v3', label: 'v3 · 감정 마커' },
  ],
  markerVariant: [
    { value: 'same', label: 'Same Shape (채택)' },
    { value: 'mixed', label: 'Mixed (reference)' },
  ],
  markerShape: [
    { value: 'jelly', label: '동글동글' },
    { value: 'heart', label: '하트' },
    { value: 'star', label: '별' },
    { value: 'roundSquare', label: '네모' },
    { value: 'pebble', label: '조약돌' },
    { value: 'diamond', label: '마름모' },
  ],
  shapePicker: [
    { value: 'grid', label: '2열 grid' },
    { value: 'list', label: 'compact card (reference)' },
  ],
  relatedVariant: [
    { value: 'current', label: '현재' },
    { value: 'threadA', label: 'Thread A' },
    { value: 'threadB', label: 'Thread B' },
  ],
};

let config: BuilderConfig | null = null;
const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const send = (m: UiToPlugin) => parent.postMessage({ pluginMessage: m }, '*');

function renderPills() {
  const c = config;
  if (!c) return;
  document.querySelectorAll<HTMLDivElement>('.pills').forEach((box) => {
    const key = box.dataset.key as PillKey;
    box.innerHTML = '';
    for (const o of OPTIONS[key]) {
      const b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('aria-pressed', String(c[key] === o.value));
      if (o.swatch) {
        const s = document.createElement('span');
        s.className = 'swatch';
        s.style.background = o.swatch;
        b.appendChild(s);
      }
      b.appendChild(document.createTextNode(o.label));
      b.onclick = () => {
        if (!config) return;
        (config as unknown as Record<string, string>)[key] = o.value;
        renderPills();
        send({ type: 'save-config', config });
      };
      box.appendChild(b);
    }
  });
}

function setBusy(busy: boolean) {
  document.querySelectorAll<HTMLButtonElement>('footer button').forEach((b) => (b.disabled = busy));
}

function run(m: UiToPlugin) {
  setBusy(true);
  send(m);
}

window.onmessage = (ev: MessageEvent) => {
  const msg = ev.data.pluginMessage as PluginToUi | undefined;
  if (!msg) return;
  if (msg.type === 'init') {
    config = msg.config;
    const sel = $<HTMLSelectElement>('screen');
    sel.innerHTML = '<option value="all">All Screens</option>';
    for (const s of msg.screens) {
      const o = document.createElement('option');
      o.value = s.key;
      o.textContent = s.label;
      sel.appendChild(o);
    }
    sel.value = config.screen;
    sel.onchange = () => {
      if (!config) return;
      config.screen = sel.value as BuilderConfig['screen'];
      send({ type: 'save-config', config });
    };
    renderPills();
  } else if (msg.type === 'status') {
    const el = $('status');
    el.textContent = msg.message;
    el.className = msg.level;
    if (msg.level !== 'info') setBusy(false);
  }
};

$('gen-one').onclick = () => config && run({ type: 'generate', config, all: config.screen === 'all' });
$('gen-all').onclick = () => config && run({ type: 'generate', config, all: true });
$('gen-home').onclick = () => config && run({ type: 'generate-home-variants', config });
$('gen-compare').onclick = () => config && run({ type: 'generate-comparisons', config });
$('gen-emotion').onclick = () => config && run({ type: 'generate-emotion-sheet', config });
$('rebuild').onclick = () => config && run({ type: 'rebuild-library', config });
