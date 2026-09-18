'use client';
import {useEffect,useRef,useState} from 'react';
/** Animation eligibility is independent from the timer's persisted clock. One observer
 * handles viewport visibility; ancestor attributes handle retained inactive pages. */
export function useVisibleMotion<T extends HTMLElement>(enabled=true){
 const ref=useRef<T>(null),[playing,setPlaying]=useState(false);
 useEffect(()=>{
  const node=ref.current;if(!node)return;
  let visible=false;const media=matchMedia('(prefers-reduced-motion: reduce)');
  // Decorative scene itself is aria-hidden; only its *ancestors* indicate a hidden page.
  const update=()=>setPlaying(enabled&&visible&&!document.hidden&&!media.matches&&document.documentElement.dataset.effects!=='off'&&!node.parentElement?.closest('[hidden],[inert],[aria-hidden="true"]'));
  const observer=typeof IntersectionObserver==='function'?new IntersectionObserver(([entry])=>{visible=entry.isIntersecting;update();},{threshold:[0,.02]}):null;
  if(observer)observer.observe(node);else{const b=node.getBoundingClientRect();visible=b.width>0&&b.height>0;}
  const attrs=new MutationObserver(update);attrs.observe(document.documentElement,{attributes:true,attributeFilter:['data-effects']});
  let ancestor=node.parentElement;while(ancestor&&ancestor!==document.documentElement){attrs.observe(ancestor,{attributes:true,attributeFilter:['inert','hidden','aria-hidden']});ancestor=ancestor.parentElement;}
  document.addEventListener('visibilitychange',update);media.addEventListener('change',update);update();
  return()=>{observer?.disconnect();attrs.disconnect();document.removeEventListener('visibilitychange',update);media.removeEventListener('change',update);};
 },[enabled]);
 return {ref,playing};
}
