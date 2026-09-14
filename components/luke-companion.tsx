'use client';
import { memo, useMemo, useState } from 'react';
import { Bird, BookHeart, Camera, ChevronLeft, ChevronRight, KeyRound, MessageCircle, RefreshCw, Timer } from 'lucide-react';
import type { Data } from '@/lib/companion';
import { companionNote, completedMinutesByDay, formatCompanionMinutes, studyMonth } from '@/lib/luke-companion';
import { requestCompanionPage } from '@/lib/use-page-tool';
import '../app/luke-companion.css';

export function LukeDesk({ data, today }: { data: Data; today: string }) {
  const [offset, setOffset] = useState(0);
  const minutes = useMemo(() => completedMinutesByDay(data.focusLog)[today] ?? 0, [data.focusLog, today]);
  return <section className="card luke-desk" aria-label="夏彦的工作台">
    <header className="luke-desk-heading"><div><KeyRound size={17} aria-hidden="true" /><span>夏彦的工作台<small>LUKE · DAILY NOTES</small></span></div>
      <button type="button" className="round" aria-label="换一张夏彦便笺" onClick={() => setOffset(n => n + 1)}><RefreshCw size={16} /></button></header>
    <div className="luke-note-row"><img className="luke-note-avatar" src="/images/luke-nap.png" alt="夏彦插画" width={64} height={64} loading="lazy" />
      <div className="luke-note"><strong>给华生的今日便笺</strong><p role="status" aria-live="polite">{companionNote(today, offset)}</p></div></div>
    <div className="luke-desk-actions">
      <button type="button" onClick={() => requestCompanionPage('timers')}><Timer size={18} /><span>接一份专注委托<small>{minutes > 0 ? `今天已记录 ${formatCompanionMinutes(minutes)}` : '我在这里，陪你开始'}</small></span></button>
      <button type="button" onClick={() => requestCompanionPage('notes')}><Camera size={18} /><span>收藏今天的线索<small>把小事写进手记</small></span></button>
      <button type="button" onClick={() => requestCompanionPage('chat')}><MessageCircle size={18} /><span>和夏彦聊一会儿<small>慢慢说，我听着</small></span></button>
    </div><small className="luke-authored">陪伴便笺为同人创作，不是游戏原台词。</small>
  </section>;
}

export const StudyCompanion = memo(function StudyCompanion({ running, subject, today }: { running: boolean; subject: string; today: string }) {
  return <div className="study-companion-note">
    <img src="/images/luke-nap.png" alt="" width={56} height={56} />
    <div><strong>夏彦 <span>{running ? '正在陪你专注' : '等你一起开始'}</span></strong>
      <p>{running ? `华生，${subject || '这一页'}交给你。我先安静陪着，结束后再一起收好今天的成果。` : companionNote(today, 2)}</p>
      <small>陪伴短句 · 同人创作</small></div>
  </div>;
});

export function StudyDial({ seconds, running, subject }: { seconds: number; running: boolean; subject: string }) {
  return <div className="luke-study-dial" data-running={running}>
    <KeyRound className="luke-dial-key" size={22} aria-hidden="true" />
    <div className="focus-clock" role="timer" aria-label="本次学习已用时间" aria-live="off">{String(Math.floor(seconds / 3600)).padStart(2, '0')}:{String(Math.floor(seconds % 3600 / 60)).padStart(2, '0')}:{String(seconds % 60).padStart(2, '0')}</div>
    <span className="luke-dial-caption">正计时 · {subject || '待选择科目'}</span>
    <span className="luke-dial-peanut"><Bird size={17} aria-hidden="true" /> 花生也在这里</span>
  </div>;
}

export function LukeStudyCalendar({ data, day, today, onDay }: { data: Data; day: string; today: string; onDay: (day: string) => void }) {
  const month = useMemo(() => studyMonth(day), [day]);
  const daily = useMemo(() => completedMinutesByDay(data.focusLog), [data.focusLog]);
  const values = month.days.map(date => daily[date] ?? 0), total = values.reduce((a, b) => a + b, 0), max = Math.max(1, ...values);
  const selected = daily[day] ?? 0;
  return <section className="card luke-study-calendar" aria-label="和夏彦的专注月历">
    <header><div><BookHeart size={18} aria-hidden="true" /><h2>和夏彦的专注月历<small>{month.title}</small></h2></div><div>
      <button type="button" className="round" aria-label="上个月的专注印记" onClick={() => onDay(month.previous)}><ChevronLeft size={18} /></button>
      <button type="button" className="round" aria-label="下个月的专注印记" onClick={() => onDay(month.next)}><ChevronRight size={18} /></button></div></header>
    <div className="luke-stamp-grid">{'一二三四五六日'.split('').map(d => <small key={d}>{d}</small>)}
      {Array.from({ length: month.offset }, (_, i) => <span key={`blank-${i}`} />)}
      {month.days.map((date, i) => <button type="button" key={date} aria-pressed={date === day} aria-current={date === today ? 'date' : undefined}
        data-recorded={values[i] > 0} aria-label={`${date}，已记录 ${formatCompanionMinutes(values[i])}`} onClick={() => onDay(date)}>
        <span>{i + 1}</span>{values[i] > 0 && <img src="/images/luke-nap.png" alt="" width={24} height={24} loading="lazy" />}
      </button>)}
    </div>
    <p className="luke-day-record" role="status"><KeyRound size={17} aria-hidden="true" /><span>{day.slice(5).replace('-', ' / ')} · {selected > 0 ? `一起留下 ${formatCompanionMinutes(selected)}的专注印记` : '还没有已保存的专注印记，慢慢来。'}</span></p>
    <div className="luke-month-heading"><strong>本月已记录 {formatCompanionMinutes(total)}</strong><button type="button" onClick={() => onDay(today)}>回到今天</button></div>
    <div className="luke-month-bars" role="img" aria-label={`${month.title}，已保存 ${formatCompanionMinutes(total)}，${values.filter(v => v > 0).length} 天有记录`}>
      {month.days.map((date, i) => <div key={date} title={`${date}：${formatCompanionMinutes(values[i])}`}><span style={{ height: `${values[i] / max * 100}%` }} /><small>{i === 0 || (i + 1) % 5 === 0 || i === month.days.length - 1 ? i + 1 : ''}</small></div>)}
    </div><small className="luke-authored">只为已结束并保存的学习记录盖印；进行中的计时不提前计入。</small>
  </section>;
}
