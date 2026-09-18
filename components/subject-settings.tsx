'use client';
import {useEffect,useState} from 'react';
import {Plus,Pencil,Check,Trash2,Play} from 'lucide-react';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from './ui/dialog';
import {addSubject,renameSubject,removeSubject,type StudySubjects} from '@/lib/study-subjects';
export function SubjectSettings({open,onClose,value,onChange,locked,startAfterChoose=false,onStart}:{open:boolean;onClose:()=>void;value:StudySubjects;onChange:(v:StudySubjects)=>void;locked:boolean;startAfterChoose?:boolean;onStart?:(subject:string)=>void}){
 const [name,setName]=useState(''),[edit,setEdit]=useState(''),[error,setError]=useState('');
 useEffect(()=>{if(!open){setName('');setEdit('');setError('');}},[open]);
 function apply(fn:()=>StudySubjects,start=false){
  if(locked)return;
  try{const next=fn();onChange(next);setName('');setEdit('');setError('');
   if(start){const chosen=next.items.find(s=>s.id===next.selected);if(chosen){onStart?.(chosen.name);onClose();}}
  }catch(e){setError(e instanceof Error?e.message:'科目未能保存。');}
 }
 return <Dialog open={open} onOpenChange={v=>{if(!v)onClose();}}><DialogContent className="settings subjects-panel210">
  <DialogTitle>{startAfterChoose?'选个科目，开始专注':'学习科目'}</DialogTitle>
  <DialogDescription>{startAfterChoose?'选一个已有科目，或添加后直接开始。':'改名或删除不会删除已保存的学习记录。'}</DialogDescription>
  <form onSubmit={e=>{e.preventDefault();apply(()=>edit?renameSubject(value,edit,name):addSubject(value,name),startAfterChoose&&!edit);}} className="subject-editor210">
   <input aria-label="科目名称" maxLength={30} value={name} onChange={e=>setName(e.target.value)} placeholder="输入你的学习科目" required disabled={locked}/>
   <button type="submit" className="primary" disabled={locked||!name.trim()} aria-label={edit?'保存科目名称':startAfterChoose?'添加并开始计时':'添加科目'}>{edit?<Check size={17}/>:startAfterChoose?<Play size={17}/>:<Plus size={17}/>}</button>
  </form>
  {edit&&<button type="button" className="soft-button" onClick={()=>{setEdit('');setName('');}}>取消修改</button>}
  <div className="subject-list210">{value.items.map(s=><div key={s.id}>
   {startAfterChoose?<button type="button" className="subject-pick210" aria-label={`开始 ${s.name}`} disabled={locked} onClick={()=>apply(()=>({...value,selected:s.id}),true)}><span>{s.name}</span><Play size={15} aria-hidden="true"/></button>:<span>{s.name}</span>}
   <button type="button" className="round" aria-label={`修改科目 ${s.name}`} disabled={locked} onClick={()=>{setEdit(s.id);setName(s.name);}}><Pencil size={16}/></button>
   <button type="button" className="round" aria-label={`删除科目 ${s.name}`} disabled={locked} onClick={()=>{if(confirm(`删除科目“${s.name}”？历史学习记录仍保留。`))apply(()=>removeSubject(value,s.id));}}><Trash2 size={16}/></button>
  </div>)}</div>
  {locked&&<p>正在读取记录或已有学习正在进行，请先返回查看。</p>}{error&&<p role="alert">{error}</p>}
 </DialogContent></Dialog>;
}
