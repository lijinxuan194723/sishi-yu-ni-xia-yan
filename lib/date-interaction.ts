/** Date-only interaction helpers. No UTC conversion, timezone or platform picker. */
export function normalizeDateEntry(value: string): string {
  const text = value.normalize('NFKC').trim();
  if (/^\d{8}$/.test(text)) return `${text.slice(0,4)}-${text.slice(4,6)}-${text.slice(6)}`;
  const parts = text.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/);
  return parts ? `${parts[1]}-${parts[2].padStart(2,'0')}-${parts[3].padStart(2,'0')}` : text;
}
export function yearFromEntry(value: string, lower: number, upper: number): number | null {
  const text = value.normalize('NFKC').trim();
  if (!/^\d{4}$/.test(text)) return null;
  const year = Number(text);
  return year >= lower && year <= upper ? year : null;
}
export function boundedMonth(year: number, month: number, min: Date, max: Date): Date {
  const index = Math.max(min.getFullYear()*12+min.getMonth(), Math.min(max.getFullYear()*12+max.getMonth(), year*12+month));
  return new Date(Math.floor(index/12), index%12, 1, 12);
}
export function moveCalendarDay(day: Date, key: string, shift = false): Date | null {
  const next = new Date(day.getFullYear(), day.getMonth(), day.getDate(), 12);
  const weekday = (day.getDay()+6)%7;
  const deltas: Record<string, number> = {ArrowLeft:-1,ArrowRight:1,ArrowUp:-7,ArrowDown:7,Home:-weekday,End:6-weekday};
  if (Object.prototype.hasOwnProperty.call(deltas,key)) { next.setDate(day.getDate()+deltas[key]); return next; }
  if (key !== 'PageUp' && key !== 'PageDown') return null;
  const delta = (key === 'PageUp' ? -1 : 1)*(shift ? 12 : 1);
  next.setDate(1); next.setMonth(next.getMonth()+delta);
  next.setDate(Math.min(day.getDate(), new Date(next.getFullYear(),next.getMonth()+1,0).getDate()));
  return next;
}
