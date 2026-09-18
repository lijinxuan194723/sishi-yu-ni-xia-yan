import {localHoliday} from './holiday-data.ts';
export type {Holiday} from './holiday-data.ts';
export const HOLIDAY_SOURCE='https://www.beijing.gov.cn/fuwu/bmfw/sy/jrts/202511/t20251104_4258838.html';
export const HOLIDAY_YEAR=2026;
export const holidayFor=localHoliday;
export function parseCalendarDate(value:string){
 if(!/^\d{4}-\d{2}-\d{2}$/.test(value))return null;
 const [y,m,d]=value.split('-').map(Number);if(y<1900||y>2100)return null;const date=new Date(y,m-1,d,12);
 return date.getFullYear()===y&&date.getMonth()===m-1&&date.getDate()===d?date:null;
}
