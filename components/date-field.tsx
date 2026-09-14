'use client';
import {useId,useState} from 'react';
import {CalendarDays,ChevronLeft,ChevronRight} from 'lucide-react';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from '@/components/ui/dialog';
import {dateKey} from '@/lib/companion';
import {holidayFor,parseCalendarDate} from '@/lib/china-holidays';
type Props={value:string;onValueChange:(value:string)=>void;min?:string;max?:string;disabled?:boolean;allowClear?:boolean;className?:string;'aria-label'?:string};
export function DateField({value,onValueChange,min='1900-01-01',max='2100-12-31',disabled=false,allowClear=true,className='',...props}:Props){
 const id=useId(),[open,setOpen]=useState(false),[draft,setDraft]=useState(value),[error,setError]=useState(''),[month,setMonth]=useState(()=>parseCalendarDate(value)??new Date());
 const lower=parseCalendarDate(min)?min:'1900-01-01',upper=parseCalendarDate(max)?max:'2100-12-31',today=dateKey(new Date());
 const allowed=(date:string)=>!!parseCalendarDate(date)&&date>=lower&&date<=upper;
 const initial=()=>allowed(value)?value:today<lower?lower:today>upper?upper:today;
 function start(){const day=initial();setDraft(day);setMonth(parseCalendarDate(day)!);setError('');setOpen(true);}
 function choose(day:string){if(allowed(day)){setDraft(day);setError('');setMonth(parseCalendarDate(day)!);}}
 function shift(n:number){setMonth(current=>new Date(current.getFullYear(),current.getMonth()+n,1,12));}
 const year=month.getFullYear(),m=month.getMonth(),days=new Date(year,m+1,0).getDate(),selectedHoliday=holidayFor(draft);
 function move(day:string,delta:number){const next=parseCalendarDate(day);if(!next)return;next.setDate(next.getDate()+delta);const value=dateKey(next);if(!allowed(value))return;choose(value);requestAnimationFrame(()=>document.getElementById(`${id}-${value}`)?.focus());}
 return <><button type="button" className={`date-field ${className}`} disabled={disabled} aria-label={props['aria-label']??'选择日期'} aria-haspopup="dialog" onClick={start}><span>{value?value.replaceAll('-',' / '):'选择日期'}</span><CalendarDays size={18}/></button>
 <Dialog open={open} onOpenChange={setOpen}><DialogContent className="date-picker" initialFocus={false}>
  <DialogTitle>{props['aria-label']??'选择日期'}</DialogTitle><DialogDescription className="sr-only">选择后点击确定应用，取消保留原日期。</DialogDescription>
  <div className="date-jump"><input aria-label="输入指定日期" placeholder="YYYY-MM-DD" inputMode="numeric" maxLength={10} value={draft} onChange={e=>{setDraft(e.target.value);setError('');if(parseCalendarDate(e.target.value)&&allowed(e.target.value))setMonth(parseCalendarDate(e.target.value)!);}}/><button type="button" className="soft-button" disabled={!allowed(today)} onClick={()=>choose(today)}>今天</button></div>
  <div className="date-month"><button type="button" className="round" aria-label="日期上个月" disabled={dateKey(new Date(year,m,1))<=lower} onClick={()=>shift(-1)}><ChevronLeft size={19}/></button><div><input type="number" aria-label="选择年份" min={1900} max={2100} value={year} onChange={e=>{const y=Number(e.target.value);if(Number.isInteger(y)&&y>=1900&&y<=2100)setMonth(new Date(y,m,1,12));}}/><span>年</span><select aria-label="选择月份" value={m} onChange={e=>setMonth(new Date(year,+e.target.value,1,12))}>{Array.from({length:12},(_,i)=><option key={i} value={i}>{i+1}月</option>)}</select></div><button type="button" className="round" aria-label="日期下个月" disabled={dateKey(new Date(year,m+1,0))>=upper} onClick={()=>shift(1)}><ChevronRight size={19}/></button></div>
  <div className="date-grid" role="group" aria-label={`${year}年${m+1}月`}>{'一二三四五六日'.split('').map(d=><small key={d}>{d}</small>)}{Array.from({length:(new Date(year,m,1).getDay()+6)%7},(_,i)=><span key={`pad-${i}`}/>)}{Array.from({length:days},(_,i)=>{const day=dateKey(new Date(year,m,i+1)),holiday=holidayFor(day);return <button id={`${id}-${day}`} type="button" key={day} disabled={!allowed(day)} aria-label={`${day}${holiday?`，${holiday.name}${holiday.kind==='rest'?'放假':'上班'}`:''}`} aria-pressed={draft===day} aria-current={day===today?'date':undefined} onClick={()=>choose(day)} onKeyDown={e=>{const delta=({ArrowLeft:-1,ArrowRight:1,ArrowUp:-7,ArrowDown:7} as Record<string,number>)[e.key];if(delta){e.preventDefault();move(day,delta);}}}>{i+1}{holiday&&<i data-kind={holiday.kind}>{holiday.kind==='rest'?'休':'班'}</i>}</button>;})}</div>
  <p className="date-context" role="status">{error||(!parseCalendarDate(draft)?'请输入完整日期':!allowed(draft)?`可选范围：${lower} 至 ${upper}`:selectedHoliday?`${selectedHoliday.name} · ${selectedHoliday.kind==='rest'?'放假':'调休上班'}`:year!==2026?'此年份未收录官方放假调休数据':' ')}</p>
  <div className="date-actions">{allowClear&&<button type="button" className="text-button" onClick={()=>{onValueChange('');setOpen(false);}}>清除日期</button>}<button type="button" className="soft-button" onClick={()=>setOpen(false)}>取消</button><button type="button" className="primary" disabled={!allowed(draft)} onClick={()=>{if(!allowed(draft)){setError('请选择范围内的有效日期');return;}onValueChange(draft);setOpen(false);}}>确定日期</button></div>
 </DialogContent></Dialog></>;
}
