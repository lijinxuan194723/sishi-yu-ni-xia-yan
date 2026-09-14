import { dateKey, validDate, type Data } from './companion.ts';
/** Authored fan-work text, not game quotes, recorded speech, or model replies. */
export const LUKE_NOTES = [
  '华生，今天的小委托是什么？把最想完成的一件事告诉我，我们一起拆成小步骤。',
  '相机已经准备好了。今天不用寻找大事，一束光、一顿饭，都值得留一张照片。',
  '我把工具收好了，这段时间留给你。先从最容易的一件事开始吧，华生。',
  '花生又在窗边探头了。等你忙完，我们一起看看今天攒下了哪些新发现。',
  '那枚钥匙好好收着，未完成的故事也不着急。今天的这一页，我们慢慢写。',
  '华生，难题也像线索，先找到最确定的那一点。剩下的，休息之后再一起看。',
] as const;
export function companionNote(day: string, offset = 0): string {
  const seed = [...day].reduce((n, ch) => (n * 31 + ch.charCodeAt(0)) >>> 0, 0);
  const step = Number.isFinite(offset) ? Math.trunc(offset) : 0;
  return LUKE_NOTES[((seed + step) % LUKE_NOTES.length + LUKE_NOTES.length) % LUKE_NOTES.length];
}
export function completedMinutesByDay(logs: Data['focusLog']): Record<string, number> {
  const result: Record<string, number> = Object.create(null);
  for (const log of logs ?? []) {
    if (!Number.isFinite(log.minutes) || log.minutes <= 0) continue;
    const date = new Date(log.at);
    if (!Number.isFinite(date.getTime())) continue;
    const key = dateKey(date); result[key] = (result[key] ?? 0) + log.minutes;
  }
  return result;
}
export function formatCompanionMinutes(minutes: number): string {
  if (!Number.isFinite(minutes) || minutes <= 0) return '0 分钟';
  if (minutes < 1) return '不足 1 分钟';
  const whole = Math.floor(minutes);
  return whole >= 60 ? `${Math.floor(whole / 60)} 小时${whole % 60 ? ` ${whole % 60} 分钟` : ''}` : `${whole} 分钟`;
}
export function studyMonth(day: string) {
  if (!validDate(day)) throw new Error('无效的日历日期');
  const date = new Date(day + 'T12:00:00'), year = date.getFullYear(), month = date.getMonth();
  const first = new Date(year, month, 1), count = new Date(year, month + 1, 0).getDate();
  return { title: `${year} 年 ${month + 1} 月`, prefix: day.slice(0, 7),
    offset: (first.getDay() + 6) % 7,
    days: Array.from({ length: count }, (_, i) => dateKey(new Date(year, month, i + 1))),
    previous: dateKey(new Date(year, month - 1, 1)), next: dateKey(new Date(year, month + 1, 1)) };
}
