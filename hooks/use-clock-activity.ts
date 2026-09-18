'use client';
import {useLayoutEffect,useRef,useState} from 'react';
/** Visibility and reduced animation are different concerns for an actual clock. */
export function useClockActivity<T extends HTMLElement>(){
 const ref=useRef<T>(null),[state,setState]=useState({visible:false,smooth:false});
 useLayoutEffect(()=>{
  const node=ref.current;if(!node)return;
  const media=matchMedia('(prefers-reduced-motion: reduce)');let intersecting=true;
  const update=()=>{
   const bounds=node.getBoundingClientRect();
   const visible=intersecting&&!document.hidden&&!node.parentElement?.closest('[hidden],[inert],[aria-hidden="true"]')&&bounds.width>0&&bounds.height>0&&bounds.bottom>0&&bounds.top<innerHeight&&bounds.right>0&&bounds.left<innerWidth;
   const smooth=visible&&!media.matches&&document.documentElement.dataset.effects!=='off';
   setState(s=>s.visible===visible&&s.smooth===smooth?s:{visible,smooth});
  };
  const intersection=typeof IntersectionObserver==='function'?new IntersectionObserver(([item])=>{intersecting=item.isIntersecting;update();}):null;
  intersection?.observe(node);
  const attrs=new MutationObserver(update);attrs.observe(document.documentElement,{attributes:true,attributeFilter:['data-effects']});
  for(let n=node.parentElement;n&&n!==document.documentElement;n=n.parentElement)attrs.observe(n,{attributes:true,attributeFilter:['hidden','inert','aria-hidden']});
  const resize=new ResizeObserver(update);resize.observe(node);
  document.addEventListener('visibilitychange',update);window.addEventListener('pageshow',update);media.addEventListener('change',update);update();
  return()=>{intersection?.disconnect();resize.disconnect();attrs.disconnect();document.removeEventListener('visibilitychange',update);window.removeEventListener('pageshow',update);media.removeEventListener('change',update);};
 },[]);
 return {ref,...state};
}
