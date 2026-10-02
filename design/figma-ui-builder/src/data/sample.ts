// Realistic Korean sample records. "Today" in all mockups is 2026-10-02 (Fri).
import type { Emotion } from '../emotions/types';

export const TODAY = '2026-10-02';

export const CATEGORIES = ['커리어', '성장', '개발', '사이드 프로젝트', '일상', '관계', '취미'] as const;
export type Category = (typeof CATEGORIES)[number];

export interface SampleRecord {
  id: string;
  at: string; // ISO local datetime
  emotion?: Emotion;
  category?: Category;
  tags?: string[];
  photo?: boolean;
  body: string;
}

export const RECORDS: SampleRecord[] = [
  { id: 'r01', at: '2026-10-02T08:42', emotion: 'neutral', category: '커리어', tags: ['성장', '회사'],
    body: '요즘 회사에서 내가 제대로 성장하고 있는지 가끔 모르겠다. 그래도 DB 문제를 직접 파고 원인을 찾는 과정은 꽤 재미있다. 어제 슬로우 쿼리 하나 잡은 것도 은근히 뿌듯했고.' },
  { id: 'r02', at: '2026-10-01T22:15', emotion: 'tired', category: '일상',
    body: '야근하고 들어오니 11시. 씻고 바로 자야지.' },
  { id: 'r03', at: '2026-10-01T12:30', emotion: 'joy', category: '관계',
    body: '점심에 팀 사람들이랑 처음으로 회사 얘기 말고 다른 얘기를 오래 했다. 다들 생각보다 취미가 다양하다.' },
  { id: 'r04', at: '2026-09-30T07:58', emotion: 'excited', category: '사이드 프로젝트', tags: ['기록앱'],
    body: '기록 앱 아이디어. 쓰는 순간 과거의 내가 썼던 비슷한 문장을 보여주면 어떨까. AI가 말하는 게 아니라 내가 나한테 답하는 느낌으로.' },
  { id: 'r05', at: '2026-09-28T16:20', emotion: 'calm', category: '취미', photo: true,
    body: '오랜만에 한강 따라 자전거. 바람이 선선해서 생각이 좀 정리됐다.' },
  { id: 'r06', at: '2026-09-24T23:40', emotion: 'anxious', category: '커리어', tags: ['이직'],
    body: '링크드인에서 연락이 하나 왔다. 지금 옮기는 게 맞는지 모르겠다. 아직 여기서 배울 게 남은 것 같기도 하고, 그냥 익숙해서 머무는 것 같기도 하고.' },
  { id: 'r07', at: '2026-09-21T10:05', emotion: 'joy', category: '개발',
    body: 'Redisson 락 걸린 부분 테스트 코드 드디어 통과. 동시성 테스트는 짤 때마다 새롭다.' },
  { id: 'r08', at: '2026-09-17T21:10', emotion: 'tired', category: '성장',
    body: '공부하기 싫은 날. 책은 펴놨는데 한 페이지도 못 읽었다. 이런 날도 있는 거지.' },
  { id: 'r09', at: '2026-09-02T08:30', emotion: 'neutral', category: '커리어',
    body: '한 달 전 이맘때 쓴 다짐: 운영 이슈 생기면 피하지 말고 먼저 손 들기. 지켜지고 있는지 체크해보자.' },
  { id: 'r10', at: '2026-08-30T19:45', emotion: 'excited', category: '사이드 프로젝트',
    body: '레시피 앱 배포 성공. 별거 아닌데 도메인 붙으니까 진짜 서비스 같다.' },
  { id: 'r11', at: '2026-07-12T09:20', emotion: 'calm', category: '일상',
    body: '아침에 커피 내리면서 아무 생각 안 하는 10분이 좋다.' },
  { id: 'r12', at: '2026-04-11T20:10', emotion: 'calm', category: '커리어',
    body: '운영 이슈를 직접 다루면서 조금씩 보는 눈이 생기는 것 같다. 예전엔 로그만 봐도 막막했는데, 이제는 어디부터 볼지 감이 온다.' },
  { id: 'r13', at: '2026-04-02T22:30', emotion: 'sad', category: '관계',
    body: '오랜 친구랑 연락이 뜸해졌다. 누가 먼저랄 것도 없이. 조금 쓸쓸하다.' },
  { id: 'r14', at: '2026-03-15T13:00', emotion: 'excited', category: '개발', tags: ['DB'],
    body: '인덱스 하나로 쿼리가 3초에서 40ms가 됐다. 이 맛에 DB 보는 것 같다.' },
  { id: 'r15', at: '2026-01-04T10:00', emotion: 'neutral', category: '성장', tags: ['다짐'],
    body: '올해는 DB랑 운영 쪽을 내 전문성으로 가져가고 싶다. 막연한 성장 말고 방향이 있는 성장.' },
  { id: 'r16', at: '2025-10-03T23:10', emotion: 'tired', category: '커리어',
    body: '아직 회사 파악도 덜 된 것 같고 부족한 것만 보인다. 그래도 하나씩 알아가는 중이다.' },
  { id: 'r17', at: '2025-09-26T08:15', emotion: 'anxious', category: '커리어', tags: ['이직'],
    body: '이직 1년 차. 잘 옮긴 건지 아직 판단이 안 선다. 일단 1년은 버텨보자고 했던 나랑의 약속.' },
  { id: 'r18', at: '2025-07-14T21:00', emotion: 'neutral', category: '성장',
    body: 'DB랑 인프라를 좀 더 공부하고 싶다.' },
  { id: 'r19', at: '2025-06-20T18:40', emotion: 'joy', category: '사이드 프로젝트',
    body: '주말 내내 사이드 프로젝트만 했는데 하나도 안 피곤했다. 재미있는 걸 할 때 나는 이렇게 집중하는구나.' },
  { id: 'r20', at: '2025-03-08T11:30', emotion: 'sad', category: '성장',
    body: '코드 리뷰에서 지적을 많이 받았다. 틀린 말은 하나도 없어서 더 속상했다.' },
  { id: 'r21', at: '2024-10-02T20:00', emotion: 'excited', category: '커리어',
    body: '2년 전 오늘. 첫 출근 전날 밤. 긴장되는데 설렌다.' },
];

export const byId = (id: string): SampleRecord => {
  const r = RECORDS.find((x) => x.id === id);
  if (!r) throw new Error(`Sample record ${id} not found`);
  return r;
};

// ─── Flow data ──────────────────────────────────────────────────────────────

/** The record just saved in the Write → Connect flow. */
export const JUST_SAVED = byId('r01');

/** Semantically related past records for JUST_SAVED (newest first). */
export const RELATED_TO_JUST_SAVED = ['r12', 'r15', 'r16', 'r18'].map(byId);

/** Home "다시 만난 생각" picks, labelled by period. */
export const REDISCOVERY = [
  { period: '한 달 전 이맘때', record: byId('r09') },
  { period: '6개월 전', record: byId('r12') },
  { period: '1년 전 이맘때', record: byId('r16') },
  { period: '2년 전 오늘', record: byId('r21') },
];

export const EXAMPLE_QUERIES = [
  '작년 이맘때 무슨 고민을 했지?',
  '이직에 대해 어떤 생각을 했었지?',
  '공부하기 싫다고 했던 기록',
  '사이드 프로젝트가 재미있었던 때',
];

export const SEARCH = {
  query: '이직에 대해 어떤 생각을 했었지?',
  results: [
    { record: byId('r06'), match: '지금 옮기는 게 맞는지 모르겠다' },
    { record: byId('r17'), match: '잘 옮긴 건지 아직 판단이 안 선다' },
    { record: byId('r15'), match: 'DB랑 운영 쪽을 내 전문성으로' },
    { record: byId('r21'), match: '첫 출근 전날 밤' },
  ],
  total: 9,
};

/** Detail screen subject and its related records. */
export const DETAIL = { record: byId('r06'), related: ['r17', 'r15', 'r12'].map(byId) };

// ─── Formatting ─────────────────────────────────────────────────────────────

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function parse(iso: string) {
  const [d, t = '00:00'] = iso.split('T');
  const [y, m, day] = d.split('-').map(Number);
  const [hh, mm] = t.split(':').map(Number);
  return { y, m, day, hh, mm, date: new Date(Date.UTC(y, m - 1, day)) };
}

export function fmtTime(iso: string): string {
  const { hh, mm } = parse(iso);
  const am = hh < 12;
  const h12 = hh % 12 === 0 ? 12 : hh % 12;
  return `${am ? '오전' : '오후'} ${h12}:${String(mm).padStart(2, '0')}`;
}

export function fmtDate(iso: string, withWeekday = false): string {
  const { y, m, day, date } = parse(iso);
  return `${y}년 ${m}월 ${day}일${withWeekday ? ` ${WEEKDAYS[date.getUTCDay()]}요일` : ''}`;
}

export function fmtMonthDay(iso: string, withWeekday = true): string {
  const { m, day, date } = parse(iso);
  return `${m}월 ${day}일${withWeekday ? ` ${WEEKDAYS[date.getUTCDay()]}요일` : ''}`;
}

/** "오늘", "어제", "3일 전", "한 달 전", "6개월 전", "1년 전", "1년 3개월 전" */
export function fmtAgo(iso: string, today = TODAY): string {
  const a = parse(iso), b = parse(today);
  const days = Math.round((b.date.getTime() - a.date.getTime()) / 86400000);
  if (days <= 0) return '오늘';
  if (days === 1) return '어제';
  if (days < 7) return `${days}일 전`;
  if (days < 28) return `${Math.round(days / 7)}주 전`;
  // Round to the nearest month so "364 days ago" reads as "1년 전", not "11개월 전".
  const months = Math.round(days / 30.44);
  if (months <= 1) return '한 달 전';
  if (months < 12) return `${months}개월 전`;
  const years = Math.floor(months / 12), rest = months % 12;
  return rest ? `${years}년 ${rest}개월 전` : `${years}년 전`;
}

/** Card meta line: "오늘 · 오전 8:42" or "9월 24일 · 오후 11:40". */
export function fmtMeta(iso: string): string {
  const ago = fmtAgo(iso);
  const head = ago === '오늘' || ago === '어제' ? ago : fmtMonthDay(iso, false);
  return `${head} · ${fmtTime(iso)}`;
}

/** Calendar mock: September 2026, emotions recorded per day (up to 3 dots shown). */
export const CALENDAR = {
  year: 2026,
  month: 9,
  selectedDay: 24,
  days: {
    2: ['neutral'], 4: ['calm'], 7: ['joy', 'tired'], 9: ['tired'], 11: ['calm'], 14: ['excited'],
    15: ['neutral', 'calm'], 17: ['tired'], 18: ['joy'], 21: ['joy'], 22: ['calm', 'anxious', 'neutral'],
    24: ['anxious', 'neutral'], 25: ['tired'], 28: ['calm'], 30: ['excited'],
  } as Record<number, Emotion[]>,
  selectedRecords: [byId('r06'), { id: 'c01', at: '2026-09-24T12:10', emotion: 'neutral', category: '일상', body: '점심은 회사 앞 국숫집. 오늘은 별일 없이 지나가는 중.' } as SampleRecord],
};
