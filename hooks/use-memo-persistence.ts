'use client';
import {useEffect,useRef,type MutableRefObject} from 'react';
import {MEMO_STORAGE_KEY,type MemoWorkspace} from '@/lib/memos';
export function useMemoPersistence({workspace,loaded,blocked,baseline,status,afterSaved}:{workspace:MemoWorkspace;loaded:boolean;blocked:MutableRefObject<boolean>;baseline:MutableRefObject<string|null>;status:(s:string)=>void;afterSaved?:()=>void}){const live=useRef({workspace,loaded,afterSaved,status});live.current={workspace,loaded,afterSaved,status};const timer=useRef<ReturnType<typeof setTimeout>|undefined>(undefined),dirty=useRef(false);
 function flush(){clearTimeout(timer.current);const current=live.current;if(!current.loaded||blocked.current||!dirty.current)return;const text=JSON.stringify(current.workspace);try{const observed=localStorage.getItem(MEMO_STORAGE_KEY);if(observed!==baseline.current){blocked.current=true;current.status('笔记已在其他位置更新，已停止覆盖。请先导出当前内容。');return;}localStorage.setItem(MEMO_STORAGE_KEY,text);baseline.current=text;dirty.current=false;current.status('已保存在本机');current.afterSaved?.();window.dispatchEvent(new Event('luke-memos-changed'));}catch{current.status('保存失败，文字仍在此页。请先导出备份，再重试。');}}
 useEffect(()=>{if(!loaded||blocked.current)return;dirty.current=JSON.stringify(workspace)!==baseline.current;if(!dirty.current)return;status('正在保存');clearTimeout(timer.current);timer.current=setTimeout(flush,140);return()=>clearTimeout(timer.current);},[workspace,loaded]);
 useEffect(()=>{const hide=()=>{if(document.hidden)flush();};const blur=()=>flush();document.addEventListener('visibilitychange',hide);window.addEventListener('pagehide',blur);return()=>{flush();document.removeEventListener('visibilitychange',hide);window.removeEventListener('pagehide',blur);};},[]);
 return {flush};
}
