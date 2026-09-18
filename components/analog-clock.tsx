'use client';
import {useLayoutEffect,useRef} from 'react';
import {dialSample,dialRunning,type DialSource} from '@/lib/analog-time';
import {useClockActivity} from '@/hooks/use-clock-activity';
/** Actual center-pivot hands, not a tiny perimeter marker or a CSS spin loop.
 * One visible dial writes three SVG transforms. No per-frame React tree render.
 * Date.now anchors every sample to persisted timer state after tab/device sleep. */
export function AnalogClock({source,size=260,variant='focus',className=''}:{source:DialSource;size?:number;variant?:'focus'|'reminder'|'pomodoro';className?:string}){
 const {ref,visible,smooth}=useClockActivity<HTMLDivElement>();
 const second=useRef<SVGGElement>(null),minute=useRef<SVGGElement>(null),hour=useRef<SVGGElement>(null),progress=useRef<SVGCircleElement>(null),latest=useRef(source);
 latest.current=source;const running=dialRunning(source),key=source.kind==='clock'?'clock':source.kind==='elapsed'?`${source.kind}:${source.startedAt}:${source.elapsedMs}`:`${source.kind}:${source.endsAt}:${source.remainingMs}:${source.durationMs}`;
 useLayoutEffect(()=>{
  let frame=0,timer:ReturnType<typeof setTimeout>|undefined,alive=true;
  const paint=()=>{
   const sample=dialSample(latest.current,Date.now(),smooth);
   second.current?.setAttribute('transform',`rotate(${sample.second} 100 100)`);
   minute.current?.setAttribute('transform',`rotate(${sample.minute} 100 100)`);
   hour.current?.setAttribute('transform',`rotate(${sample.hour} 100 100)`);
   progress.current?.setAttribute('stroke-dasharray',`${sample.progress*100} 100`);
  };
  if(!visible)return;paint();
  const tick=()=>{if(!alive)return;paint();if(!visible||!running)return;if(smooth)frame=requestAnimationFrame(tick);else timer=setTimeout(tick,1000-(Date.now()%1000)+8);};
  if(visible&&running){if(smooth)frame=requestAnimationFrame(tick);else timer=setTimeout(tick,1000-(Date.now()%1000)+8);}
  return()=>{alive=false;cancelAnimationFrame(frame);clearTimeout(timer);};
 },[key,visible,smooth,running]);
 const clock=source.kind==='clock';
 return <div ref={ref} className={`analog-clock-hf3 ${className}`} data-clock-mode={source.kind} data-variant={variant} data-running={running} data-playing={running&&smooth} data-motion={running&&visible?(smooth?'smooth':'step'):'parked'} data-visible={visible} style={{width:size,height:size}}>
  <svg className="dial-face210 analog-face-hf3" viewBox="0 0 200 200" aria-hidden="true" focusable="false">
   <circle cx="100" cy="100" r="97" className="clock-rim-hf3"/>
   <circle cx="100" cy="100" r="93" className="clock-bezel-hf3"/>
   <circle cx="100" cy="100" r="88" className="clock-paper-hf3"/>
   {Array.from({length:60},(_,i)=><line key={i} x1="100" y1="14" x2="100" y2={i%5===0?'23':'18'} transform={`rotate(${i*6} 100 100)`} className={i%5===0?'major':'minor'}/>)}
   {[[100,37,clock?'12':'60'],[163,104,clock?'3':'15'],[100,170,clock?'6':'30'],[37,104,clock?'9':'45']].map(([x,y,label])=><text key={String(label)} x={x} y={y} className="clock-label-hf3" textAnchor="middle">{label}</text>)}
   <text x="100" y="64" className="clock-signature-hf3" textAnchor="middle">LUKE</text>
   <path d="M92 137h16m-12 5h8" className="clock-signature-mark-hf3"/>
   <circle ref={progress} cx="100" cy="100" r="95" className="dial-progress210" pathLength="100" strokeDasharray="0 100" transform="rotate(-90 100 100)"/>
   {clock&&<g ref={hour} className="clock-hand-hf3 clock-hour-hf3" data-hand="hour" transform="rotate(0 100 100)"><path d="M96.8 107L96.8 70Q100 64 103.2 70L103.2 107Z"/></g>}
   <g ref={minute} className="clock-hand-hf3 clock-minute-hf3" data-hand="minute" transform="rotate(0 100 100)"><path d={clock?'M98 109L98 43Q100 38 102 43L102 109Z':'M97.5 109L97.5 56Q100 51 102.5 56L102.5 109Z'}/></g>
   <g ref={second} className="dial-needle210 clock-hand-hf3 clock-second-hf3" data-hand="second" transform="rotate(0 100 100)"><path className="clock-second-outline-hf3" d="M100 20V116"/><path className="dial-pointer210" d="M100 20V116"/><circle cx="100" cy="119" r="3" className="clock-counterweight-hf3"/></g>
   <circle cx="100" cy="100" r="4.8" className="clock-hub-base-hf3"/><circle cx="100" cy="100" r="2.3" className="clock-hub-hf3"/>
  </svg>
 </div>;
}
