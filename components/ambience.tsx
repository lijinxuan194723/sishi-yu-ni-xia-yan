'use client';
import {memo,useMemo,useEffect,useLayoutEffect,useState,useRef,type CSSProperties} from 'react';
import {flushSync} from 'react-dom';
import {seasonAlbums} from '@/lib/season-albums';
import {syncNativeAppearance} from '@/lib/interaction-feedback';
import {swapPhoto} from '@/lib/photo-transition';
import {Flower2,Leaf,Snowflake,Sparkles,Sunrise,Sun,Sunset,Moon} from 'lucide-react';
import {ambienceAt,seasonPhotos,seasons,autoAppearance,readAppearance,type Appearance} from '@/lib/ambience';
export function useAmbience(paused=false,preview=false){
 const [options,setOptionsState]=useState<Appearance>(autoAppearance),[loaded,setLoaded]=useState(false),[appearanceError,setAppearanceError]=useState('');
 useEffect(()=>{try{setOptionsState(readAppearance(JSON.parse(localStorage.getItem('luke-appearance-v1')||'null')));}catch{}setLoaded(true);},[]);
 function setOptions(next:Appearance){setOptionsState(next);try{localStorage.setItem('luke-appearance-v1',JSON.stringify(next));syncNativeAppearance();setAppearanceError('');}catch{setAppearanceError('外观已切换，但当前设备未能保存设置。');}}
 const [scene,setScene]=useState<ReturnType<typeof ambienceAt>|null>(null);
 useEffect(()=>{
  if(!loaded||paused)return;
  let alive=true,request=0;
  const update=async()=>{
   const id=++request;
   let current=options;
   try{if(!preview)current=readAppearance(JSON.parse(localStorage.getItem('luke-appearance-v1')||'null'));}catch{}
   const next=ambienceAt(new Date(),current);
   const target=preview?document.querySelector<HTMLElement>('.settings'):document.documentElement;
   if(!target)return;
   // Always yield before flushSync, including the no-image-change path.
   try{await Promise.all([...new Set([seasonPhotos[next.season],...(!preview?[seasonAlbums[next.season][0]]:[])])].map(async src=>{const image=new Image();image.src=src;await image.decode();}));}
   catch{if(alive&&id===request)setAppearanceError('主题图片加载失败，已保留当前画面，请重试。');return;}
   if(!alive||id!==request)return;
   target.dataset.season=next.season;
   target.dataset.effects=current.effects===false?'off':'on';
   target.dataset.manual=current.period==='auto'?'false':'true';
   target.dataset.night=next.night>.5?'true':'false';
   if(!preview)window.LukeAndroid?.systemTheme?.(next.night>.5?'#202c37':({spring:'#f3fcf7',summer:'#f2fbff',autumn:'#fff5e5',winter:'#f8f9ff'})[next.season],next.night>.5);
   // Publish native colours before child readiness effects can reveal the page.
   flushSync(()=>setScene(next));
  };
  void update();
  const timer=setInterval(()=>{if(!document.hidden)void update();},30000);
  const wake=()=>{document.documentElement.dataset.paused=document.hidden?'true':'false';if(!document.hidden)void update();};
  document.addEventListener('visibilitychange',wake);window.addEventListener('focus',update);
  return()=>{alive=false;clearInterval(timer);document.removeEventListener('visibilitychange',wake);window.removeEventListener('focus',update);};
 },[options,loaded,paused,preview]);
 return {scene,options,setOptions,appearanceError};
}
export const Ambience=memo(function Ambience({scene}:{scene:ReturnType<typeof ambienceAt>|null}){
 const particles=useMemo(()=>{const icons=[Flower2,Sparkles,Leaf,Snowflake];return seasons.map((season,k)=>{const Icon=icons[k];return <div className={'season-particles particles-'+season} key={season}>{Array.from({length:28},(_,i)=><span key={i} style={{'--x':`${(7+i*37)%100}%`,'--delay':`${-i*3.7}s`,'--duration':`${12+(i%6)*4}s`,'--size':`${4+(i%5)*3}px`,'--drift':`${i%2?35:-45}px`} as CSSProperties}><Icon strokeWidth={1.2}/></span>)}</div>});},[]);
 return <><div className="ambience" data-season={scene?.season} aria-hidden="true">{['morning','day','evening','night'].map((name,i)=><div key={name} className={'light-layer light-'+name} style={{opacity:scene?.lights[i]??(i===1?1:0)}}/>)}{particles}</div><div className="scene-label">{scene&&(scene.night>.5?<Moon size={15}/>:scene.period==='傍晚'?<Sunset size={15}/>:['清晨','早上'].includes(scene.period)?<Sunrise size={15}/>:<Sun size={15}/>)}<span>{scene?`${scene.seasonName} · ${scene.period}`:'夏彦与你'}</span><time>{scene?.time}</time></div></>;
});

// Two permanent image nodes; rapid clicks queue behind the current fade.
export function SeasonPhoto({season,className="",src}:{season:typeof seasons[number];className?:string;src?:string}){
 const photo=src??seasonPhotos[season];
 const initialPhoto=useRef(photo);
 const first=useRef<HTMLImageElement>(null),second=useRef<HTMLImageElement>(null),initial=useRef(season),shown=useRef(photo),front=useRef(0),queue=useRef(Promise.resolve());
 useLayoutEffect(()=>{let cancelled=false;queue.current=queue.current.catch(()=>{}).then(async()=>{
  if(cancelled||shown.current===photo||!first.current||!second.current)return;
  const nodes=[first.current,second.current],position='center '+({spring:'40%',summer:'32%',autumn:'32%',winter:'40%'})[season];
  const changed=await swapPhoto(nodes[front.current],nodes[1-front.current],photo,position,()=>cancelled);
  if(changed){front.current=1-front.current;shown.current=photo;}
 }).catch(()=>{});return()=>{cancelled=true;};},[season,photo]);
 const position='center '+({spring:'40%',summer:'32%',autumn:'32%',winter:'40%'})[initial.current];
 return <div className={"season-photo "+className}><img ref={first} src={initialPhoto.current} alt="四季中的夏彦" style={{objectPosition:position}}/><img ref={second} alt="" style={{opacity:0,objectPosition:position}}/></div>;
}
