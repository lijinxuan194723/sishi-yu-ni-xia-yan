'use client';
import {useEffect,useRef,useState} from 'react';
import type {Data} from './companion';
import {loadDurable,saveDurable,replaceDurable,requestDurability,type LoadedData} from './durable-store';
export function useLocalData(initial:Data){
 const [data,setData]=useState(initial),[ready,setReady]=useState(false),[error,setError]=useState(''),[status,setStatus]=useState('正在读取本地记忆'),[tick,setTick]=useState(0);
 const saved=useRef<LoadedData|null>(null),live=useRef(data),active=useRef(true),serial=useRef(Promise.resolve()),timer=useRef<ReturnType<typeof setTimeout>|null>(null),restoring=useRef(false),channel=useRef<BroadcastChannel|null>(null),source=useRef('');live.current=data;
 function flush(value:Data){
  if(!saved.current||restoring.current)return Promise.resolve();
  const write=async()=>{
   if(!saved.current||restoring.current)return;
   try{
    const next=await saveDurable(value,saved.current);saved.current=next;
    channel.current?.postMessage({revision:next.revision,source:source.current});
    if(active.current&&live.current===value){setStatus('已保存在本地');setError('');}
   }catch(e){if(active.current){setStatus('保存未完成');setError(e instanceof Error?e.message:'保存失败，请导出完整备份后重试。');}}
  };
  serial.current=serial.current.then(write,write);return serial.current;
 }
 useEffect(()=>{
  active.current=true;source.current=crypto.randomUUID();
  void loadDurable(initial).then(value=>{if(!active.current)return;saved.current=value;live.current=value.data;setData(value.data);setReady(true);setStatus('已保存在本地');void requestDurability();}).catch(e=>{if(active.current){setError(e instanceof Error?e.message:'原有存档无法读取，未覆盖。');setStatus('本地记忆未载入');}});
  try{channel.current=new BroadcastChannel('luke-companion-data-v205');channel.current.onmessage=event=>{if(event.data?.source!==source.current&&saved.current&&event.data?.revision>saved.current.revision){setReady(false);setError('另一个页面更新了存档。请先导出本页备份，再重新载入。');setStatus('检测到另一页面的更改');}};}catch{}
  const leave=()=>{if(timer.current)clearTimeout(timer.current);void flush(live.current);};
  const hidden=()=>{if(document.hidden)leave();};window.addEventListener('pagehide',leave);document.addEventListener('visibilitychange',hidden);
  return()=>{active.current=false;leave();channel.current?.close();channel.current=null;window.removeEventListener('pagehide',leave);document.removeEventListener('visibilitychange',hidden);};
 },[]);
 useEffect(()=>{
  if(!ready||!saved.current||restoring.current)return;
  if(data===saved.current.data){setStatus('已保存在本地');return;}
  setStatus('正在保存');
  const previous=saved.current.data;
  const draftOnly=Object.keys(data).every(key=>key==='draft'||data[key as keyof Data]===previous[key as keyof Data]);
  timer.current=setTimeout(()=>{timer.current=null;void flush(data);},draftOnly?140:0);
  return()=>{if(timer.current){clearTimeout(timer.current);timer.current=null;}};
 },[data,ready,tick]);
 useEffect(()=>{const warn=(e:BeforeUnloadEvent)=>{if(ready&&saved.current?.data!==live.current){e.preventDefault();e.returnValue='';}};window.addEventListener('beforeunload',warn);return()=>window.removeEventListener('beforeunload',warn);},[ready]);
 const save=(patch:Partial<Data>|((current:Data)=>Partial<Data>))=>{if(ready)setData(current=>{const next=typeof patch==='function'?patch(current):patch;return Object.keys(next).length?{...current,...next}:current;});};
 function reload(){if(window.confirm('重新载入前请先保存正在编辑的内容。继续吗？'))window.location.reload();}
 async function restore(value:Data){
  if(timer.current){clearTimeout(timer.current);timer.current=null;}restoring.current=true;
  try{await serial.current;const loaded=await replaceDurable(value);saved.current=loaded;live.current=loaded.data;setData(loaded.data);setReady(true);setError('');setStatus('已保存在本地');channel.current?.postMessage({revision:loaded.revision,source:source.current});}
  finally{restoring.current=false;}
 }
 return {data,setData,restore,ready,error,setError,status,save,reload,retry:()=>{if(ready)setTick(t=>t+1);else window.location.reload();}};
}
