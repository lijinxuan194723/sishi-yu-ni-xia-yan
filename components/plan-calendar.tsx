'use client';
import { ChevronLeft, ChevronRight, KeyRound } from 'lucide-react';
import { dateKey, type Data } from '@/lib/companion';
import { annualDate } from '@/lib/daily';

type Props = { month: Date; onMonth: (month: Date) => void; today: string; selected: string; onSelect: (day: string) => void; data: Data };
export function PlanCalendar({ month, onMonth, today, selected, onSelect, data }: Props) {
  const year = month.getFullYear(), m = month.getMonth();
  const birthday = data.birthday ? dateKey(annualDate(data.birthday, year)) : '';
  const anniversaries = new Set([data.since, ...(data.anniversaries ?? []).map(a => a.date)].map(d => dateKey(annualDate(d, year))));
  return <section className="card calendar luke-plan-calendar" aria-label="和夏彦的一起计划月历">
    <div className="luke-calendar-note"><KeyRound size={17} aria-hidden="true"/><span>华生，今天的约定，我替你记着。</span></div>
    <div className="month-head">
      <button type="button" className="round" aria-label="上个月" onClick={() => onMonth(new Date(year, m - 1, 1))}><ChevronLeft/></button>
      <h2 aria-live="polite">{year} 年 {m + 1} 月</h2>
      <button type="button" className="round" aria-label="下个月" onClick={() => onMonth(new Date(year, m + 1, 1))}><ChevronRight/></button>
    </div>
    <div className="calendar-grid">
      {'一二三四五六日'.split('').map(d => <small key={d}>{d}</small>)}
      {Array.from({ length: (new Date(year, m, 1).getDay() + 6) % 7 }, (_, i) => <span key={`blank-${i}`} aria-hidden="true"/>)}
      {Array.from({ length: new Date(year, m + 1, 0).getDate() }, (_, i) => {
        const day = dateKey(new Date(year, m, i + 1)), isToday = day === today;
        const count = data.tasks.filter(t => t.date === day).length;
        const tag = day === birthday ? '你的生日' : day.slice(5) === '12-05' ? '夏彦生日' : anniversaries.has(day) ? '纪念日' : count ? `${count}项约定` : isToday ? '今天' : '';
        return <button type="button" key={day} className={`${selected === day ? 'selected ' : ''}${isToday ? 'today' : ''}`}
          aria-label={`${day}${isToday ? '，今天' : ''}${tag && tag !== '今天' ? '，' + tag : ''}`} aria-current={isToday ? 'date' : undefined}
          aria-pressed={selected === day} data-haptic="selection" onClick={() => onSelect(day)}>
          {isToday && <img className="luke-today-head" src="/images/luke-today.webp" alt="" width={40} height={34} decoding="async"/>}
          <span className="calendar-day-number">{i + 1}</span><small>{tag}</small>
        </button>;
      })}
    </div><p className="luke-calendar-caption"><img src="/images/luke-today.webp" alt="" width={23} height={20}/><span>小夏彦标记今天；浅色圆框标记你选中的日期。</span></p>
  </section>;
}
