'use client';
import {useEffect,useMemo,useRef,useState} from 'react';
import type {Data} from '@/lib/companion';
import {complete,type ModelConfig} from '@/lib/model';
import {archivePrompt,commitChapter,decodeExtraction,emptyArchive,mergeFacts,nextArchiveBatch} from '@/lib/memory-archive';
type Options={data:Data;config:ModelConfig;ready:boolean;busy:boolean;save:(patch:Partial<Data>|((current:Data)=>Partial<Data>))=>void};
const RETRY_KEY='luke-memory-retry-v205';
type Retry={fingerprint:string;attempt:number;at:number};
function readRetry():Retry {try{const p=JSON.parse(localStorage.getItem(RETRY_KEY)??'{}');if(typeof p.fingerprint==='string'&&Number.isInteger(p.attempt)&&p.attempt>=0&&Number.isFinite(p.at)&&p.at<Date.now()+600000)return p;}catch{}return {fingerprint:'',attempt:0,at:0};}
function storeRetry(value:Retry){try{localStorage.setItem(RETRY_KEY,JSON.stringify(value));}catch{/* Main records have a separate durable store. */}}
export function useConversationMemory(options:Options){
 const live=useRef(options);live.current=options;
 const task=useRef<AbortController|null>(null),sequence=useRef(0),alive=useRef(true),retry=useRef<Retry>({fingerprint:'',attempt:0,at:0});
 const [wake,setWake]=useState(0),[phase,setPhase]=useState<'idle'|'working'|'error'>('idle'),[notice,setNotice]=useState('');
 function cancel(){sequence.current++;task.current?.abort();task.current=null;if(alive.current)setPhase('idle');}
 async function run(manual=false){
  const {data,config,ready,busy,save}=live.current;
  if(task.current||!ready||busy||document.hidden||!navigator.onLine)return;
  const current=data.memoryArchive??emptyArchive();
  if(!manual&&!current.enabled)return;
  if(!config.baseUrl||!config.model||!config.key){setNotice('连接聊天模型后会自动整理。');return;}
  const batch=nextArchiveBatch(data.messages,current,manual);
  if(!batch){setNotice('新的聊天已整理。');return;}
  const id=++sequence.current,controller=new AbortController();task.current=controller;
  const initialArchive=data.memoryArchive;setPhase('working');setNotice('正在更新长期记忆');
  const timeout=setTimeout(()=>controller.abort('timeout'),60000);
  try{
   const at=new Date().toISOString();
   const output=batch.records.length?decodeExtraction(await complete(config,archivePrompt(batch,current.facts),controller.signal,2400),batch,at):{summary:'本段没有新的可记录事实。',facts:[]};
   if(controller.signal.aborted||id!==sequence.current||!alive.current)return;
   const latest=live.current.data;
   if(latest.memoryArchive!==initialArchive||!commitChapter(latest.messages,latest.memoryArchive,batch,output.summary,at,!manual)){setNotice('原文有更新，稍后继续整理。');return;}
   save(value=>{
    if(id!==sequence.current||value.memoryArchive!==initialArchive)return {};
    const next=commitChapter(value.messages,value.memoryArchive,batch,output.summary,at,!manual);
    return next?{memoryArchive:mergeFacts(next,output.facts)}:{};
   });
   retry.current={fingerprint:'',attempt:0,at:Date.now()+10000};storeRetry(retry.current);
   setPhase('idle');setNotice('长期记忆已更新');
  }catch(error){
   if(id!==sequence.current||!alive.current)return;
   const attempt=retry.current.fingerprint===batch.fingerprint?retry.current.attempt+1:1;
   const delay=Math.min(300000,15000*2**Math.min(attempt-1,5));
   retry.current={fingerprint:batch.fingerprint,attempt,at:Date.now()+delay};storeRetry(retry.current);
   setPhase('error');setNotice(`${controller.signal.aborted?'整理超时':error instanceof Error?error.message:'暂时无法更新'}，${Math.ceil(delay/1000)} 秒后自动重试。`);
  }finally{
   clearTimeout(timeout);if(task.current===controller)task.current=null;
   if(alive.current&&id===sequence.current){setPhase(value=>value==='working'?'idle':value);setWake(n=>n+1);}
  }
 }
 useEffect(()=>{
  alive.current=true;retry.current=readRetry();
  const resume=()=>{if(document.hidden||!navigator.onLine)cancel();else{setWake(n=>n+1);}};
  document.addEventListener('visibilitychange',resume);window.addEventListener('online',resume);window.addEventListener('offline',resume);window.addEventListener('focus',resume);
  return()=>{alive.current=false;sequence.current++;task.current?.abort();task.current=null;document.removeEventListener('visibilitychange',resume);window.removeEventListener('online',resume);window.removeEventListener('offline',resume);window.removeEventListener('focus',resume);};
 },[]);
 const {data,config,ready,busy}=options;
 useEffect(()=>{if(ready&&data.memoryArchive?.autoRevision!==1){options.save(value=>({memoryArchive:{...(value.memoryArchive??emptyArchive()),enabled:true,autoRevision:1}}));}},[ready,data.memoryArchive?.autoRevision]);
 useEffect(()=>{if(busy||!ready)cancel();},[busy,ready]);
 useEffect(()=>{cancel();},[config.baseUrl,config.model,config.key,config.fallback?.baseUrl,config.fallback?.model,config.fallback?.key]);
 useEffect(()=>{if(task.current)cancel();},[data.draft]);
 useEffect(()=>{if(data.memoryArchive?.enabled===false)cancel();},[data.memoryArchive?.enabled]);
 const batch=useMemo(()=>nextArchiveBatch(data.messages,data.memoryArchive),[data.messages,data.memoryArchive]);
 useEffect(()=>{
  if(!ready||busy||!data.memoryArchive?.enabled||!batch||!config.baseUrl||!config.model||!config.key||document.hidden||!navigator.onLine)return;
  if(retry.current.fingerprint&&retry.current.fingerprint!==batch.fingerprint)retry.current={fingerprint:'',attempt:0,at:0};
  const timer=setTimeout(()=>{void run();},Math.max(5000,retry.current.at-Date.now()));
  return()=>clearTimeout(timer);
 },[data.messages,data.memoryArchive,data.draft,ready,busy,wake,config.baseUrl,config.model,config.key,batch]);
 return {phase,notice,cancel,run:()=>{retry.current.at=0;return run(true);}};
}
export type MemoryWorker=ReturnType<typeof useConversationMemory>;
