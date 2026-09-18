import type {Data} from './companion.ts';
export function finishStudy(data:Data,startedAt:number,now=Date.now()):Partial<Data>{
 const session=data.study;if(!session||session.startedAt!==startedAt||now<startedAt)return {};
 const records:NonNullable<Data['focusLog']>=[];
 for(let cursor=startedAt;cursor<now;){const d=new Date(cursor);const end=Math.min(now,new Date(d.getFullYear(),d.getMonth(),d.getDate()+1).getTime());records.push({at:new Date(cursor).toISOString(),minutes:(end-cursor)/60000,group:session.subject});cursor=end;}
 return {study:undefined,focusLog:[...(data.focusLog??[]),...records]};
}
export function studyTime(minutes:number){return minutes>0&&minutes<1?'不足 1 分钟':`${Math.floor(minutes)} 分钟`;}
export function editStudyRecord(data:Data,record:NonNullable<Data['focusLog']>[number],subject:string,start:number,end:number,now=Date.now()):Partial<Data>{
 if(!subject.trim()||subject.trim().length>30||!Number.isFinite(start)||!Number.isFinite(end)||start<0||end<=start||end>now||end-start>7*86400000)throw Error('请填写科目和有效起止时间，单条最长 7 天，结束时间不能晚于现在。');
 const logs=data.focusLog??[],index=logs.indexOf(record);if(index<0)throw Error('这条记录已变化，请重新打开。');
 const split=finishStudy({...data,study:{subject:subject.trim(),startedAt:start},focusLog:[]},start,end).focusLog??[];
 return {focusLog:[...logs.slice(0,index),...split,...logs.slice(index+1)]};
}
