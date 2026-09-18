'use client';
import {KeyRound} from 'lucide-react';
import {MemoBoard} from '@/components/memo-board';
import type {Data} from '@/lib/companion';
export function Journal({data,text,setText}:{data:Data;ready:boolean;save:(patch:Partial<Data>|((current:Data)=>Partial<Data>))=>void;today:string;text:string;setText:(text:string)=>void;openMessage:(index:number)=>void}){
 return <div className="luke-journal"><section className="luke-journal-note" aria-label="夏彦的手记寄语">
  <img src="/images/luke-today.webp" alt="" width={46} height={34}/><div><span><KeyRound size={14} aria-hidden="true"/> 夏彦与你 · 线索手记</span><p>华生，把今天值得记住的小事留在这里。</p><small title="同人创作；写下的手记不会自动发送给聊天模型。">同人寄语</small></div>
 </section><MemoBoard legacyNotes={data.notes} draftText={text} onDraftUsed={()=>setText('')}/></div>;
}
