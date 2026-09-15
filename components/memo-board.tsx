'use client';

import {useEffect,useMemo,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import {MemoActions,type MemoAction} from './memo-actions';
import {useMemoPress} from '@/hooks/use-memo-press';
import {MotionPresence} from './motion-presence';
import {nextMemoBackLayer} from '@/lib/memo-navigation';
import {mergeFavoriteMemos} from '@/lib/recommendation-history';
import {FAVORITES_KEY,parseFavorites} from '@/lib/recommendation-favorites';
import {BookHeart,CheckSquare2,ChevronLeft,ChevronRight,Copy,Download,Eye,Folder,FolderPlus,Heart,Pin,Plus,Search,Star,Trash2,MoreHorizontal,CheckCircle2,Circle,X} from 'lucide-react';
import {
 MEMO_STORAGE_KEY,createMemo,createMemoFolder,emptyMemoWorkspace,memoDisplayTitle,memoExcerpt,memoInCategory,memoMatches,memoMoods,parseMemoWorkspace,sortMemos,workspaceCounts,
 type MemoCategory,type MemoDocument,type MemoMood,type MemoWorkspace,
} from '@/lib/memos';
import {saveBackupFile} from '@/lib/mobile';
import baseStyles from './memo-board.module.css';
import refinedStyles from './memo-board-refined.module.css';
const styles:Record<string,string>={...baseStyles};
for(const key of Object.keys(refinedStyles))styles[key]=[baseStyles[key],refinedStyles[key]].filter(Boolean).join(' ');

type LegacyNote={date:string;text:string;mood?:string};
const JOURNAL_MIGRATION_KEY='luke-journal-to-notes-v1';
const dtf=new Intl.DateTimeFormat('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',hour12:false});

function safeName(value:string){return value.replace(/[\\/:*?"<>|]/g,'-').replace(/\s+/g,' ').trim().slice(0,60)||'无标题笔记';}
function dateLabel(time:number){const d=new Date(time),n=new Date();if(d.toDateString()===n.toDateString())return new Intl.DateTimeFormat('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false}).format(d);return `${d.getMonth()+1}月${d.getDate()}日`;}
function noteMarkdown(note:MemoDocument){return `${note.title.trim()?`# ${note.title.trim()}\n\n`:''}${note.body}`.trimEnd();}

function Preview({text}:{text:string}){
 const lines=text.split(/\r?\n/),nodes:React.ReactNode[]=[];let code:string[]=[];let inCode=false;
 lines.forEach((line,i)=>{const key=`${i}-${line.slice(0,8)}`;if(line.trim().startsWith('```')){if(inCode){nodes.push(<pre key={key}><code>{code.join('\n')}</code></pre>);code=[];}inCode=!inCode;return;}if(inCode){code.push(line);return;}if(/^###\s+/.test(line))nodes.push(<h3 key={key}>{line.replace(/^###\s+/,'')}</h3>);else if(/^##\s+/.test(line))nodes.push(<h2 key={key}>{line.replace(/^##\s+/,'')}</h2>);else if(/^#\s+/.test(line))nodes.push(<h1 key={key}>{line.replace(/^#\s+/,'')}</h1>);else if(/^>\s?/.test(line))nodes.push(<blockquote key={key}>{line.replace(/^>\s?/,'')}</blockquote>);else if(/^[-*+]\s+\[[ xX]\]\s+/.test(line)){const done=/^[-*+]\s+\[[xX]\]/.test(line);nodes.push(<p key={key} className={styles.checkLine}><CheckSquare2 size={15}/><span>{line.replace(/^[-*+]\s+\[[ xX]\]\s+/,'')}</span>{done&&<small>完成</small>}</p>);}else if(/^[-*+]\s+/.test(line))nodes.push(<p key={key}>• {line.replace(/^[-*+]\s+/,'')}</p>);else nodes.push(line?<p key={key}>{line}</p>:<br key={key}/>);});
 if(code.length)nodes.push(<pre key="last-code"><code>{code.join('\n')}</code></pre>);
 return <div className={styles.preview}>{nodes.length?nodes:<span className={styles.previewEmpty}>还没有写下内容。</span>}</div>;
}

export function MemoBoard({legacyNotes=[]}:{legacyNotes?:LegacyNote[]}){
 const [workspace,setWorkspace]=useState<MemoWorkspace>(()=>emptyMemoWorkspace()),[loaded,setLoaded]=useState(false),[status,setStatus]=useState('正在读取笔记…');
 const [category,setCategory]=useState<MemoCategory>('all'),[query,setQuery]=useState(''),[activeId,setActiveId]=useState<string|null>(null);
 const [notebooksOpen,setNotebooksOpen]=useState(false),[creatingFolder,setCreatingFolder]=useState(false),[folderDraft,setFolderDraft]=useState('');
 const [moreOpen,setMoreOpen]=useState(false),[moveOpen,setMoveOpen]=useState(false),[moodOpen,setMoodOpen]=useState(false),[preview,setPreview]=useState(false);
 const [selected,setSelected]=useState<string[]>([]),[selecting,setSelecting]=useState(false);
 const rootRef=useRef<HTMLDivElement>(null),editorRef=useRef<HTMLElement>(null);
 const [actionId,setActionId]=useState<string|null>(null);
 const press=useMemoPress(id=>{if(category!=='trash'&&!selecting)setActionId(id);});
 function toggleSelection(id:string){setSelected(v=>v.includes(id)?v.filter(x=>x!==id):[...v,id]);}
 function deleteSelected(){if(!selected.length||!allowWrite()||!window.confirm('将选中的 '+selected.length+' 条笔记移到回收站？'))return;setWorkspace(v=>({...v,memos:v.memos.map(m=>selected.includes(m.id)?{...m,deletedAt:Date.now(),pinnedAt:null}:m)}));setSelected([]);setSelecting(false);}
 useEffect(()=>{if(!activeId)return;const viewport=window.visualViewport;const update=()=>{editorRef.current?.style.setProperty('--memo-height',(viewport?.height??window.innerHeight)+'px');editorRef.current?.style.setProperty('--memo-top',(viewport?.offsetTop??0)+'px');};update();viewport?.addEventListener('resize',update);viewport?.addEventListener('scroll',update);return()=>{viewport?.removeEventListener('resize',update);viewport?.removeEventListener('scroll',update);};},[activeId]);
 useEffect(()=>{if(!loaded||blocked.current)return;const sync=()=>{try{const favorites=parseFavorites(localStorage.getItem(FAVORITES_KEY)||'[]');setWorkspace(v=>mergeFavoriteMemos(v,favorites));}catch{setStatus('推荐收藏无法读取，原数据已保留。');}};sync();window.addEventListener('luke-favorites-changed',sync);return()=>window.removeEventListener('luke-favorites-changed',sync);},[loaded]);
 useEffect(()=>{const back=(event:Event)=>{if(event.defaultPrevented||!rootRef.current?.getClientRects().length)return;if(actionId){event.preventDefault();return;}const layer=nextMemoBackLayer({move:moveOpen,menu:moreOpen,mood:moodOpen,selection:selecting,creatingFolder,notebooks:notebooksOpen,preview,editor:!!activeId,search:!!query,category:category!=='all'});if(!layer)return;event.preventDefault();switch(layer){case 'move':setMoveOpen(false);break;case 'menu':setMoreOpen(false);break;case 'mood':setMoodOpen(false);break;case 'selection':setSelecting(false);setSelected([]);break;case 'creatingFolder':setCreatingFolder(false);break;case 'notebooks':setNotebooksOpen(false);break;case 'preview':setPreview(false);break;case 'editor':setActiveId(null);break;case 'search':setQuery('');break;case 'category':setCategory('all');break;}};window.addEventListener('luke-memo-back',back);return()=>window.removeEventListener('luke-memo-back',back);},[actionId,moveOpen,moreOpen,moodOpen,selecting,creatingFolder,notebooksOpen,preview,activeId,query,category]);
 const bodyRef=useRef<HTMLTextAreaElement>(null),blocked=useRef(false),rawBackup=useRef<string|null>(null);

 useEffect(()=>{try{const raw=localStorage.getItem(MEMO_STORAGE_KEY);rawBackup.current=raw;let next=parseMemoWorkspace(raw);if(localStorage.getItem(JOURNAL_MIGRATION_KEY)!=='1'&&legacyNotes.length){const ids=new Set(next.memos.map(m=>m.id));const migrated=legacyNotes.map((n,i)=>{const id=`journal-${n.date}-${i}`;const time=Date.parse(n.date+'T12:00:00');const mood=memoMoods.includes(n.mood as MemoMood)?n.mood as MemoMood:undefined;return {id,title:'',body:n.text,createdAt:Number.isFinite(time)?time:Date.now(),updatedAt:Number.isFinite(time)?time:Date.now(),pinnedAt:null,folderId:null,starred:false,deletedAt:null,...(mood?{mood}:{})} satisfies MemoDocument;}).filter(m=>!ids.has(m.id));if(migrated.length)next={...next,memos:[...migrated,...next.memos]};localStorage.setItem(JOURNAL_MIGRATION_KEY,'1');}setWorkspace(next);setStatus('已保存在本机');}catch{blocked.current=true;setWorkspace(emptyMemoWorkspace());setStatus('原笔记数据无法读取，已暂停自动写入。');}finally{setLoaded(true);}},[]);
 useEffect(()=>{if(!loaded||blocked.current)return;try{localStorage.setItem(MEMO_STORAGE_KEY,JSON.stringify(workspace));setStatus('已保存在本机');}catch{setStatus('保存失败，请先导出备份。');}},[workspace,loaded]);
 useEffect(()=>{setMoreOpen(false);setMoveOpen(false);setMoodOpen(false);setPreview(false);},[activeId]);

 const folders=useMemo(()=>new Map(workspace.folders.map(f=>[f.id,f.name])),[workspace.folders]);
 const counts=useMemo(()=>workspaceCounts(workspace),[workspace]);
 const visible=useMemo(()=>sortMemos(workspace.memos.filter(m=>memoInCategory(m,category)&&memoMatches(m,query,m.folderId?folders.get(m.folderId)??'':'')),category==='trash'),[workspace.memos,category,query,folders]);
 const pinned=category==='trash'?[]:visible.filter(m=>m.pinnedAt!==null),others=category==='trash'?visible:visible.filter(m=>m.pinnedAt===null);
 const active=workspace.memos.find(m=>m.id===activeId)??null;
 const categoryLabel=category==='all'?'全部笔记':category==='starred'?'加星笔记':category==='trash'?'回收站':folders.get(category.slice(7))??'全部笔记';

 function allowWrite(){if(!blocked.current)return true;if(!window.confirm('原笔记数据异常。继续会用新的笔记数据覆盖它，建议先导出原始数据。仍要继续吗？'))return false;blocked.current=false;return true;}
 function mutate(id:string,patch:Partial<MemoDocument>|((memo:MemoDocument)=>Partial<MemoDocument>),touch=true){if(!allowWrite())return;setWorkspace(current=>({...current,memos:current.memos.map(m=>m.id===id?{...m,...(typeof patch==='function'?patch(m):patch),...(touch?{updatedAt:Date.now()}:{} )}:m)}));}
 function addNote(){if(!allowWrite())return;const folderId=category.startsWith('folder:')?category.slice(7):null,note=createMemo(Date.now(),folderId);setWorkspace(current=>({...current,memos:[note,...current.memos]}));setCategory(folderId?`folder:${folderId}`:'all');setQuery('');setActiveId(note.id);}
 function addFolder(){if(!allowWrite())return;const name=folderDraft.trim();if(!name)return;const folder=createMemoFolder(name);setWorkspace(current=>({...current,folders:[...current.folders,folder]}));setFolderDraft('');setCreatingFolder(false);setCategory(`folder:${folder.id}`);setNotebooksOpen(false);}
 function removeFolder(id:string){if(!allowWrite()||!window.confirm(`删除笔记本“${folders.get(id)??''}”？其中的笔记会保留在默认笔记本。`))return;setWorkspace(current=>({...current,folders:current.folders.filter(f=>f.id!==id),memos:current.memos.map(m=>m.folderId===id?{...m,folderId:null,updatedAt:Date.now()}:m)}));if(category===`folder:${id}`)setCategory('all');}
 function trash(note:MemoDocument){mutate(note.id,{deletedAt:Date.now(),pinnedAt:null},false);setActiveId(null);}
 function restore(note:MemoDocument){mutate(note.id,{deletedAt:null},false);}
 function deleteForever(note:MemoDocument){if(!allowWrite()||!window.confirm(`永久删除“${memoDisplayTitle(note)}”？`))return;setWorkspace(current=>({...current,memos:current.memos.filter(m=>m.id!==note.id)}));setActiveId(null);}
 function move(note:MemoDocument,folderId:string|null){mutate(note.id,{folderId});setMoveOpen(false);setMoreOpen(false);}
 async function copy(note:MemoDocument){try{await navigator.clipboard.writeText(noteMarkdown(note));setStatus('已复制笔记内容');}catch{setStatus('复制失败，请检查剪贴板权限。');}setMoreOpen(false);}
 function exportOne(note:MemoDocument){saveBackupFile(`${safeName(memoDisplayTitle(note))}.md`,noteMarkdown(note));setMoreOpen(false);}
 function exportAll(){const raw=blocked.current&&rawBackup.current?rawBackup.current:JSON.stringify(workspace,null,2);saveBackupFile(`四时与你-全部笔记-${new Date().toISOString().slice(0,10)}.json`,raw);}
 function selectCategory(next:MemoCategory){setSelecting(false);setSelected([]);setCategory(next);setQuery('');setActiveId(null);setNotebooksOpen(false);}
 function insert(before:string,after=''){if(!active||preview)return;const el=bodyRef.current,start=el?.selectionStart??active.body.length,end=el?.selectionEnd??start;const body=active.body.slice(0,start)+before+active.body.slice(start,end)+after+active.body.slice(end);mutate(active.id,{body});requestAnimationFrame(()=>{if(!bodyRef.current)return;const pos=end+before.length+after.length;bodyRef.current.focus();bodyRef.current.setSelectionRange(pos,pos);});}

 function row(note:MemoDocument){return <div key={note.id} className={styles.noteRow} data-selected={selected.includes(note.id)&&selecting}>
  <button className={styles.noteSelect} data-memo-id={note.id} {...press(note.id)} aria-pressed={selecting?selected.includes(note.id):undefined} onClick={()=>selecting?toggleSelection(note.id):setActiveId(note.id)}>
   <div className={styles.noteTitle}>{selecting&&(selected.includes(note.id)?<CheckCircle2 size={21}/>:<Circle size={21}/>)}<strong>{memoDisplayTitle(note)}</strong><span>{note.pinnedAt!==null&&<Pin size={13}/>} {note.starred&&<Star size={13} fill="currentColor"/>}</span></div>
   <div className={styles.noteSummary}><span>{memoExcerpt(note,100)||'还没写正文，点开继续。'}</span></div>
   <div className={styles.noteMeta}><time>{dateLabel(note.updatedAt)}</time>{note.folderId&&<span><Folder size={11}/>{folders.get(note.folderId)}</span>}{note.mood&&<span className={styles.moodBadge}><Heart size={11}/>{note.mood}</span>}</div>
  </button>
  {category!=='trash'&&!selecting&&<button type="button" className={styles.rowMore} aria-label={`手记操作：${memoDisplayTitle(note)}`} onClick={()=>setActionId(note.id)}><MoreHorizontal size={20}/></button>}
  {category==='trash'&&<div className={styles.trashActions}><button onClick={()=>restore(note)}>恢复</button><button onClick={()=>deleteForever(note)}>永久删除</button></div>}
 </div>}
 function section(title:string,items:MemoDocument[]){if(!items.length)return null;return <section className={styles.noteSection}><div className={styles.sectionHead}><h3>{title}</h3><small>{items.length}</small></div><div className={styles.noteGroup}>{items.map(row)}</div></section>}
 function handleAction(note:MemoDocument,action:MemoAction){switch(action.kind){
  case 'edit':setActiveId(note.id);break;
  case 'pin':mutate(note.id,m=>({pinnedAt:m.pinnedAt?null:Date.now()}),false);break;
  case 'star':mutate(note.id,m=>({starred:!m.starred}),false);break;
  case 'copy':void copy(note);break;
  case 'export':exportOne(note);break;
  case 'move':move(note,action.folderId??null);break;
  case 'select':setActiveId(null);setSelecting(true);setSelected([note.id]);break;
  case 'trash':if(allowWrite()){mutate(note.id,{deletedAt:Date.now(),pinnedAt:null},false);setActiveId(null);}break;
 }}
 const actionNote=workspace.memos.find(m=>m.id===actionId)??null;

 if(!loaded)return <div className={styles.loading}>正在整理你的笔记…</div>;

 return <div ref={rootRef} className={styles.shell}>
  {actionNote&&<MemoActions key={actionNote.id} note={actionNote} folders={workspace.folders} onAction={action=>handleAction(actionNote,action)} onDone={()=>setActionId(null)}/>}
  <button id="note" tabIndex={-1} aria-hidden="true" className={styles.bridgeNew} onFocus={addNote}/>
  {!active&&<div className={styles.listView}>
   <header className={styles.heroHead}><div><span>LUKE · LITTLE MEMORIES</span><h2>我们的手记</h2><p role="status">{counts.all} 条珍藏 · {status}</p></div><div className={styles.headActions}><button aria-label="搜索笔记" onClick={()=>document.getElementById('memo-search')?.focus()}><Search/></button><button aria-label="笔记本" onClick={()=>setNotebooksOpen(true)}><BookHeart/></button></div></header>
   <div className={styles.chipRail}><button className={styles.notebookChip} onClick={()=>setNotebooksOpen(true)} aria-label="打开笔记本"><BookHeart size={18}/></button><button data-active={category==='all'} onClick={()=>selectCategory('all')}>全部笔记</button><button data-active={category==='starred'} onClick={()=>selectCategory('starred')}>加星</button>{workspace.folders.map(folder=><button key={folder.id} data-active={category===`folder:${folder.id}`} onClick={()=>selectCategory(`folder:${folder.id}`)}>{folder.name}</button>)}</div>
   <p className={styles.listHint}>点开继续写，长按或点「···」整理。</p><label className={styles.searchBar}><Search size={17}/><input id="memo-search" value={query} maxLength={120} onChange={e=>setQuery(e.target.value)} aria-label="搜索手记" placeholder={`搜索${categoryLabel}`}/>{query&&<button aria-label="清空搜索" onClick={()=>setQuery('')}>×</button>}</label>
   {selecting&&<div className={styles.selectionBar}><span aria-live="polite">已选 <strong>{selected.length}</strong> 条</span><button onClick={()=>setSelected(selected.length===visible.length?[]:visible.map(m=>m.id))}>{selected.length===visible.length?'取消全选':'全选'}</button><button className={styles.selectionTrash} disabled={!selected.length} onClick={deleteSelected}><Trash2 size={16}/>回收站</button><button aria-label="退出多选" onClick={()=>{setSelecting(false);setSelected([]);}}><X size={18}/></button></div>}
   <main className={styles.noteList}>{category==='trash'?section('回收站',others):<>{section("置顶",pinned)}{section(pinned.length?"其他笔记":"全部笔记",others)}</>}{!visible.length&&<div className={styles.empty}><BookHeart size={28}/><strong>{query?'没有找到相关笔记':category==='trash'?'回收站是空的':'还没有笔记'}</strong><p>{query?'换个关键词试试。':'点右下角的 +，随手记下第一件事。'}</p></div>}</main>
   {!notebooksOpen&&!selecting&&!actionNote&&createPortal(<button className={styles.fab} aria-label="新建笔记" onClick={addNote}><Plus size={30}/></button>,document.body)}
  </div>}

  {createPortal(<MotionPresence present={!!active} kind="sheet">{active&&<article ref={editorRef} data-memo-editor="true" className={styles.editorView}>
   <header className={styles.editorHead}><button className={styles.back} aria-label="返回笔记列表" onClick={()=>setActiveId(null)}><ChevronLeft/></button><div className={styles.editorTitle}><input aria-label="笔记标题" maxLength={300} value={active.title} placeholder="标题" onChange={e=>mutate(active.id,{title:e.target.value})}/><small>{dtf.format(new Date(active.updatedAt))} · {status}</small></div><div className={styles.editorActions}><button data-on={preview} aria-label={preview?'编辑':'阅读'} onClick={()=>setPreview(v=>!v)}><Eye size={20}/></button><button type="button" aria-label="更多操作" onClick={()=>setActionId(active.id)}><MoreHorizontal size={22}/></button></div></header>
   {preview?<Preview text={active.body}/>:<textarea ref={bodyRef} className={styles.bodyInput} aria-label="笔记正文" value={active.body} maxLength={100000} onChange={e=>mutate(active.id,{body:e.target.value})} placeholder="随手写点什么……"/>}
   <div className={styles.editorBottom}><div className={styles.editorFootnote}><span>{preview?"阅读模式":"正在珍藏这一刻"}</span><span>{active.body.length.toLocaleString()} 字符</span></div><MotionPresence present={moodOpen}>{moodOpen&&<div className={styles.moodPicker}><button data-active={!active.mood} onClick={()=>{mutate(active.id,{mood:undefined});setMoodOpen(false);}}>无</button>{memoMoods.map(mood=><button key={mood} data-active={active.mood===mood} onClick={()=>{mutate(active.id,{mood});setMoodOpen(false);}}>{mood}</button>)}</div>}</MotionPresence><div className={styles.quickBar}><button className={styles.moodButton} data-on={!!active.mood} onClick={()=>setMoodOpen(v=>!v)}><Heart size={16}/>{active.mood??'心情'}</button><span/><button onClick={()=>insert('## ')}>H2</button><button onClick={()=>insert('- ')}>• 列表</button><button onClick={()=>insert('- [ ] ')}>☐ 待办</button><button onClick={()=>insert('**','**')}>B</button><button onClick={()=>insert('> ')}>引用</button></div></div>
  </article>}</MotionPresence>,document.body)}

  {createPortal(<MotionPresence present={notebooksOpen&&!active} kind="sheet">{notebooksOpen&&!active&&<div data-memo-notebooks="true" className={styles.notebookOverlay}><div className={styles.notebookSheet}><header><button aria-label="关闭笔记本" onClick={()=>setNotebooksOpen(false)}>×</button><h2>笔记本</h2><button onClick={()=>setCreatingFolder(true)}>新建</button></header><button className={styles.allNotebook} onClick={()=>selectCategory('all')}><BookHeart/><strong>全部笔记</strong><span>{counts.all}</span><ChevronRight/></button><div className={styles.notebookLabel}>我的笔记本</div><div className={styles.notebookGroup}>{workspace.folders.map(folder=><div className={styles.folderLine} key={folder.id}><button onClick={()=>selectCategory(`folder:${folder.id}`)}><Folder/><strong>{folder.name}</strong><span>{counts.folders[folder.id]??0}</span><ChevronRight/></button><button aria-label={`删除${folder.name}`} onClick={()=>removeFolder(folder.id)}>×</button></div>)}{!workspace.folders.length&&!creatingFolder&&<p className={styles.noFolders}>还没有自定义笔记本。</p>}{creatingFolder&&<form className={styles.newFolderForm} onSubmit={e=>{e.preventDefault();addFolder();}}><FolderPlus/><input autoFocus maxLength={30} value={folderDraft} onChange={e=>setFolderDraft(e.target.value)} placeholder="笔记本名称"/><button disabled={!folderDraft.trim()}>完成</button></form>}</div><div className={styles.notebookLabel}>其他</div><div className={styles.notebookGroup}><button className={styles.plainNotebook} onClick={()=>selectCategory('all')}><Folder/><strong>默认笔记本</strong><span>{workspace.memos.filter(m=>m.deletedAt===null&&m.folderId===null).length}</span><ChevronRight/></button><button className={styles.plainNotebook} onClick={()=>selectCategory('starred')}><Star/><strong>加星笔记</strong><span>{counts.starred}</span><ChevronRight/></button><button className={styles.plainNotebook} onClick={()=>selectCategory('trash')}><Trash2/><strong>回收站</strong><span>{counts.trash}</span><ChevronRight/></button></div><button className={styles.backupButton} onClick={exportAll}>导出全部笔记备份</button></div></div>}</MotionPresence>,document.body)}
 </div>;
}
