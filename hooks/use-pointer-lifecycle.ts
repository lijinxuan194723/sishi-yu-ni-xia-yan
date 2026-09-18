'use client';
import {useEffect,useRef,type RefObject,type MutableRefObject} from 'react';
/** Window events may finish outside the component, and page visibility can end a
 * drag without a pointerup. Never interpret those interruptions as confirmation. */
export function usePointerLifecycle<T extends HTMLElement>(element:RefObject<T|null>,active:MutableRefObject<{id:number}|null>,pointers:MutableRefObject<Set<number>>,cancel:()=>void){
 const latest=useRef(cancel);latest.current=cancel;
 useEffect(()=>{
  const node=element.current;if(!node)return;
  const stop=()=>{if(active.current)latest.current();pointers.current.clear();};
  const end=(event:PointerEvent)=>{pointers.current.delete(event.pointerId);if(active.current?.id===event.pointerId)stop();};
  const down=(event:PointerEvent)=>{if(active.current&&active.current.id!==event.pointerId)stop();};
  const hidden=()=>{if(document.hidden)stop();};
  const concealed=()=>{if(node.closest('[hidden],[inert],[aria-hidden="true"]'))stop();};
  // Bubble-phase end handlers run AFTER React has committed a valid local release.
  window.addEventListener('pointerup',end);window.addEventListener('pointercancel',end);
  window.addEventListener('pointerdown',down,true);window.addEventListener('blur',stop);window.addEventListener('pagehide',stop);
  document.addEventListener('visibilitychange',hidden);
  const observer=new MutationObserver(concealed);let parent:HTMLElement|null=node;
  while(parent){observer.observe(parent,{attributes:true,attributeFilter:['hidden','inert','aria-hidden']});parent=parent.parentElement;}
  return()=>{observer.disconnect();window.removeEventListener('pointerup',end);window.removeEventListener('pointercancel',end);window.removeEventListener('pointerdown',down,true);window.removeEventListener('blur',stop);window.removeEventListener('pagehide',stop);document.removeEventListener('visibilitychange',hidden);pointers.current.clear();};
 },[]);
}
