import {parseData,type Data} from './companion';
export const MAIN_KEY='luke-companion-v1',STORE_MARKER='luke-storage-v205';
type Snapshot={revision:number;payload:string;at:string};
export type LoadedData={data:Data;revision:number;payload:string;backend:'indexeddb'|'legacy'};
let opening:Promise<IDBDatabase>|undefined;
const hash=(text:string)=>{let h=2166136261;for(let i=0;i<text.length;i++)h=Math.imul(h^text.charCodeAt(i),16777619);return (h>>>0).toString(16)+':'+text.length;};
function database():Promise<IDBDatabase>{
 if(opening)return opening;
 opening=new Promise((resolve,reject)=>{
  if(typeof indexedDB==='undefined'){reject(Error('本机不支持数据库'));return;}
  const request=indexedDB.open('luke-companion-durable',1);
  request.onupgradeneeded=()=>{if(!request.result.objectStoreNames.contains('snapshots'))request.result.createObjectStore('snapshots');};
  request.onerror=()=>{opening=undefined;reject(request.error??Error('数据库无法打开'));};
  request.onblocked=()=>{opening=undefined;reject(Error('请关闭另一个四时与你页面后重试'));};
  request.onsuccess=()=>{const db=request.result;db.onversionchange=()=>{db.close();opening=undefined;};resolve(db);};
 });return opening;
}
async function snapshot():Promise<Snapshot|undefined>{const db=await database();return new Promise((resolve,reject)=>{const tx=db.transaction('snapshots','readonly'),request=tx.objectStore('snapshots').get('main');request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});}
async function commit(payload:string,expected:number|null):Promise<Snapshot>{
 const db=await database();return new Promise((resolve,reject)=>{
  let tx:IDBTransaction;try{tx=db.transaction('snapshots','readwrite',{durability:'strict'});}catch{tx=db.transaction('snapshots','readwrite');}
  const store=tx.objectStore('snapshots'),request=store.get('main');let next:Snapshot;let error:Error|undefined;
  request.onsuccess=()=>{const old=request.result as Snapshot|undefined;if(expected!==null&&(old?.revision??0)!==expected){error=Error('另一页面已更新存档，请重新载入，未覆盖其内容。');tx.abort();return;}next={revision:(old?.revision??0)+1,payload,at:new Date().toISOString()};if(old)store.put(old,'previous');store.put(next,'main');};
  request.onerror=()=>{error=Error('读取存档失败');};
  tx.oncomplete=()=>resolve(next!);tx.onerror=()=>reject(error??tx.error??Error('存档保存失败'));tx.onabort=()=>reject(error??tx.error??Error('存档事务已中止'));
 });
}
function mirror(value:Snapshot){
 let raw:string|null=null,current=false;try{raw=localStorage.getItem(MAIN_KEY);if(value.payload.length<=2000000){localStorage.setItem(MAIN_KEY,value.payload);raw=value.payload;current=true;}}catch{/* Full authoritative record is already committed to IndexedDB. */}
 try{localStorage.setItem(STORE_MARKER,JSON.stringify({revision:value.revision,mirrorHash:raw===null?null:hash(raw),current}));}catch{/* IndexedDB remains authoritative when localStorage is full. */}
}
export async function loadDurable(initial:Data):Promise<LoadedData>{
 let raw:string|null=null,marker:any=null;
 try{raw=localStorage.getItem(MAIN_KEY);marker=JSON.parse(localStorage.getItem(STORE_MARKER)??'null');}catch{}
 let existing:Snapshot|undefined;
 try{existing=await snapshot();}catch(error){if(marker)throw Error('长期存档暂时无法打开，请重试；没有用旧副本覆盖新记录。');const data=raw?parseData(raw):initial;return {data,revision:0,payload:raw??JSON.stringify(data),backend:'legacy'};}
 // A missing marker on an existing database does not mean an empty application.
 const externalLegacy=!!raw&&(!existing||(!marker&&raw!==existing.payload)||(marker?.mirrorHash&&hash(raw)!==marker.mirrorHash));
 if(externalLegacy||(!existing&&!marker)){const data=raw?parseData(raw):initial,payload=JSON.stringify(data),saved=await commit(payload,existing?.revision??0);mirror(saved);return {data,payload,revision:saved.revision,backend:'indexeddb'};}
 if(existing){const data=parseData(existing.payload);mirror(existing);return {data,payload:existing.payload,revision:existing.revision,backend:'indexeddb'};}
 if(marker?.current&&raw){const data=parseData(raw),saved=await commit(raw,0);mirror(saved);return {data,payload:raw,revision:saved.revision,backend:'indexeddb'};}
 throw Error('长期数据库缺失，未以旧副本覆盖。请从完整备份恢复。');
}
export async function saveDurable(data:Data,state:LoadedData):Promise<LoadedData>{
 const payload=JSON.stringify(data);if(payload===state.payload)return {...state,data};
 if(state.backend==='legacy'){if(localStorage.getItem(MAIN_KEY)!==state.payload&&localStorage.getItem(MAIN_KEY)!==null)throw Error('另一页面修改了存档');localStorage.setItem(MAIN_KEY,payload);return {data,payload,revision:state.revision+1,backend:'legacy'};}
 const saved=await commit(payload,state.revision);mirror(saved);return {data,payload,revision:saved.revision,backend:'indexeddb'};
}
export async function replaceDurable(data:Data):Promise<LoadedData>{const payload=JSON.stringify(parseData(JSON.stringify(data))),saved=await commit(payload,null);mirror(saved);return {data,payload,revision:saved.revision,backend:'indexeddb'};}
export async function requestDurability(){try{await navigator.storage?.persist?.();}catch{/* Not every WebView grants persistent-storage hints. */}}
