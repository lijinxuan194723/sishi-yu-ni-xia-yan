'use client';
import {useRef,useState} from 'react';
import {BookHeart,Check,CheckSquare2,ChevronLeft,ChevronRight,Copy,Download,Folder,PenLine,Pin,Star,Trash2} from 'lucide-react';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from './ui/dialog';
import {memoDisplayTitle,type MemoDocument} from '@/lib/memos';
import styles from './memo-details.module.css';
export type MemoAction={kind:'edit'|'pin'|'star'|'copy'|'export'|'select'|'trash'|'move';folderId?:string|null};
export function MemoActions({note,folders,onAction,onDone}:{note:MemoDocument;folders:{id:string;name:string}[];onAction:(action:MemoAction)=>void;onDone:()=>void}){
 const [open,setOpen]=useState(true),[page,setPage]=useState<'main'|'move'|'trash'>('main');
 const pending=useRef<MemoAction|null>(null);
 function choose(action:MemoAction){
  // Clipboard and system document pickers must retain the original user activation.
  if(action.kind==='copy'||action.kind==='export')onAction(action);else pending.current=action;
  setOpen(false);
 }
 return <Dialog open={open} onOpenChange={setOpen} onOpenChangeComplete={visible=>{
  if(visible)return;const action=pending.current;pending.current=null;if(action)onAction(action);onDone();
 }}>
  <DialogContent data-memo-actions="true" className={styles.sheet} overlayClassName={styles.backdrop}>
   <header className={styles.sheetHeader}>
    <div className={styles.handle} aria-hidden="true"/>
    <span className={styles.eyebrow}><BookHeart size={14}/> 珍藏的小事</span>
    <DialogTitle>{page==='trash'?'移到回收站？':page==='move'?'放进哪本手记？':memoDisplayTitle(note)}</DialogTitle>
    <DialogDescription>{page==='trash'?'内容不会立即删除，可以在笔记本的回收站恢复。':page==='move'?'选择笔记本，文字和原来的时间都会保留。':'长按或点右侧更多，都可以整理这条手记。'}</DialogDescription>
   </header>
   <div className={styles.sheetBody}>
    {page==='main'?<>
     <button className={styles.primaryAction} onClick={()=>choose({kind:'edit'})}><PenLine size={19}/><span>继续写<small>打开这条手记</small></span><ChevronRight size={17}/></button>
     <div className={styles.actionPair}>
      <button aria-pressed={!!note.pinnedAt} onClick={()=>choose({kind:'pin'})}><Pin size={18}/>{note.pinnedAt?'取消置顶':'置顶'}</button>
      <button aria-pressed={note.starred} onClick={()=>choose({kind:'star'})}><Star size={18} fill={note.starred?'currentColor':'none'}/>{note.starred?'取消加星':'加星'}</button>
     </div>
     <div className={styles.actionGroup}>
      <button onClick={()=>setPage('move')}><Folder size={18}/><span>移动到笔记本</span><ChevronRight size={16}/></button>
      <button onClick={()=>choose({kind:'copy'})}><Copy size={18}/><span>复制文字</span></button>
      <button onClick={()=>choose({kind:'export'})}><Download size={18}/><span>导出 Markdown</span></button>
      <button onClick={()=>choose({kind:'select'})}><CheckSquare2 size={18}/><span>选择多条手记</span></button>
     </div>
     <button className={styles.dangerAction} onClick={()=>setPage('trash')}><Trash2 size={18}/>移到回收站</button>
    </>:page==='move'?<div className={styles.actionGroup}>
      <button onClick={()=>setPage('main')}><ChevronLeft size={18}/>返回操作</button>
      {[{id:'',name:'默认笔记本'},...folders].map(folder=><button key={folder.id} onClick={()=>choose({kind:'move',folderId:folder.id||null})}><Folder size={18}/><span>{folder.name}</span>{(note.folderId||'')===folder.id&&<Check size={17}/>}</button>)}
     </div>:<div className={styles.confirmBox}><p>“{memoDisplayTitle(note)}”</p><button className={styles.dangerAction} data-haptic="confirm" onClick={()=>choose({kind:'trash'})}><Trash2 size={18}/>确认移到回收站</button><button className={styles.cancel} onClick={()=>setPage('main')}>保留手记</button></div>}
   </div>
   {page!=='trash'&&<button className={styles.cancel} onClick={()=>setOpen(false)}>取消</button>}
  </DialogContent>
 </Dialog>;
}
