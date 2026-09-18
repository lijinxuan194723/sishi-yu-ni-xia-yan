import y2023 from '../data/holidays/2023.json' with {type:'json'};
import y2024 from '../data/holidays/2024.json' with {type:'json'};
import y2025 from '../data/holidays/2025.json' with {type:'json'};
import y2026 from '../data/holidays/2026.json' with {type:'json'};
export type HolidayDay={name:string;date:string;isOffDay:boolean};
export type HolidayDocument={year:number;papers:string[];days:HolidayDay[]};
export type Holiday={name:string;kind:'rest'|'work'};
const object=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
export function validHolidayDate(s:unknown):s is string{if(typeof s!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(s))return false;const d=new Date(s+'T12:00:00Z');return Number.isFinite(+d)&&d.toISOString().slice(0,10)===s;}
function annual(year:number,days:unknown,papers:string[]):HolidayDocument{if(!Number.isInteger(year)||year<1900||year>2100||!Array.isArray(days)||days.length<10||days.length>100)throw Error('未取得完整年度放假安排。');const seen=new Set();for(const d of days){if(!object(d)||!validHolidayDate(d.date)||d.date<`${year-1}-12-01`||d.date>`${year}-12-31`||typeof d.name!=='string'||!d.name.trim()||d.name.length>32||/[<>\x00-\x1f]/.test(d.name)||typeof d.isOffDay!=='boolean'||seen.has(d.date))throw Error('放假条目存在错误或重复。');seen.add(d.date);}const all=(days as HolidayDay[]).filter(d=>d.isOffDay).map(d=>d.name).join('、');if(!['元旦','春节','清明','劳动','端午','中秋','国庆'].every(s=>all.includes(s))||!days.some(d=>!d.isOffDay))throw Error('年度放假或补班信息不完整。');return {year,papers,days:(days as HolidayDay[]).map(d=>({...d})).sort((a,b)=>a.date.localeCompare(b.date))};}
export function parseStaticHoliday(value:unknown,year:number):HolidayDocument{if(!object(value)||value.year!==year||!Array.isArray(value.papers)||!value.papers.length||value.papers.length>10||!value.papers.every(v=>{try{const u=new URL(String(v));return ['https:','http:'].includes(u.protocol)&&!u.username&&!u.password&&(u.hostname==='gov.cn'||u.hostname.endsWith('.gov.cn'));}catch{return false;}}))throw Error('静态安排缺少正确年度或公告引用。');return annual(year,value.days,value.papers);}
export function parseApiHoliday(value:unknown,year:number){if(!object(value)||value.code!==0||!object(value.holiday))throw Error('备用接口未取得年度安排。');return annual(year,Object.entries(value.holiday).map(([key,v])=>{if(!object(v)||!/^\d{2}-\d{2}$/.test(key)||v.date!==`${year}-${key}`)throw Error('备用接口日期不匹配。');return {name:v.name,date:v.date,isOffDay:v.holiday};}),[]);}
export const BUNDLED_HOLIDAYS=[y2023,y2024,y2025,y2026].map(d=>parseStaticHoliday(d,d.year));
export function scheduleSignature(d:HolidayDocument){return d.days.map(d=>d.date+':'+Number(d.isOffDay)).join('|');}
export function holidayIndex(year:number,documents:HolidayDocument[]){const result:Record<string,Holiday>=Object.create(null);for(const doc of documents.slice().sort((a,b)=>a.year-b.year))for(const d of doc.days)if(d.date.startsWith(year+'-'))result[d.date]={name:d.name,kind:d.isOffDay?'rest':'work'};return result;}
export function localHoliday(date:string){return validHolidayDate(date)?holidayIndex(Number(date.slice(0,4)),BUNDLED_HOLIDAYS)[date]??null:null;}
