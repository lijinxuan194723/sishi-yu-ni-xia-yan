import {useEffect,useRef,type PointerEvent,type MouseEvent,type KeyboardEvent} from 'react';
import {longPressController} from '@/lib/long-press';
import {FEEDBACK_KEY,feedbackMode} from '@/lib/interaction-feedback';

/** Consume the release click before document-level button feedback or actions.
 * A new gesture/keyboard input cancels the guard immediately. */
function guardReleaseClick(){
 let timer:ReturnType<typeof setTimeout>;
 const clear=()=>{clearTimeout(timer);window.removeEventListener('click',consume,true);window.removeEventListener('pointerdown',clear,true);window.removeEventListener('keydown',clear,true);window.removeEventListener('pointerup',released,true);};
 const consume=(event:globalThis.MouseEvent)=>{event.preventDefault();event.stopImmediatePropagation();clear();};
 const released=()=>{clearTimeout(timer);timer=setTimeout(clear,350);};
 window.addEventListener('click',consume,true);
 window.addEventListener('pointerdown',clear,true);
 window.addEventListener('keydown',clear,true);
 window.addEventListener('pointerup',released,true);
 timer=setTimeout(clear,5000);
 return clear;
}
export function useMemoPress(open:(id:string)=>void){
 const latest=useRef(open);latest.current=open;
 const trusted=useRef(false),touch=useRef(false),release=useRef<(()=>void)|null>(null);
 const state=useRef<ReturnType<typeof longPressController>|null>(null);
 if(!state.current)state.current=longPressController(id=>{
  release.current?.();release.current=touch.current?guardReleaseClick():null;
  latest.current(id);
  try{if(trusted.current&&!document.hidden&&feedbackMode(localStorage.getItem(FEEDBACK_KEY))!=='off'){
   (window.LukeAndroid as unknown as {hapticEvent?:(kind:string)=>void})?.hapticEvent?.('selection');
  }}catch{}
 },{set:(fn,ms)=>setTimeout(fn,ms),clear:id=>clearTimeout(id as ReturnType<typeof setTimeout>),now:()=>performance.now()});
 const gesture=state.current;
 useEffect(()=>{const cancel=()=>gesture.cancel();document.addEventListener('scroll',cancel,true);document.addEventListener('visibilitychange',cancel);window.addEventListener('blur',cancel);
  return()=>{cancel();release.current?.();document.removeEventListener('scroll',cancel,true);document.removeEventListener('visibilitychange',cancel);window.removeEventListener('blur',cancel);};},[gesture]);
 return (id:string)=>({
  onPointerDown:(e:PointerEvent)=>{trusted.current=e.isTrusted;touch.current=e.pointerType!=='mouse';if(e.button!==0||e.pointerType==='mouse'){gesture.cancel();return;}gesture.start(id,e.pointerId,e.clientX,e.clientY,e.isPrimary);},
  onPointerMove:(e:PointerEvent)=>gesture.move(e.pointerId,e.clientX,e.clientY),
  onPointerUp:()=>gesture.cancel(),onPointerCancel:()=>gesture.cancel(),onPointerLeave:()=>gesture.cancel(),
  onClickCapture:(e:MouseEvent)=>{if(gesture.consume(id,e.detail===0)){e.preventDefault();e.stopPropagation();}},
  onContextMenu:(e:MouseEvent)=>{e.preventDefault();trusted.current=e.isTrusted;gesture.context(id);},
  onKeyDown:(e:KeyboardEvent)=>{if(e.key==='ContextMenu'||e.shiftKey&&e.key==='F10'){e.preventDefault();open(id);}}
 });
}
