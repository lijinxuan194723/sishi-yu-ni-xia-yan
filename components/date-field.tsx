'use client';
import {useId,useState,useRef,useEffect,useLayoutEffect} from 'react';
import {CalendarDays,ChevronLeft,ChevronRight} from 'lucide-react';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from '@/components/ui/dialog';
import {dateKey} from '@/lib/companion';
import {holidayFor,parseCalendarDate} from '@/lib/china-holidays';
import {normalizeDateEntry,yearFromEntry,boundedMonth,moveCalendarDay} from '@/lib/date-interaction';
type Props={value:string;onValueChange:(value:string)=>void;min?:string;max?:string;disabled?:boolean;allowClear?:boolean;className?:string;'aria-label'?:string};
export function DateField({value,onValueChange,min='1900-01-01',max='2100-12-31',disabled=false,allowClear=true,className='',...props}:Props){
 const id=useId(),trigger=useRef<HTMLButtonElement>(null),applying=useRef(false),pendingFocus=useRef('');
 const [open,setOpen]=useState(false),[draft,setDraft]=useState(value),[error,setError]=useState('');
 const [month,setMonth]=useState(()=>parseCalendarDate(value)??new Date());
 const [yearEntry,setYearEntry]=useState(String(month.getFullYear()));
 const lower=parseCalendarDate(min)?min:'1900-01-01',upper=parseCalendarDate(max)?max:'2100-12-31';
 const low=parseCalendarDate(lower)!,high=parseCalendarDate(upper)!,today=dateKey(new Date());
 const allowed=(date:string)=>!!parseCalendarDate(date)&&date>=lower&&date<=upper;
 const initial=()=>allowed(value)?value:today<lower?lower:today>upper?upper:today;
 const year=month.getFullYear(),m=month.getMonth(),days=new Date(year,m+1,0).getDate();
 const selectedHoliday=holidayFor(draft);
 useEffect(()=>setYearEntry(String(year)),[year]);
 useLayoutEffect(()=>{
  if(!pendingFocus.current)return;
  const next=document.getElementById(`${id}-${pendingFocus.current}`);
  if(next){pendingFocus.current='';next.focus({preventScroll:true});}
 },[draft,year,m,id]);
 function start(){if(disabled)return;const day=initial();applying.current=false;setDraft(day);setMonth(parseCalendarDate(day)!);setYearEntry(day.slice(0,4));setError('');setOpen(true);}
 function choose(day:string){if(allowed(day)){setDraft(day);setError('');setMonth(parseCalendarDate(day)!);}}
 function shift(n:number){setMonth(current=>boundedMonth(current.getFullYear(),current.getMonth()+n,low,high));setError('');}
 function commitYear(){
  const next=yearFromEntry(yearEntry,low.getFullYear(),high.getFullYear());
  if(next===null){setYearEntry(String(year));setError(`年份范围：${low.getFullYear()}—${high.getFullYear()}`);return;}
  const nextMonth=boundedMonth(next,m,low,high);setMonth(nextMonth);setYearEntry(String(nextMonth.getFullYear()));setError('');
 }
 function apply(next:string){if(applying.current)return;applying.current=true;onValueChange(next);setOpen(false);}
 const focusDay=allowed(draft)&&draft.slice(0,7)===dateKey(month).slice(0,7)?draft:
  Array.from({length:days},(_,index)=>dateKey(new Date(year,m,index+1))).find(allowed);
 return <><button ref={trigger} type="button" className={`date-field ${className}`} disabled={disabled} aria-label={props['aria-label']??'选择日期'} aria-haspopup="dialog" aria-expanded={open} onClick={start}><span>{value?value.replaceAll('-',' / '):'选择日期'}</span><CalendarDays size={18}/></button>
 <Dialog open={open} onOpenChange={setOpen}><DialogContent className="date-picker date-picker-refined" initialFocus={kind=>kind==='keyboard'?(document.getElementById(`${id}-${focusDay}`)??false):false} finalFocus={trigger}>
  <div className="date-picker-body">
   <DialogTitle>{props['aria-label']??'选择日期'}</DialogTitle><DialogDescription className="sr-only">选择后点击确定应用，取消保留原日期。</DialogDescription>
   <div className="date-jump"><input aria-label="输入指定日期" aria-describedby={`${id}-status`} aria-invalid={draft.length>=8&&!allowed(draft)} placeholder="例如 20260915" inputMode="numeric" maxLength={12} value={draft} onChange={event=>{const next=normalizeDateEntry(event.target.value);setDraft(next);setError('');if(allowed(next))setMonth(parseCalendarDate(next)!);}} onKeyDown={event=>{if(event.key==='Enter'&&!event.nativeEvent.isComposing){event.preventDefault();if(allowed(draft))apply(draft);}}}/><button type="button" className="bar-today" aria-label="回到今天" disabled={!allowed(today)} onClick={()=>choose(today)}><img src="/images/companions/cat.webp" width={30} height={30} alt=""/></button></div>
   <div className="date-month"><button type="button" className="round" aria-label="日期上个月" disabled={year*12+m<=low.getFullYear()*12+low.getMonth()} onClick={()=>shift(-1)}><ChevronLeft size={19}/></button><div><input type="text" inputMode="numeric" aria-label="选择年份" aria-invalid={yearEntry.length===4&&yearFromEntry(yearEntry,low.getFullYear(),high.getFullYear())===null} maxLength={4} value={yearEntry} onChange={event=>{setYearEntry(event.target.value);setError('');}} onBlur={commitYear} onKeyDown={event=>{if(event.key==='Enter'){event.preventDefault();commitYear();}}}/><span>年</span><select aria-label="选择月份" value={m} onChange={event=>{const nextMonth=+event.target.value;setMonth(current=>boundedMonth(current.getFullYear(),nextMonth,low,high));setError('');}}>{Array.from({length:12},(_,index)=><option key={index} value={index} disabled={year*12+index<low.getFullYear()*12+low.getMonth()||year*12+index>high.getFullYear()*12+high.getMonth()}>{index+1}月</option>)}</select></div><button type="button" className="round" aria-label="日期下个月" disabled={year*12+m>=high.getFullYear()*12+high.getMonth()} onClick={()=>shift(1)}><ChevronRight size={19}/></button></div>
   <div className="date-grid" role="group" aria-label={`${year}年${m+1}月`}>{'一二三四五六日'.split('').map(day=><small key={day}>{day}</small>)}{Array.from({length:(new Date(year,m,1).getDay()+6)%7},(_,index)=><span key={`pad-${index}`}/>)}{Array.from({length:days},(_,index)=>{
    const day=dateKey(new Date(year,m,index+1)),holiday=holidayFor(day);
    return <button id={`${id}-${day}`} type="button" key={day} disabled={!allowed(day)} tabIndex={day===focusDay?0:-1} aria-label={`${day}${holiday?`，${holiday.name}${holiday.kind==='rest'?'放假':'上班'}`:''}`} aria-pressed={draft===day} aria-current={day===today?'date':undefined} onClick={()=>choose(day)} onKeyDown={event=>{
     const next=moveCalendarDay(parseCalendarDate(day)!,event.key,event.shiftKey);if(!next)return;
     event.preventDefault();const key=dateKey(next);if(!allowed(key))return;pendingFocus.current=key;choose(key);
    }}><span className="date-badge-line">{holiday&&<i data-kind={holiday.kind}>{holiday.kind==='rest'?'休':'班'}</i>}</span><span>{day===today?<img src="/images/companions/cat.webp" width={26} height={26} alt=""/>:index+1}</span></button>;
   })}</div>
   <p id={`${id}-status`} className="date-context" role="status">{error||(!parseCalendarDate(draft)?'请输入完整日期':!allowed(draft)?`可选范围：${lower} 至 ${upper}`:selectedHoliday?`${selectedHoliday.name} · ${selectedHoliday.kind==='rest'?'放假':'调休上班'}`:year!==2026?'此年份未收录官方放假调休数据':' ')}</p>
  </div>
  <div className="date-actions">{allowClear&&<button type="button" className="text-button" onClick={()=>apply('')}>清除日期</button>}<button type="button" className="soft-button" onClick={()=>setOpen(false)}>取消</button><button type="button" className="primary" data-haptic="confirm" disabled={!allowed(draft)} onClick={()=>{if(!allowed(draft)){setError('请选择范围内的有效日期');return;}apply(draft);}}>确定日期</button></div>
 </DialogContent></Dialog></>;
}
