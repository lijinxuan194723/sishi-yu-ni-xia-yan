'use client';
import { useEffect, useRef, useState } from 'react';
import { AlarmClock, Bell, Hourglass } from 'lucide-react';
import { actionsBridge, nativeAction } from '@/lib/native-actions';
export function ReminderPanel() {
 const [mode, setMode] = useState<'alarm'|'timer'>('timer'), [time, setTime] = useState('08:00'), [minutes, setMinutes] = useState(25);
 const [label, setLabel] = useState('夏彦提醒你：休息一下，喝口水'), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false);
 const [supported, setSupported] = useState(false); const opening = useRef(false), alive = useRef(true);
 useEffect(() => { alive.current = true; setSupported(!!actionsBridge()?.clockAction); return () => { alive.current = false; }; }, []);
 async function openClock(kind: 'alarm'|'timer'|'manage') {
  const bridge = actionsBridge(); if (!bridge?.clockAction || opening.current) return;
  const parts = time.match(/^(\d{2}):(\d{2})$/); if (kind === 'alarm' && (!parts || +parts[1] > 23 || +parts[2] > 59)) { setNotice('请选择有效的提醒时间。'); return; }
  if (kind === 'timer' && (!Number.isInteger(minutes) || minutes < 1 || minutes > 1439)) { setNotice('倒计时请输入 1～1439 的整数分钟。'); return; }
  opening.current = true; setBusy(true);
  try { const result = await nativeAction(id => bridge.clockAction!(id, kind, +(parts?.[1] || 0), +(parts?.[2] || 0), minutes * 60, label.trim())); if (alive.current) setNotice(result.message); }
  catch (e) { if (alive.current) setNotice(e instanceof Error ? e.message : '系统时钟暂时无法打开。'); }
  finally { opening.current = false; if (alive.current) setBusy(false); }
 }
 return <section className="card reminder-panel" aria-label="闹钟与锁屏提醒">
  <h2><Bell size={20}/> 夏彦的提醒便笺</h2>
  <div className="reminder-tabs" aria-label="提醒类型"><button type="button" aria-pressed={mode === 'timer'} onClick={() => setMode('timer')}><Hourglass size={16}/>倒计时提醒</button><button type="button" aria-pressed={mode === 'alarm'} onClick={() => setMode('alarm')}><AlarmClock size={16}/>定时闹钟</button></div>
  <div className="reminder-fields">{mode === 'timer' ? <label>倒计时（分钟）<input type="number" min={1} max={1439} step={1} value={minutes} onChange={e => setMinutes(Number(e.target.value))}/></label> : <label>闹钟时间<input type="time" value={time} onChange={e => setTime(e.target.value)}/></label>}<label>提醒文字<input maxLength={80} value={label} onChange={e => setLabel(e.target.value)}/></label></div>
  <button type="button" className="primary" disabled={!supported || busy} data-haptic="confirm" onClick={() => void openClock(mode)}>{busy ? '正在打开系统时钟…' : '到系统时钟确认提醒'}</button>
  <button type="button" className="soft-button" disabled={!supported || busy} onClick={() => void openClock('manage')}>查看或管理系统闹钟</button>

  {!supported&&<p className="reminder-unavailable-hf3" role="status">当前环境不能打开系统时钟，上方倒计时仍可使用。</p>}
  {notice && <p role="status">{notice}</p>}
 </section>;
}
