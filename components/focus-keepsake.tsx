'use client';
import {memo,useEffect,useRef,useState} from 'react';
import {KeyRound} from 'lucide-react';
/** One small composited pendant; its animation never drives the study clock. */
export const FocusKeepsake=memo(function FocusKeepsake({running}:{running:boolean}){
 const ref=useRef<HTMLDivElement>(null);const [visible,setVisible]=useState(true);
 useEffect(()=>{
  let inView=true;const update=()=>setVisible(inView&&!document.hidden);
  const observer=typeof IntersectionObserver==='undefined'?null:new IntersectionObserver(([entry])=>{inView=entry.isIntersecting;update();});
  if(ref.current)observer?.observe(ref.current);document.addEventListener('visibilitychange',update);update();
  return()=>{observer?.disconnect();document.removeEventListener('visibilitychange',update);};
 },[]);
 return <div ref={ref} className="focus-keepsake" data-playing={running&&visible} aria-hidden="true"><div className="focus-keepsake-pendant"><span className="focus-keepsake-thread"/><img src="/images/luke-nap.png" alt="" width="42" height="42" draggable={false}/><KeyRound size={15}/></div></div>;
});
