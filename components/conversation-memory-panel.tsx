'use client';
import {useMemo,useState} from 'react';
import {BookHeart,Search,Archive,RefreshCw} from 'lucide-react';
import {emptyMemory,type Data} from '@/lib/companion';
import {emptyArchive,reconciledArchive,archiveThrough,nextArchiveBatch} from '@/lib/memory-archive';
import type {MemoryWorker} from '@/hooks/use-conversation-memory';
import './conversation-memory.css';
type Props={data:Data;ready:boolean;busy:boolean;status:string;worker:MemoryWorker;save:(patch:Partial<Data>|((current:Data)=>Partial<Data>))=>void;onChat:()=>void;onSource:(index:number)=>void;onBackup:()=>void};
export function ConversationMemoryPanel({data,ready,busy,status,worker,save,onChat,onSource,onBackup}:Props){
 const [query,setQuery]=useState(''),[count,setCount]=useState(8);
 const archive=data.memoryArchive??emptyArchive(),memory=data.memory??emptyMemory;
 const valid=useMemo(()=>reconciledArchive(data.messages,archive),[data.messages,data.memoryArchive]);
 const through=archiveThrough(valid),stale=archive.chapters.length-valid.chapters.length;
 const needle=query.normalize('NFKC').trim().toLowerCase();
 const found=useMemo(()=>archive.chapters.map((chapter,index)=>({chapter,index})).filter(({chapter})=>!needle||chapter.summary.normalize('NFKC').toLowerCase().includes(needle)).reverse(),[data.memoryArchive,needle]);
 const working=worker.phase==='working';
 return <div className="settings-stack conversation-memory" data-conversation-memory>
  <div className="memory-overview"><BookHeart size={23}/><div><strong>把我们的聊天留成章节</strong><span>原文 {data.messages.length} 条 · 有效章节 {valid.chapters.length} 篇 · 已整理至 {through} 条</span></div></div>
  <label className="memory-auto"><span><strong>自动整理长期记忆</strong><small>聊天空闲时，使用当前模型整理新的章节。</small></span><input type="checkbox" role="switch" aria-label="自动整理长期记忆" checked={archive.enabled} disabled={!ready} onChange={e=>{worker.cancel();const enabled=e.target.checked;save(current=>({memoryArchive:{...(current.memoryArchive??emptyArchive()),enabled}}));}}/></label>
  <p className="memory-disclosure">开启后会向已配置的模型发送待整理聊天，产生额外请求；不接入其他记忆云服务。原文保留在本机，回复按相关性读取旧片段。摘要可能遗漏或记错，可核对原文并固定重要约定。</p>
  {stale>0&&<p role="status" className="memory-disclosure">{stale} 篇章节的原文已变化，暂不用于回复，将从变化处重新整理。</p>}
  <div className="memory-commands"><button type="button" className="soft-button" disabled={!ready||busy||(!working&&!nextArchiveBatch(data.messages,archive,true))} onClick={()=>working?worker.cancel():void worker.run()}><RefreshCw size={16}/>{working?'取消整理':'整理下一章节'}</button><button type="button" className="soft-button" onClick={onChat}>查看完整聊天</button></div>
  {worker.notice&&<p role="status" className="memory-disclosure">{worker.notice}</p>}<small role="status">{status}</small>
  <label>希望他牢牢记住的事<textarea disabled={!ready||busy} maxLength={5000} rows={4} value={memory.pinned} placeholder="喜欢的称呼、重要约定、聊天边界" onChange={e=>{const pinned=e.target.value;save(current=>({memory:{...(current.memory??emptyMemory),pinned}}));}}/></label>
  <div className="memory-archive-title"><Archive size={18}/><h4>聊天章节</h4><small>{archive.chapters.length} 篇</small></div>
  <label className="memory-search"><Search size={17}/><input aria-label="搜索聊天记忆章节" value={query} placeholder="找一个话题或约定" onChange={e=>{setQuery(e.target.value);setCount(8);}}/></label>
  <div className="memory-chapters">{found.slice(0,count).map(({chapter:c,index})=><details key={`${c.from}-${c.fingerprint}`} data-stale={index>=valid.chapters.length}><summary><span>第 {index+1} 章<small>第 {c.from+1}—{c.to} 条聊天{index>=valid.chapters.length?' · 待重新整理':''}</small></span></summary><p>{c.summary}</p><div><small>{new Date(c.updatedAt).toLocaleDateString('zh-CN')}</small><button type="button" className="text-button" disabled={c.from>=data.messages.length} onClick={()=>onSource(c.from)}>查看对应原文</button></div></details>)}</div>
  {!found.length&&<p className="memory-empty">{needle?'没有匹配的章节。':'新的聊天会逐段整理在这里。'}</p>}
  {found.length>count&&<button type="button" className="soft-button" onClick={()=>setCount(n=>n+8)}>再看 8 篇</button>}
  <details className="memory-legacy"><summary>原有摘要{memory.summary?' · 已保留':''}</summary><label>长期记忆摘要<textarea disabled={!ready||busy} maxLength={14000} rows={5} value={memory.summary} onChange={e=>{const summary=e.target.value;save(current=>({memory:{...(current.memory??emptyMemory),summary,through:summary?(current.memory??emptyMemory).through:0}}));}}/></label></details>
  <button type="button" className="soft-button" onClick={onBackup}>导出完整聊天与记忆备份</button>
 </div>;
}
