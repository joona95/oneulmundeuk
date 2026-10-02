// Quick emotion-style lab: renders v1/v2 blob + all styles (color and mono silhouette) to preview/emotion-lab.html.
import * as esbuild from 'esbuild';
import { writeFile, mkdir } from 'node:fs/promises';
const out = await esbuild.build({ entryPoints: ['src/emotions/index.ts'], bundle: true, write: false, format: 'esm' });
const m = await import('data:text/javascript;base64,' + Buffer.from(out.outputFiles[0].text).toString('base64'));
const { EMOTIONS, EMOTION_LABEL, EMOTION_STYLES, EMOTION_STYLES_V1, MARKER_STYLES, MARKER_SHAPES, sameShapeMarker, wrapSvg } = m;
const pal = (await esbuild.build({ entryPoints: ['src/tokens/palettes.ts'], bundle: true, write: false, format: 'esm' })).outputFiles[0].text;
const { emotionColors } = await import('data:text/javascript;base64,' + Buffer.from(pal).toString('base64'));
const rows = [...Object.keys(MARKER_SHAPES).map((k) => [`내 감정 조각 · ${MARKER_SHAPES[k].userLabel}`, sameShapeMarker(k)]), ['Marker B · Mixed', MARKER_STYLES.mixed], ['기존 Blob', EMOTION_STYLES_V1.blob], ['개선 Blob', EMOTION_STYLES.blob], ['Creature', EMOTION_STYLES.creature], ['Doodle', EMOTION_STYLES.doodle], ['Geometric', EMOTION_STYLES.geometric]];
const mono = { fill: '#2B2824', ink: '#2B2824' };
let h = '<html><body style="font-family:sans-serif;background:#FAF7F2;padding:24px">';
for (const [name, st] of rows) {
  h += `<h3>${name}</h3><div style="display:flex;gap:28px">`;
  for (const e of EMOTIONS) h += `<div style="text-align:center">${wrapSvg(st.draw(e, emotionColors[e]), 56)}<br>${wrapSvg(st.draw(e, emotionColors[e]), 20)} ${wrapSvg(st.draw(e, emotionColors[e]), 16)} ${wrapSvg(st.draw(e, mono), 28)}<div style="font-size:12px">${EMOTION_LABEL[e]}</div></div>`;
  h += '</div>';
}
await mkdir('preview', { recursive: true });
await writeFile('preview/emotion-lab.html', h + '</body></html>');
