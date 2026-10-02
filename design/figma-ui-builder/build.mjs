// Builds dist/code.js (plugin sandbox) and dist/ui.html (Builder panel, script inlined).
import * as esbuild from 'esbuild';
import { readFile, writeFile, mkdir } from 'node:fs/promises';

const watch = process.argv.includes('--watch');
await mkdir('dist', { recursive: true });

const codeOptions = {
  entryPoints: ['src/code.ts'],
  bundle: true,
  outfile: 'dist/code.js',
  target: 'es2017',
  format: 'iife',
  logLevel: 'info',
};

async function buildUi() {
  const result = await esbuild.build({
    entryPoints: ['src/ui/ui.ts'],
    bundle: true,
    write: false,
    target: 'es2017',
    format: 'iife',
  });
  const js = result.outputFiles[0].text;
  const html = await readFile('src/ui/ui.html', 'utf8');
  // function replacer: avoids `$&`-style patterns inside the bundle being interpreted
  await writeFile('dist/ui.html', html.replace('<!-- SCRIPT -->', () => `<script>\n${js}</script>`));
  console.log('  dist/ui.html');
}

if (watch) {
  const ctx = await esbuild.context(codeOptions);
  await ctx.watch();
  await buildUi();
  const { watch: fsWatch } = await import('node:fs');
  fsWatch('src/ui', { recursive: true }, () => buildUi().catch(console.error));
  console.log('watching…');
} else {
  await esbuild.build(codeOptions);
  await buildUi();
}
