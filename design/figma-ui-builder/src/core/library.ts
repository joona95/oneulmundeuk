// Component library: builds each reusable component once per theme signature and hands out instances.
// Components live on the "Echo Components" page, inside one Section per theme signature.
import type { Theme } from '../tokens/resolve';
import type { Emotion, EmotionSize } from '../emotions/types';
import type { IconName } from './icons';
import { solid } from './layout';

export type MainComponent = ComponentNode | ComponentSetNode;

export interface ComponentDef {
  /** Unique, human-readable component name (shown in Figma's Assets panel). */
  name: string;
  build(t: Theme, lib: Library): MainComponent;
}

export interface InstanceOpts {
  /** Variant properties for component sets, e.g. { state: 'selected' }. */
  variant?: Record<string, string>;
  /** Text overrides by text-layer name. */
  text?: Record<string, string>;
  /** Layer names to hide. */
  hide?: string[];
  /** Layer names (hidden by default in the main component) to show. */
  show?: string[];
  /** Nested instance swaps by layer name. */
  swap?: Record<string, ComponentNode>;
}

const PAGE_NAME = 'Echo Components';
const SECTION_GAP = 48;
const ROW_WIDTH = 1800;

export class Library {
  private cache = new Map<string, MainComponent>();
  private section!: SectionNode;
  private cursor = { x: SECTION_GAP, y: SECTION_GAP, rowH: 0 };

  constructor(
    readonly t: Theme,
    private defs: Record<string, ComponentDef>,
  ) {}

  /** Finds or creates the library section for this theme. `rebuild` discards a cached section. */
  async init(rebuild: boolean): Promise<void> {
    let page = figma.root.children.find((p) => p.name === PAGE_NAME);
    if (!page) {
      page = figma.createPage();
      page.name = PAGE_NAME;
    }
    await page.loadAsync();
    const name = `Library · ${this.t.signature}`;
    const existing = page.children.find((n): n is SectionNode => n.type === 'SECTION' && n.name === name);
    if (existing && rebuild) existing.remove();
    if (existing && !rebuild) {
      this.section = existing;
      for (const child of existing.children) {
        const key = child.getPluginData('echoName');
        if (key && (child.type === 'COMPONENT' || child.type === 'COMPONENT_SET')) this.cache.set(key, child);
      }
      this.cursor.y = existing.height; // new components go below the existing ones
      return;
    }
    const section = figma.createSection();
    section.name = name;
    // Stack sections vertically on the page.
    const bottom = page.children.reduce((m, n) => Math.max(m, n.y + n.height), 0);
    section.x = 0;
    section.y = bottom + (page.children.length ? 200 : 0);
    section.fills = [solid(this.t.color.background)];
    page.appendChild(section);
    this.section = section;
  }

  /** Returns the main component (or set) for a registered definition, building it on first use. */
  get(name: string): MainComponent {
    const hit = this.cache.get(name);
    if (hit && !hit.removed) return hit;
    const def = this.defs[name];
    if (!def) throw new Error(`Unknown component "${name}". Register it in components/index.ts.`);
    const node = def.build(this.t, this);
    node.name = name;
    node.setPluginData('echoName', name);
    this.place(node);
    this.cache.set(name, node);
    return node;
  }

  /** Parent for component sets (combineAsVariants needs one). */
  get container(): SectionNode {
    return this.section;
  }

  private place(node: MainComponent) {
    if (node.parent !== this.section) this.section.appendChild(node);
    if (this.cursor.x + node.width > ROW_WIDTH && this.cursor.x > SECTION_GAP) {
      this.cursor.x = SECTION_GAP;
      this.cursor.y += this.cursor.rowH + SECTION_GAP;
      this.cursor.rowH = 0;
    }
    node.x = this.cursor.x;
    node.y = this.cursor.y;
    this.cursor.x += node.width + SECTION_GAP;
    this.cursor.rowH = Math.max(this.cursor.rowH, node.height);
    const w = Math.max(this.section.width, node.x + node.width + SECTION_GAP);
    const h = Math.max(this.section.height, node.y + node.height + SECTION_GAP);
    this.section.resizeWithoutConstraints(w, h);
  }

  /** Main component, or the matching variant inside a set. */
  component(name: string, variant?: Record<string, string>): ComponentNode {
    const main = this.get(name);
    if (main.type === 'COMPONENT') return main;
    const want = variant ?? {};
    const match = main.children.find((c) => {
      const props = (c as ComponentNode).variantProperties ?? {};
      return Object.keys(want).every((k) => props[k] === want[k]);
    });
    return (match ?? main.defaultVariant) as ComponentNode;
  }

  instance(name: string, o: InstanceOpts = {}): InstanceNode {
    const inst = this.component(name, o.variant).createInstance();
    if (o.swap) for (const [layer, comp] of Object.entries(o.swap)) swapNested(inst, layer, comp);
    if (o.text) for (const [layer, value] of Object.entries(o.text)) setText(inst, layer, value);
    if (o.hide) for (const layer of o.hide) hideLayer(inst, layer);
    if (o.show) for (const layer of o.show) showLayer(inst, layer);
    return inst;
  }

  // Convenience accessors used by many components/screens.
  icon(name: IconName): ComponentNode {
    return this.component('Icon', { name });
  }
  emotion(e: Emotion, size: EmotionSize = 'sm'): ComponentNode {
    return this.component('Emotion', { emotion: e, size });
  }
}

export function setText(root: InstanceNode | FrameNode, layer: string, value: string) {
  const node = root.findOne((n) => n.type === 'TEXT' && n.name === layer) as TextNode | null;
  if (!node) throw new Error(`Text layer "${layer}" not found in ${root.name}`);
  node.characters = value;
}

export function hideLayer(root: InstanceNode | FrameNode, layer: string) {
  const node = root.findOne((n) => n.name === layer);
  if (!node) throw new Error(`Layer "${layer}" not found in ${root.name}`);
  node.visible = false;
}

export function showLayer(root: InstanceNode | FrameNode, layer: string) {
  const node = root.findOne((n) => n.name === layer);
  if (!node) throw new Error(`Layer "${layer}" not found in ${root.name}`);
  node.visible = true;
}

export function swapNested(root: InstanceNode, layer: string, comp: ComponentNode) {
  const node = root.findOne((n) => n.type === 'INSTANCE' && n.name === layer) as InstanceNode | null;
  if (!node) throw new Error(`Nested instance "${layer}" not found in ${root.name}`);
  node.swapComponent(comp);
  node.name = layer; // keep the layer name stable so later overrides can still find it
}

/** Recolors every vector inside an icon instance (strokes and solid fills). */
export function tint(node: SceneNode, hex: string) {
  const paint = [solid(hex)];
  const visit = (n: SceneNode) => {
    if (n.type === 'VECTOR' || n.type === 'ELLIPSE' || n.type === 'RECTANGLE' || n.type === 'BOOLEAN_OPERATION') {
      if (Array.isArray(n.strokes) && n.strokes.length) n.strokes = paint;
      if (Array.isArray(n.fills) && n.fills.length) n.fills = paint;
    }
    if ('children' in n) n.children.forEach(visit);
  };
  visit(node);
}
