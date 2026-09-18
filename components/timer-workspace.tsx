'use client';
import {useEffect,useMemo,useRef,useState} from 'react';
import {ChevronLeft,ChevronRight,Play,Square,Settings2} from 'lucide-react';
import {dateKey,type Data} from '@/lib/companion';
import {rangeFor,shiftDay,focusStats} from '@/lib/focus-stats';
import {finishStudy,studyTime} from '@/lib/study';
import {emptySubjects,readStudySubjects,saveStudySubjects,type StudySubjects} from '@/lib/study-subjects';
import {SectionWorkspace} from './section-workspace';
import {SubjectSettings} from './subject-settings';
import {DateField} from './date-field';
import {StudyCompanion,LukeStudyCalendar} from './luke-companion';
import {MechanicalDial} from './study-dial';
import {StudyRecords} from './study-records';
import {ReminderPanel} from './reminder-panel';
import {InAppCountdown} from './countdown-ruler';
import {FocusMoment} from './focus-moment';
import {ExpandableNote} from './expandable-note';
import './timer-polish.css';
export function TimerWorkspace({data,ready,save,today}:{data:Data;ready:boolean;save:(p:Partial<Data>|((d:Data)=>Partial<Data>))=>void;today:string}){
 const [subjects,setSubjects]=useState<StudySubjects>(emptySubjects),[loaded,setLoaded]=useState(false),[subjectOpen,setSubjectOpen]=useState(false),[startAfterChoose,setStartAfterChoose]=useState(false),[error,setError]=useState(''),[now,setNow]=useState(Date.now()),[mode,setMode]=useState('day'),[day,setDay]=useState(today);
 function reloadSubjects(){try{setSubjects(readStudySubjects());setLoaded(true);setError('');}catch(e){setLoaded(false);setError(e instanceof Error?e.message:'科目未能读取。');}}
 useEffect(()=>{reloadSubjects();const storage=(e:StorageEvent)=>{if(e.key==='luke-study-subjects-v206'||e.key===null)reloadSubjects();};window.addEventListener('storage',storage);window.addEventListener('luke-subjects-change',reloadSubjects);return()=>{window.removeEventListener('storage',storage);window.removeEventListener('luke-subjects-change',reloadSubjects);};},[]);
 const active=data.study,subject=active?.subject??subjects.items.find(s=>s.id===subjects.selected)?.name??'';
 function update(v:StudySubjects){const saved=saveStudySubjects(v);setSubjects(saved);setError('');}
 useEffect(()=>{if(!active)return;let id:ReturnType<typeof setInterval>|undefined;const sync=()=>{clearInterval(id);if(!document.hidden){setNow(Date.now());id=setInterval(()=>setNow(Date.now()),1000);}};sync();document.addEventListener('visibilitychange',sync);return()=>{clearInterval(id);document.removeEventListener('visibilitychange',sync);};},[active?.startedAt]);
 const seconds=active?Math.max(0,Math.floor((now-active.startedAt)/1000)):0,logs=useMemo(()=>data.focusLog??[],[data.focusLog]),range=useMemo(()=>rangeFor(day,mode),[day,mode]),stats=useMemo(()=>focusStats(logs,range.from,range.to),[logs,range]);
 function shift(n:number){const d=new Date(day+'T12:00:00');setDay(mode==='year'?dateKey(new Date(d.getFullYear()+n,0,1)):mode==='month'?dateKey(new Date(d.getFullYear(),d.getMonth()+n,1)):shiftDay(day,n*(mode==='week'?7:1)));}
 // Keep the real action available for a fresh user; missing setup opens a clear
 // add-and-start flow rather than a permanently disabled, silent button.
 const starting=useRef(false);
 useEffect(()=>{starting.current=false;},[active]);
 function startStudy(chosen:string){
  if(!ready||!loaded||active||starting.current||!chosen.trim())return;
  starting.current=true;document.activeElement instanceof HTMLElement&&document.activeElement.blur();
  const start=Date.now();setNow(start);save(current=>current.study?{}:{study:{subject:chosen,startedAt:start}});
  // Save owns durability/status; allow an explicit retry if storage never committed.
  queueMicrotask(()=>{starting.current=false;});
 }
 function requestStart(){if(!ready)return;if(!loaded){reloadSubjects();return;}if(!subject){setStartAfterChoose(true);setSubjectOpen(true);return;}startStudy(subject);}
 const action=active?<button type="button" className="primary" disabled={!ready} onClick={()=>save(current=>finishStudy(current,active.startedAt,Date.now()))}><Square size={17}/>结束并保存</button>:<button type="button" className="primary" disabled={!ready} onClick={requestStart}><Play size={17}/>{!ready?'正在读取记录':!loaded?'重新读取科目':'开始计时'}</button>;
 return <div className="timer-workspace study-workspace"><SectionWorkspace name="计时分区" sections={[
 {id:'focus',label:'专注',content:<section className="card study-timer"><StudyCompanion running={!!active} subject={subject} today={today}/><div className="study-select210"><label className="sr-only" htmlFor="study-subject210">学习科目</label><select id="study-subject210" aria-label="学习科目" disabled={!ready||!!active||!loaded} value={active?'@active':subjects.selected} onChange={e=>{try{update({...subjects,selected:e.target.value});}catch(e){setError(e instanceof Error?e.message:'未保存。');}}}>{active?<option value="@active">{active.subject}</option>:<option value="">{subjects.items.length?'选择学习科目':'先添加你的科目'}</option>}{subjects.items.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}</select><button type="button" className="round" aria-label="管理学习科目" onClick={()=>{setStartAfterChoose(false);setSubjectOpen(true);}}><Settings2 size={18}/></button></div>{error&&<p role="alert">{error}</p>}<MechanicalDial seconds={seconds} running={!!active} subject={subject} startedAt={active?.startedAt}/></section>,footer:action},
 {id:'statistics',label:'统计',content:<section className="card"><h2>学习时长</h2><div className="journal-filters" aria-label="学习统计周期">{[['day','日'],['week','周'],['month','月'],['year','年']].map(([id,label])=><button key={id} aria-pressed={mode===id} onClick={()=>setMode(id)}>{label}</button>)}</div><div className="study-range"><button className="round" aria-label="上一学习周期" onClick={()=>shift(-1)}><ChevronLeft/></button><DateField compact aria-label="学习统计日期" value={day} allowClear={false} onValueChange={setDay}/><button className="round" aria-label="下一学习周期" onClick={()=>shift(1)}><ChevronRight/></button></div><p className="study-period">{range.from===range.to?range.from:range.from+' — '+range.to}<button className="text-button" onClick={()=>setDay(today)}>今天</button></p><p className="study-total">合计 <strong>{studyTime(stats.minutes)}</strong></p><div className="study-categories" aria-label="各科学习时长">{Object.entries(stats.categories).sort((a,b)=>b[1]-a[1]).map(([name,minutes])=><div key={name}><span>{name}</span><strong>{studyTime(minutes)}</strong><progress max={Math.max(1,stats.minutes)} value={minutes}/></div>)}</div><StudyRecords data={data} save={save} from={range.from} to={range.to}/>{!stats.count&&<p className="empty">这段时间还没有学习记录。</p>}</section>},
 {id:'calendar',label:'月历',content:<LukeStudyCalendar data={data} day={day} today={today} onDay={value=>{setDay(value);setMode('day');}}/>},
 {id:'pomodoro',label:'番茄',content:<FocusMoment data={data} ready={ready} save={save}/>},
 {id:'reminders',label:'提醒',content:<><InAppCountdown data={data} ready={ready} save={save}/><div className="reminder-system-hf3"><ExpandableNote label="系统闹钟与锁屏提醒" title="系统闹钟与锁屏提醒"><ReminderPanel/></ExpandableNote></div></>}
 ]}/><SubjectSettings open={subjectOpen} onClose={()=>{setSubjectOpen(false);setStartAfterChoose(false);}} startAfterChoose={startAfterChoose} onStart={startStudy} value={subjects} onChange={update} locked={!loaded||!ready||!!active}/></div>;
}
