import {LukeDesk} from './luke-companion';
import {dateKey,type Data} from '@/lib/companion';
import {studyTime} from '@/lib/study';
export function WeekReview({data,today}:{data:Data;today:string}){
 const start=new Date(today+'T12:00:00');start.setDate(start.getDate()-6);const from=dateKey(start),inWeek=(day:string)=>day>=from&&day<=today;
 const items=[['陪伴打卡',new Set(data.checks.filter(inWeek)).size+' 天'],['完成计划',data.tasks.filter(t=>t.done&&inWeek(t.date)).length+' 件'],['珍藏手记',data.notes.filter(n=>inWeek(n.date)).length+' 篇'],['专注时光',studyTime((data.focusLog??[]).filter(n=>inWeek(dateKey(new Date(n.at)))).reduce((sum,n)=>sum+n.minutes,0))]];
 return <><LukeDesk data={data} today={today}/><section className="card week-review"><h2>我们的最近七天</h2><div>{items.map(([label,value])=><p key={label}><strong>{value}</strong><small>{label}</small></p>)}</div><small>平凡的小事，也在一点点积攒。</small></section></>;
}
