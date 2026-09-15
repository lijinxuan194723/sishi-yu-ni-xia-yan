
'use client';
import {useEffect,useRef} from 'react';
const pages=['home','chat','heart','calendar','notes','timers'];
export function swipeDirection(dx:number,dy:number,elapsed:number){return elapsed<=800&&Math.abs(dx)>=64&&Math.abs(dx)>Math.abs(dy)*1.8?(dx<0?1:-1):0;}
export function usePageSwipe(tab:string,navigate:(page:string)=>void,blocked:boolean){
 const live=useRef({tab,navigate,blocked});live.current={tab,navigate,blocked};
 const previous=useRef(tab);useEffect(()=>{const direction=pages.indexOf(tab)>=pages.indexOf(previous.current)?'forward':'back';document.documentElement.dataset.pageDirection=direction;previous.current=tab;},[tab]);
 useEffect(()=>{
  let start:{x:number;y:number;t:number;id:number}|null=null;
  const down=(e:PointerEvent)=>{start=null;const target=e.target as HTMLElement;if(e.pointerType!=='touch'||!e.isPrimary||live.current.blocked||e.clientX<22||e.clientX>innerWidth-22||!target.closest('main')||target.closest('input,textarea,select,button,a,[role=dialog],[contenteditable=true],.hero-gallery,.weather-week,.calendar-grid,.memo-editor,.memo-canvas,[data-no-swipe]'))return;start={x:e.clientX,y:e.clientY,t:performance.now(),id:e.pointerId};};
  const cancel=()=>{start=null;};
  const up=(e:PointerEvent)=>{const s=start;start=null;if(!s||s.id!==e.pointerId||live.current.blocked||document.querySelector('[role=dialog]')||window.getSelection()?.type==='Range')return;const direction=swipeDirection(e.clientX-s.x,e.clientY-s.y,performance.now()-s.t),index=pages.indexOf(live.current.tab),next=pages[index+direction];if(direction&&next)live.current.navigate(next);};
  document.addEventListener('pointerdown',down,{passive:true});document.addEventListener('pointerup',up,{passive:true});document.addEventListener('pointercancel',cancel,{passive:true});
  return()=>{document.removeEventListener('pointerdown',down);document.removeEventListener('pointerup',up);document.removeEventListener('pointercancel',cancel);};
 },[]);
}
