// Font resolution. Prefers Pretendard (if installed locally), then Korean Google fonts bundled in Figma.
import type { Weight } from '../tokens/base';

const FAMILY_PREFERENCE = ['Pretendard', 'Pretendard Variable', 'Noto Sans KR', 'IBM Plex Sans KR', 'Inter'];

const STYLE_CANDIDATES: Record<Weight, string[]> = {
  400: ['Regular', 'Normal', 'Book'],
  500: ['Medium', 'Regular'],
  600: ['SemiBold', 'Semi Bold', 'Semibold', 'DemiBold', 'Bold'],
  700: ['Bold', 'SemiBold'],
};

let resolved: { family: string; styles: Record<Weight, FontName> } | null = null;

export async function loadFonts(): Promise<string> {
  if (resolved) return resolved.family;
  const available = await figma.listAvailableFontsAsync();
  const byFamily = new Map<string, Set<string>>();
  for (const f of available) {
    const set = byFamily.get(f.fontName.family) ?? new Set<string>();
    set.add(f.fontName.style);
    byFamily.set(f.fontName.family, set);
  }
  const family = FAMILY_PREFERENCE.find((fam) => byFamily.has(fam)) ?? 'Inter';
  const styles = byFamily.get(family) ?? new Set(['Regular', 'Medium', 'Semi Bold', 'Bold']);

  const pick = (w: Weight): FontName => {
    const style = STYLE_CANDIDATES[w].find((s) => styles.has(s)) ?? 'Regular';
    return { family, style };
  };
  const map = { 400: pick(400), 500: pick(500), 600: pick(600), 700: pick(700) } as Record<Weight, FontName>;
  const unique = new Map<string, FontName>();
  for (const f of Object.values(map)) unique.set(`${f.family}/${f.style}`, f);
  await Promise.all([...unique.values()].map((f) => figma.loadFontAsync(f)));
  resolved = { family, styles: map };
  return family;
}

export function font(weight: Weight): FontName {
  if (!resolved) throw new Error('loadFonts() must be awaited before creating text');
  return resolved.styles[weight];
}
