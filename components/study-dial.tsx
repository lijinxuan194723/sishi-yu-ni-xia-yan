'use client';
import {useLayoutEffect,useRef,useState} from 'react';
import {Bird} from 'lucide-react';
import {FocusKeepsake} from './focus-keepsake';
import {useVisibleMotion} from '@/hooks/use-visible-motion';
import {fitClockDiameter,elapsedText} from '@/lib/analog-time';
import {AnalogClock} from './analog-clock';
export function MechanicalDial({seconds,running,subject,startedAt}:{seconds:number;running:boolean;subject:string;startedAt?:number}){
 const stage=useRef<HTMLDivElement>(null),readout=useRef<HTMLDivElement>(null),[{size,compact},setLayout]=useState({size:216,compact:false});const {ref,playing}=useVisibleMotion<HTMLDivElement>(running);
 useLayoutEffect(()=>{
  const node=stage.current;if(!node)return;const pane=node.closest<HTMLElement>('.section-scroll210'),card=node.closest<HTMLElement>('.study-timer');if(!pane||!card)return;
  const others=[...card.children].filter(e=>e!==node);let frame=0;
  const update=()=>{frame=0;if(!node.getClientRects().length)return;const s=getComputedStyle(card),p=getComputedStyle(pane);
   // Measuring summary only keeps the dial stable while the optional note expands.
   const above=others.reduce((v,e)=>v+(e.querySelector(':scope>button')??e).getBoundingClientRect().height,0);
   const inset=parseFloat(s.paddingTop)+parseFloat(s.paddingBottom)+parseFloat(s.borderTopWidth)+parseFloat(s.borderBottomWidth)+(parseFloat(s.rowGap)||0)*others.length;
   const free=pane.clientHeight-parseFloat(p.paddingTop)-parseFloat(p.paddingBottom)-above-inset;
   const compact=innerHeight<=650;
   const next=compact?Math.max(112,Math.floor(Math.min((node.clientWidth-12)*.48,free-4,168))):fitClockDiameter(node.clientWidth,free,readout.current?.getBoundingClientRect().height??76,300);
   setLayout(old=>old.size===next&&old.compact===compact?old:{size:next,compact});
  };
  const schedule=()=>{if(!frame)frame=requestAnimationFrame(update);};const ro=new ResizeObserver(schedule);ro.observe(pane);ro.observe(node);if(readout.current)ro.observe(readout.current);others.forEach(e=>ro.observe(e.querySelector(':scope>button')??e));update();
  return()=>{cancelAnimationFrame(frame);ro.disconnect();};
 },[]);
 return <div className="dial-stage210 dial-stage-hf3" data-compact={compact} ref={stage}>
  <AnalogClock source={{kind:'elapsed',elapsedMs:seconds*1000,...(running&&startedAt!==undefined?{startedAt}:{})}} size={size} className="luke-study-dial mechanical-dial210"/>
  <div className="focus-readout-hf3" ref={readout}>
   <div className="focus-clock" role="timer" aria-label="本次学习已用时间" aria-live="off">{elapsedText(seconds*1000)}</div>
   <div className="focus-status-hf3" ref={ref}><FocusKeepsake running={playing}/><span><small>{running?'正在专注':'准备开始'} · 正计时</small><span className="dial-peanut210"><Bird size={13} aria-hidden="true"/>花生也在这里</span></span></div>
  </div>
 </div>;
}
