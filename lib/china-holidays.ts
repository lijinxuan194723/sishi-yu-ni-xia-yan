/** Mainland China, State Council General Office notice dated 2025-11-04. No inferred make-up workdays. */
export const HOLIDAY_SOURCE='https://www.beijing.gov.cn/fuwu/bmfw/sy/jrts/202511/t20251104_4258838.html';
export const HOLIDAY_YEAR=2026;
export type Holiday={name:string;kind:'rest'|'work'};
const ranges:readonly [string,string,string][]=[['元旦','01-01','01-03'],['春节','02-15','02-23'],['清明节','04-04','04-06'],['劳动节','05-01','05-05'],['端午节','06-19','06-21'],['中秋节','09-25','09-27'],['国庆节','10-01','10-07']];
const work:Record<string,string>={'01-04':'元旦调休','02-14':'春节调休','02-28':'春节调休','05-09':'劳动节调休','09-20':'国庆调休','10-10':'国庆调休'};
export function holidayFor(date:string):Holiday|null{
 if(!/^2026-\d{2}-\d{2}$/.test(date))return null;
 const d=date.slice(5);if(work[d])return {name:work[d],kind:'work'};
 const match=ranges.find(([,from,to])=>d>=from&&d<=to);return match?{name:match[0],kind:'rest'}:null;
}
export function parseCalendarDate(value:string){
 if(!/^\d{4}-\d{2}-\d{2}$/.test(value))return null;
 const [y,m,d]=value.split('-').map(Number);if(y<1900||y>2100)return null;const date=new Date(y,m-1,d,12);
 return date.getFullYear()===y&&date.getMonth()===m-1&&date.getDate()===d?date:null;
}
