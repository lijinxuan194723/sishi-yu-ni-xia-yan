'use client';
import {useEffect,useRef,useState} from 'react';
import type {Data} from '@/lib/companion';
import {complete,type ModelConfig} from '@/lib/model';
import {archivePrompt,commitChapter,nextArchiveBatch,MAX_CHAPTER_CHARS} from '@/lib/memory-archive';
type Options={data:Data;config:ModelConfig;ready:boolean;busy:boolean;save:(patch:Partial<Data>|((current:Data)=>Partial<Data>))=>void};
export function useConversationMemory(options:Options){
 const live=useRef(options);live.current=options;
 const task=useRef<AbortController|null>(null),sequence=useRef(0),alive=useRef(true),cooldown=useRef(0);
 const [wake,setWake]=useState(0);
 const [phase,setPhase]=useState<'idle'|'working'|'error'>('idle'),[notice,setNotice]=useState('');
 function cancel(){sequence.current++;task.current?.abort();task.current=null;if(alive.current){setPhase('idle');setNotice('');}}
 async function run(manual=false){
  const {data,config,ready,busy,save}=live.current;
  if(task.current||!ready||busy||document.hidden)return;
  if(!config.baseUrl||!config.model||!config.key){setNotice('先在聊天模型中保存可用的连接。');return;}
  const batch=nextArchiveBatch(data.messages,data.memoryArchive,manual);
  if(!batch){setNotice('目前没有需要整理的新章节。');return;}
  const id=++sequence.current,controller=new AbortController();task.current=controller;
  const initialArchive=data.memoryArchive;setPhase('working');setNotice('正在整理聊天章节…');
  const timeout=setTimeout(()=>controller.abort('timeout'),45000);
  cooldown.current=Date.now()+60000;
  try{
   const summary=await complete(config,archivePrompt(batch),controller.signal,2200);
   if(controller.signal.aborted||id!==sequence.current||!alive.current)return;
   if(!summary.trim()||summary.trim().length>MAX_CHAPTER_CHARS)throw new Error('记忆整理结果过长或为空，原有章节未覆盖。');
   const at=new Date().toISOString();
   // The updater rechecks both sources and archive identity; stale work cannot overwrite a restore/edit.
   save(current=>{
    if(id!==sequence.current||current.memoryArchive!==initialArchive)return {};
    const next=commitChapter(current.messages,current.memoryArchive,batch,summary,at,!manual);
    return next?{memoryArchive:next}:{};
   });
   setNotice('本段已整理，保存状态见下方。');setPhase('idle');
  }catch(error){
   if(id!==sequence.current||!alive.current)return;
   setPhase('error');setNotice(controller.signal.aborted?'整理已超时，原有聊天和记忆未覆盖。':error instanceof Error?error.message:'整理失败，原有记录未覆盖。');
  }finally{clearTimeout(timeout);if(task.current===controller)task.current=null;}
 }
 useEffect(()=>{
  alive.current=true;const hidden=()=>{if(document.hidden)cancel();else setWake(n=>n+1);};document.addEventListener('visibilitychange',hidden);
  return()=>{alive.current=false;sequence.current++;task.current?.abort();task.current=null;document.removeEventListener('visibilitychange',hidden);};
 },[]);
 const {data,config,ready,busy}=options;
 useEffect(()=>{cancel();},[busy,ready,config.baseUrl,config.model,config.key,config.fallback?.baseUrl,config.fallback?.model,config.fallback?.key]);
 useEffect(()=>{if(!data.memoryArchive?.enabled)cancel();},[data.memoryArchive?.enabled]);
 useEffect(()=>{
  if(!ready||busy||!data.memoryArchive?.enabled||data.draft?.trim()||!nextArchiveBatch(data.messages,data.memoryArchive))return;
  const id=setTimeout(()=>{if(!document.hidden&&!live.current.data.draft?.trim())void run();},Math.max(12000,cooldown.current-Date.now()));
  return()=>clearTimeout(id);
 },[data.messages,data.memoryArchive,data.draft,ready,busy,wake,config.baseUrl,config.model,config.key]);
 return {phase,notice,cancel,run:()=>run(true)};
}
export type MemoryWorker=ReturnType<typeof useConversationMemory>;
