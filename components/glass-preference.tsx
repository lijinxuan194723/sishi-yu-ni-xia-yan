'use client';
import {useEffect,useState} from 'react';
export const GLASS_KEY='luke-glass-headers-v209';
const EVENT='luke-glass-headers-change';
function read(){return localStorage.getItem(GLASS_KEY)!=='off';}
export function useGlassPreference(){useEffect(()=>{const apply=()=>{try{document.documentElement.dataset.glass210=read()?'on':'off';}catch{document.documentElement.dataset.glass210='off';}};const storage=(e:StorageEvent)=>{if(e.key===null||e.key===GLASS_KEY)apply();};apply();window.addEventListener(EVENT,apply);window.addEventListener('storage',storage);return()=>{window.removeEventListener(EVENT,apply);window.removeEventListener('storage',storage);};},[]);}
export function GlassPreference(){const [on,setOn]=useState(true),[error,setError]=useState('');useEffect(()=>{try{setOn(read());}catch{setOn(false);setError('通透设置未能读取，使用实色。');}},[]);return <section className="preference-card"><label className="inline-label"><input type="checkbox" role="switch" aria-label="顶部通透效果" checked={on} onChange={e=>{try{localStorage.setItem(GLASS_KEY,e.target.checked?'on':'off');setOn(e.target.checked);window.dispatchEvent(new Event(EVENT));setError('');}catch{setError('外观设置未保存，原选择保留。');}}}/>顶部通透效果</label><small>系统要求减少透明度或提高对比度时，使用清晰的实色底。</small>{error&&<p role="alert">{error}</p>}</section>;}
