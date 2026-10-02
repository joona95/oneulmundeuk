// Renders the mock Figma tree to HTML/CSS (auto layout → flexbox) for visual QA.
// Approximation only: real output is always the Figma layers.

const hex = (c, a = 1) => `rgba(${Math.round(c.r * 255)},${Math.round(c.g * 255)},${Math.round(c.b * 255)},${a})`;
const WEIGHT = { Regular: 400, Normal: 400, Book: 400, Medium: 500, SemiBold: 600, 'Semi Bold': 600, Semibold: 600, Bold: 700 };
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function paint(fills) {
  const f = (fills ?? []).find((p) => p.visible !== false);
  if (!f) return null;
  const solids = (fills ?? []).filter((p) => p.visible !== false && p.type === 'SOLID');
  if (solids.length > 1) {
    // Figma stacks paints bottom → top; CSS lists layers top → bottom.
    const [base, ...over] = solids;
    return `${over.reverse().map((p) => `linear-gradient(${hex(p.color, p.opacity ?? 1)}, ${hex(p.color, p.opacity ?? 1)})`).join(', ')}, ${hex(base.color, base.opacity ?? 1)}`;
  }
  if (f.type === 'SOLID') return hex(f.color, f.opacity ?? 1);
  if (f.type === 'GRADIENT_LINEAR') return `linear-gradient(90deg, ${f.gradientStops.map((s) => `${hex(s.color, s.color.a)} ${s.position * 100}%`).join(', ')})`;
  return null;
}

function sizingCss(n, parent) {
  const css = [];
  const H = n.layoutSizingHorizontal, V = n.layoutSizingVertical;
  const inAuto = parent && parent.isAuto && n.layoutPositioning !== 'ABSOLUTE';
  const row = parent?.layoutMode === 'HORIZONTAL';
  if (!inAuto) {
    if (H !== 'HUG') css.push(`width:${n.width}px`);
    if (V !== 'HUG') css.push(`height:${n.height}px`);
    return css;
  }
  css.push('flex-shrink:0');
  if (H === 'FILL') css.push(row ? 'flex:1 1 0;min-width:0' : 'align-self:stretch');
  else if (H === 'FIXED') css.push(`width:${n.width}px`);
  if (V === 'FILL') css.push(row ? 'align-self:stretch' : 'flex:1 1 0;min-height:0');
  else if (V === 'FIXED') css.push(`height:${n.height}px`);
  return css;
}

function boxCss(n) {
  const css = [];
  const bg = paint(n.fills);
  if (bg) css.push(`background:${bg}`);
  const r = n.cornerRadius;
  if (r) css.push(`border-radius:${Math.min(r, 999)}px`);
  if (n.topLeftRadius) css.push(`border-top-left-radius:${n.topLeftRadius}px;border-top-right-radius:${n.topRightRadius ?? 0}px`);
  const sc = paint(n.strokes);
  if (sc) {
    const w = n.strokeWeight ?? 1;
    const sides = ['Top', 'Right', 'Bottom', 'Left'].map((s) => n[`stroke${s}Weight`] ?? w);
    css.push(`box-shadow:${[
      sides[0] && `inset 0 ${sides[0]}px 0 ${sc}`,
      sides[2] && `inset 0 -${sides[2]}px 0 ${sc}`,
      sides[1] && `inset -${sides[1]}px 0 0 ${sc}`,
      sides[3] && `inset ${sides[3]}px 0 0 ${sc}`,
      ...(n.effects ?? []).filter((e) => e.type === 'DROP_SHADOW').map((e) => `${e.offset.x}px ${e.offset.y}px ${e.radius}px ${hex(e.color, e.color.a)}`),
    ].filter(Boolean).join(',')}`);
  } else {
    const sh = (n.effects ?? []).filter((e) => e.type === 'DROP_SHADOW');
    if (sh.length) css.push(`box-shadow:${sh.map((e) => `${e.offset.x}px ${e.offset.y}px ${e.radius}px ${hex(e.color, e.color.a)}`).join(',')}`);
  }
  if (n.opacity !== 1) css.push(`opacity:${n.opacity}`);
  if (n.clipsContent) css.push('overflow:hidden');
  return css;
}

function layoutCss(n) {
  if (!n.isAuto) return ['position:relative'];
  const J = { MIN: 'flex-start', CENTER: 'center', MAX: 'flex-end', SPACE_BETWEEN: 'space-between' };
  const A = { MIN: 'flex-start', CENTER: 'center', MAX: 'flex-end', BASELINE: 'baseline' };
  const css = [
    'display:flex', 'position:relative', 'box-sizing:border-box',
    `flex-direction:${n.layoutMode === 'HORIZONTAL' ? 'row' : 'column'}`,
    `gap:${n.layoutWrap === 'WRAP' ? `${n.counterAxisSpacing ?? 0}px ${n.itemSpacing}px` : `${n.itemSpacing}px`}`,
    `padding:${n.paddingTop ?? 0}px ${n.paddingRight ?? 0}px ${n.paddingBottom ?? 0}px ${n.paddingLeft ?? 0}px`,
    `justify-content:${J[n.primaryAxisAlignItems ?? 'MIN']}`,
    `align-items:${A[n.counterAxisAlignItems ?? 'MIN']}`,
  ];
  if (n.layoutWrap === 'WRAP') css.push('flex-wrap:wrap');
  if (n.minHeight) css.push(`min-height:${n.minHeight}px`);
  if (n.maxWidth) css.push(`max-width:${n.maxWidth}px`);
  return css;
}

function svgFor(n) {
  let svg = n._svg;
  const v = n.children?.find((c) => c.type === 'VECTOR');
  const tinted = (p) => p?.[0]?.color && (p[0].color.r + p[0].color.g + p[0].color.b) > 0;
  if (v && (tinted(v.strokes) || tinted(v.fills))) {
    const c = hex((tinted(v.strokes) ? v.strokes : v.fills)[0].color);
    svg = svg.replace(/stroke="#[0-9A-Fa-f]{3,8}"/g, `stroke="${c}"`).replace(/fill="#[0-9A-Fa-f]{3,8}"/g, `fill="${c}"`);
  }
  return svg.replace('<svg ', `<svg style="width:${n.width}px;height:${n.height}px;display:block" `);
}

function textHtml(n, extra) {
  const w = WEIGHT[n.fontName.style] ?? 400;
  const lh = n.lineHeight?.value ? `${n.lineHeight.value}px` : 'normal';
  const ls = n.letterSpacing?.value ? `${n.letterSpacing.value / 100}em` : '0';
  const color = paint(n.fills) ?? '#000';
  const css = [
    ...extra, `font-family:'Noto Sans CJK KR','Noto Sans KR',sans-serif`, `font-size:${n.fontSize}px`, `line-height:${lh}`,
    `font-weight:${w}`, `letter-spacing:${ls}`, `color:${color}`, 'white-space:pre-wrap', 'word-break:keep-all', 'overflow-wrap:anywhere',
    `text-align:${(n.textAlignHorizontal ?? 'LEFT').toLowerCase()}`,
  ];
  if (n.textAutoResize === 'WIDTH_AND_HEIGHT' && n.layoutSizingHorizontal === 'HUG') css.push('white-space:pre');
  if (n.maxLines) css.push(`display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:${n.maxLines};overflow:hidden`);
  let body = esc(n.characters);
  if (n._ranges?.length) {
    const chars = n.characters;
    const marks = new Array(chars.length).fill(null).map(() => ({}));
    for (const r of n._ranges) for (let i = r.a; i < r.b; i++) { if (r.fills) marks[i].color = paint(r.fills); if (r.font) marks[i].weight = WEIGHT[r.font.style]; }
    body = '';
    let i = 0;
    while (i < chars.length) {
      let j = i + 1;
      while (j < chars.length && JSON.stringify(marks[j]) === JSON.stringify(marks[i])) j++;
      const m = marks[i];
      const seg = esc(chars.slice(i, j));
      body += m.color || m.weight ? `<span style="${m.color ? `color:${m.color};` : ''}${m.weight ? `font-weight:${m.weight}` : ''}">${seg}</span>` : seg;
      i = j;
    }
  }
  return `<div data-name="${esc(n.name)}" style="${css.join(';')}">${body}</div>`;
}

export function render(n, parent = null) {
  if (n.visible === false) return '';
  const pos = n.layoutPositioning === 'ABSOLUTE' || (parent && !parent.isAuto && parent.type !== 'SECTION')
    ? [`position:absolute;left:${n.x}px;top:${n.y}px`] : [];
  const size = sizingCss(n, parent);
  if (n.type === 'TEXT') return textHtml(n, [...pos, ...size]);
  if (n._svg) return `<div data-name="${esc(n.name)}" style="${[...pos, ...size, 'flex-shrink:0'].join(';')}">${svgFor(n)}</div>`;
  if (n.type === 'ELLIPSE' || n.type === 'RECTANGLE') {
    const css = [...pos, ...size, ...boxCss(n), 'flex-shrink:0'];
    if (n.type === 'ELLIPSE') css.push('border-radius:50%');
    return `<div data-name="${esc(n.name)}" style="${css.join(';')}"></div>`;
  }
  const css = [...size, ...layoutCss(n), ...boxCss(n), ...pos];
  const kids = (n.children ?? []).map((c) => render(c, n)).join('');
  return `<div data-name="${esc(n.name)}" data-type="${n.type}" style="${css.join(';')}">${kids}</div>`;
}

export function page(title, frames, bg = '#E9E6E1') {
  return `<!doctype html><html><head><meta charset="utf-8"><title>${esc(title)}</title></head>
<body style="margin:0;background:${bg};font-family:sans-serif">
<div style="display:flex;flex-wrap:wrap;gap:48px;padding:48px;align-items:flex-start">
${frames.map((f) => `<div><div style="font:600 13px sans-serif;color:#555;margin-bottom:10px">${esc(f.name)}</div>${render(f)}</div>`).join('\n')}
</div></body></html>`;
}
