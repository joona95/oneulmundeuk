// Line icon set (24×24 grid, 1.75 stroke, round caps). Rendered as real vectors via createNodeFromSvg.
// To add an icon: add an entry to ICONS. `S` = stroked element, `F` = filled element.

type El = { k: 'S' | 'F'; svg: string };
const S = (svg: string): El => ({ k: 'S', svg });
const F = (svg: string): El => ({ k: 'F', svg });

export const ICONS = {
  home: [S('<path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z"/>')],
  records: [S('<rect x="5" y="4" width="14" height="16" rx="3"/>'), S('<path d="M9 9h6M9 13h6M9 17h3"/>')],
  explore: [S('<circle cx="12" cy="12" r="8"/>'), S('<path d="M15 9l-1.8 4.2L9 15l1.8-4.2z"/>')],
  settings: [S('<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/>'), S('<circle cx="15" cy="7" r="2"/>'), S('<circle cx="9" cy="17" r="2"/>')],
  plus: [S('<path d="M12 5v14M5 12h14"/>')],
  search: [S('<circle cx="11" cy="11" r="6.5"/>'), S('<path d="M16 16l4 4"/>')],
  close: [S('<path d="M6 6l12 12M18 6 6 18"/>')],
  back: [S('<path d="M15 5l-7 7 7 7"/>')],
  chevronLeft: [S('<path d="M14 6l-6 6 6 6"/>')],
  chevronRight: [S('<path d="M10 6l6 6-6 6"/>')],
  more: [F('<circle cx="12" cy="5.5" r="1.6"/>'), F('<circle cx="12" cy="12" r="1.6"/>'), F('<circle cx="12" cy="18.5" r="1.6"/>')],
  calendar: [S('<rect x="4" y="5.5" width="16" height="14.5" rx="3"/>'), S('<path d="M4 10h16M8.5 3.5v4M15.5 3.5v4"/>')],
  image: [S('<rect x="4" y="5" width="16" height="14" rx="3"/>'), S('<circle cx="9" cy="10" r="1.6"/>'), S('<path d="M20 15.5l-4.5-4.5L7 19.5"/>')],
  tag: [S('<path d="M4 12.5V5a1 1 0 0 1 1-1h7.5l7.5 7.5-7.5 7.5z"/>'), S('<circle cx="8.5" cy="8.5" r="1.2"/>')],
  check: [S('<path d="M5 12.5l4.5 4.5L19 7.5"/>')],
  arrowRight: [S('<path d="M5 12h14M13 6l6 6-6 6"/>')],
  arrowUp: [S('<path d="M12 19V5M6 11l6-6 6 6"/>')],
  expand: [S('<path d="M14 5h5v5M10 19H5v-5M19 5l-6 6M5 19l6-6"/>')],
  lock: [S('<rect x="5" y="10.5" width="14" height="9.5" rx="2.5"/>'), S('<path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>')],
  bell: [S('<path d="M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z"/>'), S('<path d="M10 20.5a2 2 0 0 0 4 0"/>')],
  download: [S('<path d="M12 4v11M7 10l5 5 5-5M5 20h14"/>')],
  upload: [S('<path d="M12 20V9M7 14l5-5 5 5M5 4h14"/>')],
  sort: [S('<path d="M7 5v14M4 16l3 3 3-3M17 19V5M14 8l3-3 3 3"/>')],
  link: [S('<path d="M10 14a4 4 0 0 0 5.6 0l3-3a4 4 0 0 0-5.6-5.6l-1 1"/>'), S('<path d="M14 10a4 4 0 0 0-5.6 0l-3 3a4 4 0 0 0 5.6 5.6l1-1"/>')],
  edit: [S('<path d="M5 19h4L19 9l-4-4L5 15z"/>')],
  folder: [S('<path d="M4 7a1 1 0 0 1 1-1h4.5l2 2H19a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z"/>')],
  drop: [S('<path d="M12 4c3 4 6 7 6 10a6 6 0 0 1-12 0c0-3 3-6 6-10z"/>')],
  shield: [S('<path d="M12 4l7 3v5c0 4-3 7-7 8-4-1-7-4-7-8V7z"/>')],
  clock: [S('<circle cx="12" cy="12" r="8"/>'), S('<path d="M12 8v4l3 2"/>')],
  history: [S('<path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3L4.5 9"/>'), S('<path d="M4.5 5v4h4M12 8.5V12l2.5 1.5"/>')],
  signal: [F('<rect x="3" y="15" width="3" height="5" rx="1"/>'), F('<rect x="8" y="11" width="3" height="9" rx="1"/>'), F('<rect x="13" y="7" width="3" height="13" rx="1"/>'), F('<rect x="18" y="3" width="3" height="17" rx="1"/>')],
  wifi: [S('<path d="M3.5 9.5a12 12 0 0 1 17 0M6.5 12.8a7.5 7.5 0 0 1 11 0M9.5 16a3 3 0 0 1 5 0"/>'), F('<circle cx="12" cy="19" r="1.4"/>')],
  battery: [S('<rect x="3" y="7" width="16" height="10" rx="2.5"/>'), F('<rect x="5" y="9" width="10" height="6" rx="1"/>'), F('<rect x="20" y="10" width="1.8" height="4" rx=".9"/>')],
} satisfies Record<string, El[]>;

export type IconName = keyof typeof ICONS;

export function iconSvg(name: IconName, color: string, size: number, strokeWidth = 1.75): string {
  const body = ICONS[name]
    .map((e) =>
      e.svg.replace(
        /^<(\w+)/,
        e.k === 'S'
          ? `<$1 stroke="${color}" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-linejoin="round" fill="none"`
          : `<$1 fill="${color}"`,
      ),
    )
    .join('');
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">${body}</svg>`;
}
