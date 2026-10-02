// Minimal Figma Plugin API mock for offline validation (node). Not a full emulator:
// it implements what Echo UI Builder uses and enforces the rules that most often break plugins
// in real Figma (FILL/HUG legality, font loading, instance immutability, variant sets).

let idc = 0;
const CONTAINERS = new Set(['DOCUMENT', 'PAGE', 'FRAME', 'COMPONENT', 'COMPONENT_SET', 'INSTANCE', 'SECTION', 'GROUP']);
export const warnings = [];
export const loadedFonts = new Set();
const fontKey = (f) => `${f.family}/${f.style}`;

function insideInstance(n) {
  for (let p = n; p; p = p.parent) if (p.type === 'INSTANCE') return true;
  return false;
}

export class MockNode {
  constructor(type) {
    this.type = type;
    this.id = `${++idc}:1`;
    this.name = type;
    this.parent = null;
    if (CONTAINERS.has(type)) this.children = [];
    this._pd = {};
    this.visible = true;
    this.removed = false;
    this.x = 0; this.y = 0; this.width = 100; this.height = 100;
    this.fills = []; this.strokes = []; this.effects = [];
    this.opacity = 1;
    this.layoutMode = 'NONE';
    this.primaryAxisSizingMode = 'AUTO';
    this.counterAxisSizingMode = 'AUTO';
    this._lsh = undefined; this._lsv = undefined;
    this._lp = 'AUTO';
  }
  get isAuto() { return this.layoutMode && this.layoutMode !== 'NONE'; }
  setPluginData(k, v) { this._pd[k] = v; }
  getPluginData(k) { return this._pd[k] ?? ''; }
  resize(w, h) {
    if (!(w >= 0.01 && h >= 0.01)) throw new Error(`resize(${w}, ${h}) invalid on ${this.name}`);
    const sx = w / this.width, sy = h / this.height;
    this.width = w; this.height = h;
    // Children with SCALE constraints follow the parent (non-auto-layout frames only).
    if (!this.isAuto) for (const c of this.children ?? []) {
      if (c.constraints?.horizontal === 'SCALE') { c.x *= sx; c.width *= sx; }
      if (c.constraints?.vertical === 'SCALE') { c.y *= sy; c.height *= sy; }
    }
  }
  resizeWithoutConstraints(w, h) { this.resize(w, h); }
  rescale(s) { this.width *= s; this.height *= s; }
  appendChild(c) { this.insertChild(this.children ? this.children.length : 0, c); }
  insertChild(i, c) {
    if (!this.children) throw new Error(`${this.type} cannot have children`);
    if (insideInstance(this)) throw new Error(`Cannot add children inside an instance (${this.name})`);
    if (c.parent) {
      if (insideInstance(c.parent)) throw new Error('Cannot move a node out of an instance');
      c.parent.children.splice(c.parent.children.indexOf(c), 1);
    }
    this.children.splice(i, 0, c);
    c.parent = this;
  }
  remove() {
    if (this.parent && insideInstance(this.parent)) throw new Error('Cannot remove a node inside an instance');
    if (this.parent) this.parent.children.splice(this.parent.children.indexOf(this), 1);
    this.parent = null;
    this.removed = true;
  }
  findAll(fn = () => true) {
    const out = [];
    const walk = (n) => { for (const c of n.children ?? []) { if (fn(c)) out.push(c); walk(c); } };
    walk(this);
    return out;
  }
  findOne(fn) { return this.findAll(fn)[0] ?? null; }

  // ── auto-layout sizing rules ──
  _axisMode(axis) {
    const horizontal = this.layoutMode === 'HORIZONTAL';
    const primary = (axis === 'H') === horizontal;
    return (primary ? this.primaryAxisSizingMode : this.counterAxisSizingMode) === 'AUTO' ? 'HUG' : 'FIXED';
  }
  _sizing(axis) {
    const explicit = axis === 'H' ? this._lsh : this._lsv;
    if (explicit) return explicit;
    if (this.type === 'TEXT') {
      if (this.textAutoResize === 'WIDTH_AND_HEIGHT') return 'HUG';
      if (this.textAutoResize === 'HEIGHT') return axis === 'H' ? 'FIXED' : 'HUG';
      return 'FIXED';
    }
    if (this.isAuto) return this._axisMode(axis);
    return 'FIXED';
  }
  _setSizing(axis, v) {
    if (v === 'FILL') {
      if (!this.parent || !this.parent.isAuto) throw new Error(`FILL on "${this.name}" requires an auto-layout parent (parent: ${this.parent?.name ?? 'none'})`);
      // Cross-axis FILL in a hug parent is fine (stretches to the tallest sibling);
      // primary-axis FILL in a hug parent silently turns the parent fixed in Figma.
      const primary = (axis === 'H') === (this.parent.layoutMode === 'HORIZONTAL');
      if (primary && this.parent._sizing(axis) === 'HUG') warnings.push(`FILL child "${this.name}" inside HUG parent "${this.parent.name}" on primary axis ${axis}`);
    }
    if (v === 'HUG' && !(this.isAuto || this.type === 'TEXT')) throw new Error(`HUG on "${this.name}" requires auto-layout or text`);
    if (axis === 'H') this._lsh = v; else this._lsv = v;
  }
  get layoutSizingHorizontal() { return this._sizing('H'); }
  set layoutSizingHorizontal(v) { this._setSizing('H', v); }
  get layoutSizingVertical() { return this._sizing('V'); }
  set layoutSizingVertical(v) { this._setSizing('V', v); }
  get layoutPositioning() { return this._lp; }
  set layoutPositioning(v) {
    if (v === 'ABSOLUTE' && (!this.parent || !this.parent.isAuto)) throw new Error(`ABSOLUTE on "${this.name}" requires an auto-layout parent`);
    this._lp = v;
  }
  get layoutWrap() { return this._wrap ?? 'NO_WRAP'; }
  set layoutWrap(v) {
    if (v === 'WRAP' && this.layoutMode !== 'HORIZONTAL') throw new Error(`layoutWrap WRAP requires HORIZONTAL layout (${this.name})`);
    this._wrap = v;
  }

  // ── text ──
  get fontName() { return this._font ?? { family: 'Inter', style: 'Regular' }; }
  set fontName(f) {
    if (!loadedFonts.has(fontKey(f))) throw new Error(`Font ${fontKey(f)} not loaded`);
    this._font = f;
  }
  get characters() { return this._chars ?? ''; }
  set characters(s) {
    if (!loadedFonts.has(fontKey(this.fontName))) throw new Error(`Set characters on "${this.name}" without loaded font ${fontKey(this.fontName)}`);
    if (typeof s !== 'string') throw new Error('characters must be a string');
    this._chars = s;
    this._ranges = [];
  }
  setRangeFills(a, b, fills) { this._checkRange(a, b); (this._ranges ??= []).push({ a, b, fills }); }
  setRangeFontName(a, b, f) {
    this._checkRange(a, b);
    if (!loadedFonts.has(fontKey(f))) throw new Error(`Range font ${fontKey(f)} not loaded`);
    (this._ranges ??= []).push({ a, b, font: f });
  }
  _checkRange(a, b) { if (a < 0 || b > this.characters.length || a >= b) throw new Error(`Bad range ${a}-${b}`); }

  // ── components ──
  get variantProperties() {
    if (this.type !== 'COMPONENT' || this.parent?.type !== 'COMPONENT_SET') return null;
    return Object.fromEntries(this.name.split(',').map((kv) => kv.trim().split('=')));
  }
  get defaultVariant() { return this.children[0]; }
  createInstance() {
    if (this.type !== 'COMPONENT') throw new Error('createInstance on non-component');
    const inst = cloneTree(this, 'INSTANCE');
    inst.mainComponent = this;
    return inst;
  }
  swapComponent(c) {
    if (this.type !== 'INSTANCE') throw new Error('swapComponent on non-instance');
    const fresh = cloneTree(c, 'INSTANCE');
    this.children = fresh.children;
    for (const ch of this.children) ch.parent = this;
    this.mainComponent = c;
    this.width = c.width; this.height = c.height;
  }
}

const SKIP = new Set(['id', 'parent', 'children', 'removed', 'mainComponent', '_pd']);
function cloneTree(src, rootType) {
  const n = new MockNode(rootType ?? src.type);
  for (const k of Object.keys(src)) if (!SKIP.has(k)) n[k] = structuredClone(src[k]);
  n.type = rootType ?? src.type;
  if (src.type === 'INSTANCE') n.mainComponent = src.mainComponent;
  if (rootType === 'INSTANCE' && src.type === 'COMPONENT') { n._lsh = undefined; n._lsv = undefined; n._lp = 'AUTO'; }
  if (src.children) {
    n.children = [];
    for (const c of src.children) {
      const cc = cloneTree(c);
      cc.parent = n;
      n.children.push(cc);
    }
  }
  return n;
}

export function createFigma({ fonts = [['Noto Sans KR', ['Regular', 'Medium', 'SemiBold', 'Bold']], ['Inter', ['Regular', 'Medium', 'Semi Bold', 'Bold']]] } = {}) {
  const root = new MockNode('DOCUMENT');
  const first = new MockNode('PAGE');
  first.name = 'Page 1';
  root.appendChild(first);
  const storage = new Map();
  const posted = [];
  const figma = {
    root,
    currentPage: first,
    get mixed() { return Symbol.for('mixed'); },
    ui: { postMessage: (m) => posted.push(m), onmessage: null },
    showUI() {},
    closePlugin() {},
    clientStorage: { getAsync: async (k) => storage.get(k), setAsync: async (k, v) => void storage.set(k, v) },
    viewport: { scrollAndZoomIntoView() {} },
    async listAvailableFontsAsync() {
      return fonts.flatMap(([family, styles]) => styles.map((style) => ({ fontName: { family, style } })));
    },
    async loadFontAsync(f) {
      const ok = fonts.some(([fam, styles]) => fam === f.family && styles.includes(f.style));
      if (!ok) throw new Error(`Font not available: ${fontKey(f)}`);
      loadedFonts.add(fontKey(f));
    },
    async setCurrentPageAsync(p) { figma.currentPage = p; },
    createPage() { const p = new MockNode('PAGE'); root.appendChild(p); p.loadAsync = async () => {}; p.selection = []; return p; },
    createFrame: () => new MockNode('FRAME'),
    createComponent: () => new MockNode('COMPONENT'),
    createSection: () => new MockNode('SECTION'),
    createText: () => { const t = new MockNode('TEXT'); t.textAutoResize = 'NONE'; return t; },
    createEllipse: () => new MockNode('ELLIPSE'),
    createRectangle: () => new MockNode('RECTANGLE'),
    createNodeFromSvg(svg) {
      if (!/^<svg[\s\S]*<\/svg>$/.test(svg)) throw new Error('Invalid SVG');
      if (/NaN|undefined|Infinity/.test(svg)) throw new Error(`SVG contains invalid numbers: ${svg.slice(0, 120)}`);
      const f = new MockNode('FRAME');
      const w = Number(/width="([\d.]+)"/.exec(svg)?.[1] ?? 24);
      f.resize(w, w);
      f._svg = svg;
      f.fills = [{ type: 'SOLID', color: { r: 1, g: 1, b: 1 } }];
      const v = new MockNode('VECTOR');
      v.strokes = /stroke="#/.test(svg) ? [{ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }] : [];
      v.fills = /fill="#/.test(svg) ? [{ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }] : [];
      v._original = true;
      f.appendChild(v);
      return f;
    },
    combineAsVariants(nodes, parent) {
      if (!nodes.length || nodes.some((n) => n.type !== 'COMPONENT')) throw new Error('combineAsVariants needs components');
      const names = new Set(nodes.map((n) => n.name));
      if (names.size !== nodes.length) throw new Error('Duplicate variant names');
      for (const n of nodes) if (!/^[^=,]+=[^=,]+(, [^=,]+=[^=,]+)*$/.test(n.name)) throw new Error(`Bad variant name "${n.name}"`);
      const set = new MockNode('COMPONENT_SET');
      parent.appendChild(set);
      for (const n of nodes) set.appendChild(n);
      return set;
    },
  };
  first.loadAsync = async () => {};
  first.selection = [];
  return { figma, posted, storage };
}
