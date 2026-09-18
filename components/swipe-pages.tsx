'use client';
import {Children,cloneElement,isValidElement,useEffect,useLayoutEffect,useRef,useState,type ReactElement,type ReactNode} from 'react';
import {animate,motion,useMotionValue,type AnimationPlaybackControls} from 'motion/react';
import {usePointerLifecycle} from '@/hooks/use-pointer-lifecycle';
import {prefersReducedMotion} from '@/lib/motion';
export const PAGE_ORDER=['home','chat','heart','calendar','notes','timers'];
export function SwipePages({active,onSelect,blocked,children}:{active:string;onSelect:(id:string)=>void;blocked:boolean;children:ReactNode}){
 const viewport=useRef<HTMLDivElement>(null),width=useRef(1),x=useMotionValue(0),motionControl=useRef<AnimationPlaybackControls|null>(null),live=useRef({active,onSelect,blocked});live.current={active,onSelect,blocked};
 const [visited,setVisited]=useState(()=>new Set([active]));const gesture=useRef<{id:number;startX:number;startY:number;at:number;lastX:number;lastT:number;speed:number;origin:number;claimed:boolean}|null>(null),pointers=useRef(new Set<number>());
 const skip='input,textarea,select,button,a,[contenteditable=true],[role=dialog],[role=slider],[data-no-swipe],.hero-gallery,.weather-forecast210,.calendar-grid,[data-memo-editor],.memo-editor';
 function finish(target:number,instant=false){motionControl.current?.stop();motionControl.current=animate(x,-target*width.current,instant||prefersReducedMotion()?{duration:0}:{type:'spring',stiffness:340,damping:34,mass:.9});}
 useLayoutEffect(()=>{
  const node=viewport.current;if(!node)return;
  // Motion is the sole owner of horizontal page position. Browser focus scrolling
  // must not add a second scrollLeft offset during an in-flight page transition.
  const resetViewportScroll=()=>{if(node.scrollLeft)node.scrollLeft=0;if(node.scrollTop)node.scrollTop=0;};
  const onScroll=(event:Event)=>{if(event.target===node)resetViewportScroll();};
  const sync=()=>{motionControl.current?.stop();gesture.current=null;pointers.current.clear();resetViewportScroll();width.current=node.clientWidth||1;x.set(-PAGE_ORDER.indexOf(live.current.active)*width.current);};
  sync();const resize=new ResizeObserver(sync);resize.observe(node);node.addEventListener('scroll',onScroll,{passive:true});
  const unsubscribe=x.on('change',v=>{resetViewportScroll();document.documentElement.style.setProperty('--page-progress210',String(Math.max(0,Math.min(PAGE_ORDER.length-1,-v/width.current))))});
  return()=>{resize.disconnect();unsubscribe();node.removeEventListener('scroll',onScroll);motionControl.current?.stop();document.documentElement.style.removeProperty('--page-progress210');};
 },[]);
 useLayoutEffect(()=>{document.documentElement.dataset.page210=active;setVisited(v=>new Set([...v,active]));if(!gesture.current?.claimed)finish(PAGE_ORDER.indexOf(active));},[active]);
 useEffect(()=>{if(blocked)cancel(true);},[blocked]);
 function cancel(instant=false){const g=gesture.current;gesture.current=null;pointers.current.clear();if(g&&viewport.current?.hasPointerCapture(g.id))viewport.current.releasePointerCapture(g.id);finish(PAGE_ORDER.indexOf(live.current.active),instant);}
 usePointerLifecycle(viewport,gesture,pointers,()=>cancel(true));
 return <div className="page-viewport210" ref={viewport} data-page={active} onPointerDown={e=>{
  pointers.current.add(e.pointerId);if(pointers.current.size>1){cancel();return;}
  const target=e.target as Element;if(!e.isPrimary||e.button!==0||blocked||target.closest(skip)||e.clientX<22||e.clientX>innerWidth-22||document.querySelector('[role=dialog]')||window.getSelection()?.type==='Range')return;
  const i=PAGE_ORDER.indexOf(live.current.active);setVisited(v=>new Set([...v,...PAGE_ORDER.slice(Math.max(0,i-1),i+2)]));gesture.current={id:e.pointerId,startX:e.clientX,startY:e.clientY,at:e.timeStamp,lastX:e.clientX,lastT:e.timeStamp,speed:0,origin:i,claimed:false};
 }} onPointerMove={e=>{const g=gesture.current;if(!g||g.id!==e.pointerId||pointers.current.size>1)return;const dx=e.clientX-g.startX,dy=e.clientY-g.startY;if(!g.claimed){if(Math.abs(dy)>10&&Math.abs(dy)>=Math.abs(dx)){gesture.current=null;return;}if(Math.abs(dx)<10||Math.abs(dx)<Math.abs(dy)*1.5)return;g.claimed=true;motionControl.current?.stop();e.currentTarget.setPointerCapture(e.pointerId);}e.preventDefault();const elapsed=e.timeStamp-g.lastT;if(elapsed>0)g.speed=(e.clientX-g.lastX)/elapsed;g.lastX=e.clientX;g.lastT=e.timeStamp;let offset=dx;if((g.origin===0&&dx>0)||(g.origin===PAGE_ORDER.length-1&&dx<0))offset=dx*.18;x.set(-g.origin*width.current+offset);}}
 onPointerUp={e=>{pointers.current.delete(e.pointerId);const g=gesture.current;gesture.current=null;if(!g||g.id!==e.pointerId)return;if(e.currentTarget.hasPointerCapture(e.pointerId))e.currentTarget.releasePointerCapture(e.pointerId);if(!g.claimed)return;const dx=e.clientX-g.startX,dy=e.clientY-g.startY;const shouldMove=Math.abs(dx)>width.current*.22||(Math.abs(dx)>32&&Math.abs(g.speed)>.45&&e.timeStamp-g.lastT<100);const target=Math.max(0,Math.min(PAGE_ORDER.length-1,g.origin+(shouldMove&&Math.abs(dx)>Math.abs(dy)*1.35?(dx<0?1:-1):0)));finish(target);if(target!==g.origin)live.current.onSelect(PAGE_ORDER[target]);}}
 onPointerCancel={e=>{pointers.current.delete(e.pointerId);cancel();}} onLostPointerCapture={()=>{if(gesture.current?.claimed)cancel();}}>
 <motion.div className="page-track210" style={{x}}>{Children.toArray(children).sort((a,b)=>PAGE_ORDER.indexOf((a as ReactElement<{value:string}>).props.value)-PAGE_ORDER.indexOf((b as ReactElement<{value:string}>).props.value)).map(child=>{if(!isValidElement(child))return child;const element=child as ReactElement<{value:string;className?:string;children?:ReactNode;keepMounted?:boolean;inert?:boolean;'aria-hidden'?:boolean}>;const id=element.props.value;if(!PAGE_ORDER.includes(id))return element;return <section key={id} id={`main-panel-${id}`} role="tabpanel" aria-labelledby={`main-tab-${id}`} data-slot="tabs-content" data-page-id={id} className={(element.props.className??'')+' page-slide210'} inert={active!==id} aria-hidden={active!==id}>{visited.has(id)||active===id?element.props.children:null}</section>;})}</motion.div></div>;
}
