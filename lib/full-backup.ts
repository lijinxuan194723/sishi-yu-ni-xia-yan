import {SKILLS_KEY,parseSkillStore} from './chat-skills';
import {SUBJECTS_KEY,parseStudySubjects} from './study-subjects';
import {parseData,type Data} from './companion';
import {replaceDurable,restorableSetting,STORE_MARKER,encodeSnapshot} from './durable-store';
import {MEMO_STORAGE_KEY,parseMemoWorkspace} from './memos';

export const FULL_BACKUP_FORMAT='four-seasons-luke-full-backup';
export const FULL_BACKUP_VERSION=1 as const;
const MAIN_STORAGE_KEY='luke-companion-v1';
const MAX_TOTAL_CHARS=60_000_000;
const sensitiveKey=/(?:api[-_]?key|token|secret|password|credential|authorization|auth[-_]?key)/i;
const sensitiveField=/^(?:key|apiKey|api_key|token|secret|password|credential|authorization)$/i;

export type FullBackup={
 format:typeof FULL_BACKUP_FORMAT;
 version:typeof FULL_BACKUP_VERSION;
 exportedAt:string;
 storage:Record<string,string>;
 excludedSensitiveKeys:string[];
};

function scrub(value:unknown):unknown{
 if(Array.isArray(value))return value.map(scrub);
 if(value&&typeof value==='object'){
  const result:Record<string,unknown>={};
  for(const [key,item] of Object.entries(value as Record<string,unknown>)){
   if(sensitiveField.test(key))continue;
   result[key]=scrub(item);
  }
  return result;
 }
 return value;
}

function safeStorageValue(raw:string){
 try{return JSON.stringify(scrub(JSON.parse(raw)));}
 catch{return raw;}
}

export function buildFullBackup(mainData?:Data):FullBackup{
 const storage:Record<string,string>={},excludedSensitiveKeys:string[]=[];
 for(let i=0;i<localStorage.length;i++){
  const key=localStorage.key(i);
  if(!key||!key.startsWith('luke-'))continue;
  if(key!==MAIN_STORAGE_KEY&&!restorableSetting(key)&&!sensitiveKey.test(key))continue;
  if(sensitiveKey.test(key)){excludedSensitiveKeys.push(key);continue;}
  const raw=localStorage.getItem(key);
  if(raw===null)continue;
  if(key!==MAIN_STORAGE_KEY)storage[key]=safeStorageValue(raw);
 }
 if(mainData)storage[MAIN_STORAGE_KEY]=encodeSnapshot(mainData);
 else{
  const marker=JSON.parse(localStorage.getItem(STORE_MARKER)??'null');if(marker&&!marker.current)throw Error('请从应用内导出完整备份，不能导出过期副本。');
  const main=localStorage.getItem(MAIN_STORAGE_KEY);
  if(main!==null)storage[MAIN_STORAGE_KEY]=JSON.stringify(parseData(main));
 }
 return {format:FULL_BACKUP_FORMAT,version:FULL_BACKUP_VERSION,exportedAt:new Date().toISOString(),storage,excludedSensitiveKeys};
}

export function stringifyFullBackup(mainData?:Data){const text=JSON.stringify(buildFullBackup(mainData),null,2);if(new TextEncoder().encode(text).byteLength>60_000_000)throw Error('完整备份超过 60 MB，请先单独导出大型附件。');return text;}

export function isFullBackup(value:unknown):value is FullBackup{
 if(!value||typeof value!=='object'||Array.isArray(value))return false;
 const p=value as Partial<FullBackup>;
 return p.format===FULL_BACKUP_FORMAT&&p.version===FULL_BACKUP_VERSION&&typeof p.exportedAt==='string'&&!!p.storage&&typeof p.storage==='object'&&!Array.isArray(p.storage);
}

export function parseFullBackup(text:string):FullBackup{
 if(text.length>MAX_TOTAL_CHARS||new TextEncoder().encode(text).byteLength>60_000_000)throw Error('完整备份文件过大');
 const parsed:unknown=JSON.parse(text);
 if(!isFullBackup(parsed))throw Error('不是四时与你完整备份');
 const storage:Record<string,string>={};
 let total=0;
 for(const [key,value] of Object.entries(parsed.storage)){
  if(!key.startsWith('luke-')||sensitiveKey.test(key)||typeof value!=='string'||value.length>50_000_000)throw Error('完整备份包含无效数据');
  total+=key.length+value.length;if(total>MAX_TOTAL_CHARS)throw Error('完整备份文件过大');
  storage[key]=value;
 }
 const main=storage[MAIN_STORAGE_KEY];
 if(!main)throw Error('完整备份缺少主数据');
 parseData(main);
 if(storage[SKILLS_KEY])parseSkillStore(JSON.parse(storage[SKILLS_KEY]));
 if(storage[SUBJECTS_KEY])parseStudySubjects(JSON.parse(storage[SUBJECTS_KEY]));
 if(storage[MEMO_STORAGE_KEY])parseMemoWorkspace(storage[MEMO_STORAGE_KEY]);
 return {...parsed,storage,excludedSensitiveKeys:Array.isArray(parsed.excludedSensitiveKeys)?parsed.excludedSensitiveKeys.filter((x):x is string=>typeof x==='string').slice(0,100):[]};
}

export function fullBackupSummary(backup:FullBackup){
 const data=parseData(backup.storage[MAIN_STORAGE_KEY]);
 let memoCount=0,folderCount=0;
 if(backup.storage[MEMO_STORAGE_KEY]){const memos=parseMemoWorkspace(backup.storage[MEMO_STORAGE_KEY]);memoCount=memos.memos.length;folderCount=memos.folders.length;}
 return {messages:data.messages.length,tasks:data.tasks.length,legacyNotes:data.notes.length,memos:memoCount,folders:folderCount,storageKeys:Object.keys(backup.storage).length};
}

export async function restoreFullBackup(backup:FullBackup,restore?:(data:Data,settings:Record<string,string>)=>Promise<void>){
 const checked=parseFullBackup(JSON.stringify(backup)),data=parseData(checked.storage[MAIN_STORAGE_KEY]);
 const settings=Object.fromEntries(Object.entries(checked.storage).filter(([key])=>restorableSetting(key)).map(([key,raw])=>[key,safeStorageValue(raw)]));
 settings['luke-backup-confirmed']=String(Date.now());
 if(restore)await restore(data,settings);else await replaceDurable(data,settings);
}

export function parseLegacyMainBackup(text:string):Data{return parseData(text);}
