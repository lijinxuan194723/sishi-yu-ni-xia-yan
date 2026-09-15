import {parseData,type Data} from './companion.ts';
export const MAIN_KEY='luke-companion-v1',STORE_MARKER='luke-storage-v205';
type Snapshot={revision:number;payload:string;at:string};
type RestoreJournal={before:Record<string,string>;after:Record<string,string>;revision:number;startedAt?:number;owner?:string};
const restoreOwner=typeof crypto!=='undefined'&&crypto.randomUUID?crypto.randomUUID():String(Math.random());
export type LoadedData={data:Data;revision:number;payload:string;backend:'indexeddb'|'legacy'};
let opening:Promise<IDBDatabase>|undefined;
const encoded=new WeakMap<object,string>();
function encode(value:unknown):string|undefined{if(value===undefined)return undefined;if(value&&typeof value==='object'){let text=encoded.get(value);if(text!==undefined)return text;text=Array.isArray(value)?'['+value.map(v=>encode(v)??'null').join(',')+']':'{'+Object.entries(value).filter(([,v])=>v!==undefined).map(([k,v])=>JSON.stringify(k)+':'+encode(v)).join(',')+'}';encoded.set(value,text);return text;}return JSON.stringify(value);}
/** Immutable sections are encoded only when changed, not for each draft keystroke. */
export function encodeSnapshot(data:Data){return encode(data)!;}
const hash=(text:string)=>{let h=2166136261;for(let i=0;i<text.length;i++)h=Math.imul(h^text.charCodeAt(i),16777619);return (h>>>0).toString(16)+':'+text.length;};
function database():Promise<IDBDatabase>{
 if(opening)return opening;
 opening=new Promise((resolve,reject)=>{
  if(typeof indexedDB==='undefined'){reject(Error('本机不支持数据库'));return;}
  const request=indexedDB.open('luke-companion-durable',1);let finished=false;
  const timeout=setTimeout(()=>{finished=true;opening=undefined;reject(Error('数据库打开超时，请重试；原存档没有改动。'));},10000);
  const fail=(e:Error)=>{if(finished)return;finished=true;clearTimeout(timeout);opening=undefined;reject(e);};
  request.onupgradeneeded=()=>{if(!request.result.objectStoreNames.contains('snapshots'))request.result.createObjectStore('snapshots');};
  request.onerror=()=>fail(request.error??Error('数据库无法打开'));
  request.onblocked=()=>fail(Error('请关闭另一个四时与你页面后重试'));
  request.onsuccess=()=>{if(finished){request.result.close();return;}finished=true;clearTimeout(timeout);const db=request.result;db.onversionchange=()=>{db.close();opening=undefined;};db.onclose=()=>{opening=undefined;};resolve(db);};
 });return opening;
}
async function record<T>(key:string):Promise<T|undefined>{const db=await database();return new Promise((resolve,reject)=>{const tx=db.transaction('snapshots','readonly'),request=tx.objectStore('snapshots').get(key);let result:T|undefined;request.onsuccess=()=>{result=request.result;};tx.oncomplete=()=>resolve(result);tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error??Error('读取中断'));});}
async function commit(payload:string,expected:number|null,restoring=false):Promise<Snapshot>{
 const db=await database();return new Promise((resolve,reject)=>{
  let tx:IDBTransaction;try{tx=db.transaction('snapshots','readwrite',{durability:'strict'});}catch{tx=db.transaction('snapshots','readwrite');}
  const store=tx.objectStore('snapshots');let next:Snapshot,error:Error|undefined;
  const journal=store.get('restore');journal.onsuccess=()=>{if(journal.result&&!restoring){error=Error('正在恢复备份，请稍后重试保存。');tx.abort();return;}const request=store.get('main');request.onsuccess=()=>{const old=request.result as Snapshot|undefined;if(expected!==null&&(old?.revision??0)!==expected){error=Error('另一页面已更新存档，未覆盖其内容。请先导出本页备份，再重新载入。');tx.abort();return;}next={revision:(old?.revision??0)+1,payload,at:new Date().toISOString()};if(old)store.put(old,'previous');store.put(next,'main');if(restoring)store.delete('restore');};};
  tx.oncomplete=()=>resolve(next!);tx.onerror=()=>reject(error??tx.error??Error('存档保存失败'));tx.onabort=()=>reject(error??tx.error??Error('存档事务已中止'));
 });
}
function mirror(value:Snapshot){
 let raw:string|null=null,current=false;try{raw=localStorage.getItem(MAIN_KEY);if(value.payload.length<=500000){localStorage.setItem(MAIN_KEY,value.payload);raw=value.payload;current=true;}}catch{/* Authoritative record is already committed. */}
 try{localStorage.setItem(STORE_MARKER,JSON.stringify({revision:value.revision,mirrorHash:raw===null?null:hash(raw),current}));}catch{}
}
const sensitive=/(?:api[-_]?key|token|secret|password|credential|authorization|auth[-_]?key)/i;
export const restorableSetting=(key:string)=>key.startsWith('luke-')&&key!==MAIN_KEY&&key!==STORE_MARKER&&!key.startsWith('luke-durable-')&&!key.startsWith('luke-memory-retry-')&&!sensitive.test(key);
function readSettings(){const values:Record<string,string>={};for(let i=0;i<localStorage.length;i++){const k=localStorage.key(i);if(k&&restorableSetting(k)){const raw=localStorage.getItem(k);if(raw!==null)values[k]=raw;}}return values;}
function writeSettings(values:Record<string,string>){const keys=Object.keys(readSettings());for(const k of keys)if(!(k in values))localStorage.removeItem(k);for(const [k,v] of Object.entries(values))if(restorableSetting(k))localStorage.setItem(k,v);}
async function clearRestore(){const db=await database();await new Promise<void>((resolve,reject)=>{const tx=db.transaction('snapshots','readwrite');tx.objectStore('snapshots').delete('restore');tx.oncomplete=()=>resolve();tx.onabort=tx.onerror=()=>reject(tx.error??Error('恢复记录无法保存'));});}
/** Recovery journal is in IndexedDB so a crash or localStorage quota cannot silently mix backups. */
export async function prepareSettingsRestore(after:Record<string,string>,revision:number){
 const before=readSettings(),clean=Object.fromEntries(Object.entries(after).filter(([k])=>restorableSetting(k)));
 const db=await database();await new Promise<void>((resolve,reject)=>{const tx=db.transaction('snapshots','readwrite'),store=tx.objectStore('snapshots'),req=store.get('main');let error:Error|undefined;req.onsuccess=()=>{if((req.result?.revision??0)!==revision){error=Error('存档已由其他页面更新，请重新载入后恢复。');tx.abort();return;}const r=store.get('restore');r.onsuccess=()=>{if(r.result){error=Error('另一个恢复操作尚未结束，请重新载入。');tx.abort();return;}store.put({before,after:clean,revision,startedAt:Date.now(),owner:restoreOwner} satisfies RestoreJournal,'restore');};};tx.oncomplete=()=>resolve();tx.onabort=tx.onerror=()=>reject(error??tx.error??Error('无法开始备份恢复'));});
 try{writeSettings(clean);}catch(error){try{writeSettings(before);await clearRestore();}catch{/* Recovered before startup on the next successful database open. */}throw error;}
}
async function repairRestore(ownFailure=false){const journal=await record<RestoreJournal>('restore');if(!journal)return;if(ownFailure&&journal.owner!==restoreOwner)throw Error('其他页面正在恢复备份。');if(!ownFailure&&journal.startedAt&&Date.now()-journal.startedAt<30000)throw Error('备份恢复正在进行，请稍后重新载入。');writeSettings(journal.before);await clearRestore();}
export async function loadDurable(initial:Data):Promise<LoadedData>{
 let raw:string|null=null,marker:any=null;try{raw=localStorage.getItem(MAIN_KEY);marker=JSON.parse(localStorage.getItem(STORE_MARKER)??'null');}catch{}
 let existing:Snapshot|undefined;
 try{await database();}catch(error){if(marker)throw Error('长期存档暂时无法打开，没有用旧副本覆盖新记录。请重试。');const data=raw?parseData(raw):initial;return {data,revision:0,payload:raw??encodeSnapshot(data),backend:'legacy'};}
 await repairRestore();existing=await record<Snapshot>('main');
 // A stale legacy mirror is never authoritative after migration, even if its marker was removed.
 if(existing){const data=parseData(existing.payload);return {data,payload:existing.payload,revision:existing.revision,backend:'indexeddb'};}
 if(marker&&!marker.current)throw Error('长期数据库缺失，请从完整备份恢复。没有以旧副本覆盖新记录。');
 const data=raw?parseData(raw):initial,payload=encodeSnapshot(data),saved=await commit(payload,0);mirror(saved);return {data,payload,revision:saved.revision,backend:'indexeddb'};
}
export async function saveDurable(data:Data,state:LoadedData):Promise<LoadedData>{
 const payload=encodeSnapshot(data);if(payload===state.payload)return {...state,data};
 if(state.backend==='legacy'){const raw=localStorage.getItem(MAIN_KEY);if(raw!==state.payload&&raw!==null)throw Error('另一页面修改了存档');localStorage.setItem(MAIN_KEY,payload);return {data,payload,revision:state.revision+1,backend:'legacy'};}
 const saved=await commit(payload,state.revision);mirror(saved);return {data,payload,revision:saved.revision,backend:'indexeddb'};
}
export async function replaceDurable(data:Data,settings?:Record<string,string>):Promise<LoadedData>{
 const clean=parseData(encodeSnapshot(data)),payload=encodeSnapshot(clean),old=await record<Snapshot>('main'),revision=old?.revision??0;
 if(settings)await prepareSettingsRestore(settings,revision);
 try{const saved=await commit(payload,revision,!!settings);mirror(saved);return {data:clean,payload,revision:saved.revision,backend:'indexeddb'};}
 catch(error){if(settings)await repairRestore(true);throw error;}
}
export async function requestDurability(){try{await navigator.storage?.persist?.();}catch{}}
