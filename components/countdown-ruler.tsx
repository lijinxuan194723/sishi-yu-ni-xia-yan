'use client';
import {useEffect,useRef,useState} from 'react';
import {usePointerLifecycle} from '@/hooks/use-pointer-lifecycle';
import {motion,animate,useMotionValue} from 'motion/react';
import {Play,Pause,RotateCcw,Clock3} from 'lucide-react';
import {rulerMinutes,countdownRemaining,startCountdown,pauseCountdown,resumeCountdown,completeCountdown} from '@/lib/countdown';
import {prefersReducedMotion} from '@/lib/motion';
import type {Data} from '@/lib/companion';
import {AnalogClock} from './analog-clock';
import {useClockActivity} from '@/hooks/use-clock-activity';
import {remainingText} from '@/lib/analog-time';
export function CountdownRuler({value,onChange,onCommit,disabled}:{value:number;onChange:(v:number)=>void;onCommit:(v:number)=>void;disabled:boolean}){const element=useRef<HTMLDivElement>(null),offset=useMotionValue(-value*8),active=useRef<{id:number;x:number;y:number;start:number;claimed:boolean}|null>(null),pointers=useRef(new Set<number>()),selected=useRef(value),animation=useRef<ReturnType<typeof animate>|null>(null);selected.current=value;
 function reset(){active.current=null;animation.current?.stop();animation.current=animate(offset,-selected.current*8,prefersReducedMotion()?{duration:0}:{type:'spring',stiffness:380,damping:24});}
 function cancelDrag(){const g=active.current;active.current=null;if(g){selected.current=g.start;onChange(g.start);if(element.current?.hasPointerCapture(g.id))element.current.releasePointerCapture(g.id);}pointers.current.clear();reset();}
 usePointerLifecycle(element,active,pointers,cancelDrag);

 useEffect(()=>{if(!active.current)offset.set(-value*8);},[value]);useEffect(()=>()=>animation.current?.stop(),[]);useEffect(()=>{if(disabled)cancelDrag();},[disabled]);
 return <div ref={element} className="countdown-ruler210" data-no-swipe role="slider" aria-label="拖动刻度选择倒计时" aria-valuemin={0} aria-valuemax={180} aria-valuenow={value} aria-valuetext={`${value} 分钟，按回车开始`} aria-disabled={disabled} tabIndex={disabled?-1:0} onKeyDown={e=>{if(disabled)return;let v=value;if(e.key==='ArrowRight')v=Math.min(180,v+1);else if(e.key==='ArrowLeft')v=Math.max(0,v-1);else if(e.key==='PageUp')v=Math.min(180,v+5);else if(e.key==='PageDown')v=Math.max(0,v-5);else if(e.key==='Home')v=0;else if(e.key==='End')v=180;else if(e.key==='Enter'||e.key===' '){e.preventDefault();if(value>0)onCommit(value);return;}else return;e.preventDefault();onChange(v);}}
 onPointerDown={e=>{pointers.current.add(e.pointerId);if(pointers.current.size>1){cancelDrag();return;}if(disabled||!e.isPrimary||e.button!==0)return;animation.current?.stop();active.current={id:e.pointerId,x:e.clientX,y:e.clientY,start:value,claimed:false};}}
 onPointerMove={e=>{const g=active.current;if(!g||g.id!==e.pointerId||disabled)return;const dx=e.clientX-g.x,dy=e.clientY-g.y;if(!g.claimed){if(Math.abs(dy)>8&&Math.abs(dy)>Math.abs(dx)){cancelDrag();return;}if(Math.abs(dx)<10||Math.abs(dx)<Math.abs(dy)*1.5)return;g.claimed=true;e.currentTarget.setPointerCapture(e.pointerId);}e.preventDefault();const v=rulerMinutes(g.start,dx);selected.current=v;onChange(v);const raw=-g.start*8+dx;offset.set(raw>0?raw*.12:raw< -1440?-1440+(raw+1440)*.12:raw);}}
 onPointerUp={e=>{pointers.current.delete(e.pointerId);const g=active.current;active.current=null;if(e.currentTarget.hasPointerCapture(e.pointerId))e.currentTarget.releasePointerCapture(e.pointerId);reset();if(g?.id===e.pointerId&&g.claimed&&!disabled&&selected.current>0)onCommit(selected.current);}}
 onPointerCancel={e=>{pointers.current.delete(e.pointerId);cancelDrag();}} onLostPointerCapture={()=>{if(active.current)cancelDrag();}}>
 <div className="ruler-cursor210" aria-hidden="true"/><div className="ruler-window210"><motion.div className="ruler-track210" style={{x:offset}} aria-hidden="true">{Array.from({length:181},(_,i)=><span key={i} data-major={i%5===0}>{i%5===0&&<b>{i}</b>}</span>)}</motion.div></div></div>;
}
export function InAppCountdown({data,ready,save}:{data:Data;ready:boolean;save:(p:Partial<Data>|((d:Data)=>Partial<Data>))=>void}){
 const [minutes,setMinutes]=useState(()=>data.countdown?Math.max(1,Math.min(180,Math.ceil(data.countdown.seconds/60))):25),[now,setNow]=useState(Date.now()),[error,setError]=useState('');
 const {ref,visible}=useClockActivity<HTMLElement>();
 const c=data.countdown,remaining=countdownRemaining(c,now),running=c?.endsAt!==undefined,changed=!!c&&!running&&minutes!==Math.ceil(c.seconds/60);
 useEffect(()=>{if(c)setMinutes(Math.max(1,Math.min(180,Math.ceil(c.seconds/60))));},[c?.seconds]);
 // Idle really is the current clock; running really is the saved countdown.
 // No fake decorative loop when paused, and no ticking work on a hidden page.
 useEffect(()=>{
  setNow(Date.now());if(!visible||c&&!running)return;
  const tick=setInterval(()=>setNow(Date.now()),250);return()=>clearInterval(tick);
 },[visible,!!c,running,c?.endsAt]);
 useEffect(()=>{if(ready&&c?.endsAt!==undefined&&remaining<=0)save(current=>completeCountdown(current,c.endsAt!,Date.now()));},[remaining,c?.endsAt,ready]);
 function start(value:number){try{const time=Date.now();startCountdown(data,value,time);setNow(time);save(current=>current.countdown?.endsAt!==undefined&&countdownRemaining(current.countdown,time)>0?{}:startCountdown(current,value,time));setError('');}catch(e){setError(e instanceof Error?e.message:'计时未开始。');}}
 const complete=!!c&&c.seconds>0&&c.remainingMs===0&&!running;
 const wall=new Date(now),clockText=[wall.getHours(),wall.getMinutes(),wall.getSeconds()].map(n=>String(n).padStart(2,'0')).join(':');
 return <section className="card in-app-countdown210" ref={ref}>
  <h2><Clock3 size={20}/>时钟与倒计时</h2>
  <div className="countdown-display-hf3" data-idle={!c}>
   <AnalogClock source={c?{kind:'countdown',durationMs:c.seconds*1000,remainingMs:c.remainingMs,endsAt:c.endsAt}:{kind:'clock'}} size={160} variant="reminder"/>
   <div className="countdown-readout-hf3"><small className="timer-phase-hf3">{!c?'当前时间':complete?'已结束':running?'剩余时间':'已暂停'}</small><div className="countdown-value210" role="timer" aria-label={c?'倒计时剩余时间':'当前时间'} aria-live="off">{c?remainingText(remaining):clockText}</div><p>{c?`${Math.round(c.seconds/60)} 分钟倒计时`:'慢慢来，我在这里'}</p></div>
  </div>
  {complete&&<p className="countdown-done-hf3" role="status">时间到了，歇一小会儿吧。</p>}
  <div className="countdown-setting210"><label>分钟<input type="number" min={1} max={180} step={1} aria-label="倒计时分钟" value={minutes} disabled={running} onChange={e=>setMinutes(Math.max(0,Math.min(180,Math.round(Number(e.target.value)))))} /></label><span>拖动刻度，松手开始</span></div>
  <CountdownRuler value={minutes} onChange={setMinutes} onCommit={start} disabled={!ready||running}/>
  <div className="countdown-actions210">{running?<button className="primary" disabled={!ready} onClick={()=>{const end=c!.endsAt!;save(current=>pauseCountdown(current,end,Date.now()));setNow(Date.now());}}><Pause size={17}/>暂停</button>:c&&remaining>0&&!changed?<button className="primary" disabled={!ready} onClick={()=>{setNow(Date.now());save(current=>resumeCountdown(current));}}><Play size={17}/>继续</button>:<button className="primary" disabled={!ready||minutes<1} onClick={()=>start(minutes)}><Play size={17}/>{changed?'按新时长开始':'开始倒计时'}</button>}{c&&<button className="soft-button" disabled={!ready} onClick={()=>{if(!running||confirm('停止并重置当前倒计时？'))save({countdown:undefined});}}><RotateCcw size={16}/>重置</button>}</div>
  {error&&<p role="alert">{error}</p>}
 </section>;
}
