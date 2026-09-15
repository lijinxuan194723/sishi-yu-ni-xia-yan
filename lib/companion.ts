import {parseArchive,type MemoryArchive} from './memory-archive.ts';
import type {Focus} from './focus.ts';
export type Reading={title:string;author:string;updatedAt:string;progress?:string;thought?:string};
export const contextLabels={weather:'天气',reading:'阅读',study:'学习',plans:'计划',notes:'手记与心情',dates:'生日与纪念日'} as const;
export type Memory={summary:string;through:number;pinned:string;updatedAt:string};
export const moods=['开心','平静','疲惫','低落','期待'] as const;
export type Mood=typeof moods[number];
export type Data = {memoryArchive?:MemoryArchive;name:string; since:string; messages:{who:string;text:string;source?:string;at?:string;favorite?:boolean;interrupted?:boolean}[]; tasks:{id:string;date:string;text:string;done:boolean;important?:boolean}[]; notes:{date:string;text:string;mood?:Mood}[]; checks:string[];reading?:Reading;books?:Reading[];contextSharing?:Partial<Record<keyof typeof contextLabels,boolean>>;study?:{subject:string;startedAt:number};draft?:string;countdown?:{seconds:number;remainingMs:number;endsAt?:number};focus?:Focus;focusTasks?:{id:string;title:string;minutes:number;group:string}[];focusLog?:{at:string;minutes:number;kind?:'pomodoro';title?:string;group?:string}[]; memory?:Memory;birthday?:string;anniversaries?:{id:string;title:string;date:string}[]};
export const emptyMemory:Memory={summary:'',through:0,pinned:'',updatedAt:''};
export function dateKey(d:Date){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
export function validDate(value:unknown):value is string{return typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value)&&dateKey(new Date(value+'T12:00:00'))===value;}
export function parseData(text:string):Data{
 const p=JSON.parse(text);const str=(v:unknown,max:number)=>typeof v==='string'&&v.length<=max;
 if(!p||!str(p.name,12)||!validDate(p.since)||!Array.isArray(p.messages)||!p.messages.every((m:any)=>m&&['me','luke'].includes(m.who)&&str(m.text,20000)&&(m.source===undefined||['model','demo'].includes(m.source))&&(m.at===undefined||str(m.at,40)))||!Array.isArray(p.tasks)||!p.tasks.every((t:any)=>t&&str(t.id,100)&&validDate(t.date)&&str(t.text,150)&&typeof t.done==='boolean')||!Array.isArray(p.notes)||!p.notes.every((n:any)=>n&&validDate(n.date)&&str(n.text,5000))||!Array.isArray(p.checks)||!p.checks.every(validDate))throw new Error('备份格式不正确');
 const book=(b:any)=>b&&str(b.title,120)&&b.title.trim()&&str(b.author,80)&&str(b.updatedAt,40)&&Number.isFinite(Date.parse(b.updatedAt))&&(b.progress===undefined||str(b.progress,80))&&(b.thought===undefined||str(b.thought,1000));
 if(p.books!==undefined&&(!Array.isArray(p.books)||!p.books.every(book)))throw Error('书架格式不正确');
 if(p.contextSharing!==undefined&&(!p.contextSharing||typeof p.contextSharing!=='object'||Array.isArray(p.contextSharing)||Object.entries(p.contextSharing).some(([k,v])=>!Object.hasOwn(contextLabels,k)||typeof v!=='boolean')))throw Error('近况共享格式不正确');
 if(p.messages.some((m:any)=>m.interrupted!==undefined&&typeof m.interrupted!=='boolean'))throw Error('回复状态格式不正确');
 if(p.reading!==undefined&&!book(p.reading))throw Error('阅读进度格式不正确');
 const cleanBook=(b:any)=>({title:b.title,author:b.author,updatedAt:b.updatedAt,...(b.progress!==undefined?{progress:b.progress}:{}),...(b.thought!==undefined?{thought:b.thought}:{})});
 if(p.reading!==undefined&&(!p.reading||!str(p.reading.title,120)||!p.reading.title.trim()||!str(p.reading.author,80)||!str(p.reading.updatedAt,40)||!Number.isFinite(Date.parse(p.reading.updatedAt))))throw Error('阅读记录格式不正确');
 if(p.birthday!==undefined&&p.birthday!==''&&!validDate(p.birthday))throw Error('生日格式不正确');
 if(p.messages.some((m:any)=>m.favorite!==undefined&&typeof m.favorite!=='boolean')||p.notes.some((n:any)=>n.mood!==undefined&&!moods.includes(n.mood)))throw Error('收藏或心情格式不正确');
 if(p.anniversaries!==undefined&&(!Array.isArray(p.anniversaries)||p.anniversaries.length>30||!p.anniversaries.every((a:any)=>a&&str(a.id,100)&&str(a.title,40)&&a.title.trim()&&validDate(a.date))))throw Error('纪念日格式不正确');
 if(p.draft!==undefined&&!str(p.draft,2000))throw Error('聊天草稿格式不正确');
 if(p.tasks.some((t:any)=>t.important!==undefined&&typeof t.important!=='boolean'))throw Error('重要计划格式不正确');
 const minutes=(v:any)=>Number.isInteger(v)&&v>=1&&v<=180;
 if(p.focus!==undefined&&(!p.focus||!minutes(p.focus.minutes)||!Number.isFinite(p.focus.remainingMs)||p.focus.remainingMs<0||p.focus.remainingMs>p.focus.minutes*60000||(p.focus.endsAt!==undefined&&(!Number.isSafeInteger(p.focus.endsAt)||p.focus.endsAt<0||p.focus.endsAt>8640000000000000))))throw Error('专注计时格式不正确');
 if(p.focusLog!==undefined&&(!Array.isArray(p.focusLog)||!p.focusLog.every((f:any)=>f&&str(f.at,40)&&Number.isFinite(Date.parse(f.at))&&Number.isFinite(f.minutes)&&f.minutes>0&&f.minutes<=1500)))throw Error('专注记录格式不正确');
 if(p.study!==undefined&&(!p.study||!str(p.study.subject,30)||!p.study.subject.trim()||!Number.isSafeInteger(p.study.startedAt)||p.study.startedAt<0||p.study.startedAt>Date.now()))throw Error('学习计时格式不正确');
 const tomato=p.focus?.pomodoro;
 if(tomato!==undefined&&(!tomato||!['work','short','long'].includes(tomato.phase)||!minutes(tomato.work)||!minutes(tomato.short)||!minutes(tomato.long)||!Number.isInteger(tomato.rounds)||tomato.rounds<1||tomato.rounds>12||!Number.isInteger(tomato.completed)||tomato.completed<0||tomato.completed>tomato.rounds||p.focus.minutes!==tomato[tomato.phase]||(tomato.phase==='long'?tomato.completed!==tomato.rounds:tomato.completed>=tomato.rounds)||(tomato.phase==='short'&&tomato.completed===0)))throw Error('番茄钟格式不正确');
 if(p.focusLog?.some((f:any)=>f.kind!==undefined&&f.kind!=='pomodoro'))throw Error('专注记录类型不正确');
 const c=p.countdown;
 if(c!==undefined&&(!c||!Number.isInteger(c.seconds)||c.seconds<0||c.seconds>86399||!Number.isFinite(c.remainingMs)||c.remainingMs<0||c.remainingMs>c.seconds*1000||(c.endsAt!==undefined&&(!Number.isSafeInteger(c.endsAt)||c.endsAt<0||c.endsAt>8640000000000000))))throw Error('倒计时格式不正确');
 if(p.focus?.title!==undefined&&!str(p.focus.title,80))throw Error('专注标题格式不正确');
 if(p.focusLog?.some((l:any)=>l.title!==undefined&&!str(l.title,80)))throw Error('专注记录标题格式不正确');
 if(p.focusTasks!==undefined&&(!Array.isArray(p.focusTasks)||!p.focusTasks.every((t:any)=>t&&str(t.id,100)&&str(t.title,80)&&t.title.trim()&&str(t.group,30)&&t.group.trim()&&minutes(t.minutes))||new Set(p.focusTasks.map((t:any)=>t.id)).size!==p.focusTasks.length))throw Error('专注待办格式不正确');
 if(p.focus?.group!==undefined&&!str(p.focus.group,30)||p.focusLog?.some((l:any)=>l.group!==undefined&&!str(l.group,30)))throw Error('专注分类格式不正确');
 const m=p.memory;if(m&&(!str(m.summary,14000)||!str(m.pinned,5000)||!str(m.updatedAt,40)||!Number.isSafeInteger(m.through)||m.through<0||m.through>p.messages.length||(!m.summary&&m.through!==0)))throw new Error('记忆格式不正确');
 const memoryArchive=p.memoryArchive===undefined?undefined:parseArchive(p.memoryArchive,p.messages.length);
 return {...(memoryArchive?{memoryArchive}:{}),...(p.contextSharing?{contextSharing:p.contextSharing}:{}),...(p.books?{books:p.books.map(cleanBook)}:{}),...(p.reading?{reading:cleanBook(p.reading)}:{}),...(p.study?{study:{subject:p.study.subject,startedAt:p.study.startedAt}}:{}),...(p.focusTasks?{focusTasks:p.focusTasks.map((t:any)=>({id:t.id,title:t.title,minutes:t.minutes,group:t.group}))}:{}),...(c?{countdown:{seconds:c.seconds,remainingMs:c.remainingMs,...(c.endsAt!==undefined?{endsAt:c.endsAt}:{})}}:{}),...(p.draft!==undefined?{draft:p.draft}:{}),...(p.focus?{focus:{...(p.focus.group?{group:p.focus.group}:{}),...(p.focus.title?{title:p.focus.title}:{}),minutes:p.focus.minutes,remainingMs:p.focus.remainingMs,...(tomato?{pomodoro:{work:tomato.work,short:tomato.short,long:tomato.long,rounds:tomato.rounds,completed:tomato.completed,phase:tomato.phase}}:{}),...(p.focus.endsAt!==undefined?{endsAt:p.focus.endsAt}:{})}}:{}),...(p.focusLog?{focusLog:p.focusLog.map((f:any)=>({at:f.at,minutes:f.minutes,...(f.group?{group:f.group}:{}),...(f.title?{title:f.title}:{}),...(f.kind?{kind:f.kind}:{})}))}:{}),...(p.birthday?{birthday:p.birthday}:{}),...(p.anniversaries?{anniversaries:p.anniversaries.map((a:any)=>({id:a.id,title:a.title,date:a.date}))}:{}),name:p.name,since:p.since,messages:p.messages.map((x:any)=>({who:x.who,text:x.text,...(x.interrupted!==undefined?{interrupted:x.interrupted}:{}),...(x.source?{source:x.source}:{}),...(x.at?{at:x.at}:{}),...(x.favorite!==undefined?{favorite:x.favorite}:{})})),tasks:p.tasks.map((x:any)=>({id:x.id,date:x.date,text:x.text,done:x.done,...(x.important!==undefined?{important:x.important}:{})})),notes:p.notes.map((x:any)=>({date:x.date,text:x.text,...(x.mood?{mood:x.mood}:{})})),checks:p.checks,...(m?{memory:{summary:m.summary,pinned:m.pinned,through:m.through,updatedAt:m.updatedAt}}:{})};
}
export function companionReply(text:string){return /累|难过|伤心/.test(text)?'辛苦啦。先在这里休息一下吧，我陪着你。':/晚安|睡/.test(text)?'晚安，明天也一起度过吧。祝你做个好梦。':/想你|喜欢/.test(text)?'我也很想见到你。和你一起的每一天，都值得好好珍藏。':'嗯，我在听。今天的小事，也可以慢慢说给我听。';}

export function togetherDays(since:string,now=new Date()){return Math.max(1,Math.floor((Date.parse(dateKey(now)+'T00:00:00Z')-Date.parse(since+'T00:00:00Z'))/86400000)+1);}
