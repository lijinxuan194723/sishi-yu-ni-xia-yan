import {weatherCopy} from './home-copy.ts';
import {isAndroid,networkFetch} from './mobile.ts';
export type WeatherDay={date:string;temperature:number;label:string;kind:'sun'|'cloud'|'rain'|'snow'|'storm'|'fog'};
export type Weather={temperature:number;label:string;kind:WeatherDay['kind'];observedAt:number;fetchedAt:number;source:string;forecast:string;rainSoon:boolean;alert:string;days?:WeatherDay[]};
export type WeatherConfig={provider:'open-meteo';lat:string;lon:string;place:string;key:string;effects:boolean;districtFallback?:boolean;districtEndpoint?:string};
export function coordinates(lat:unknown,lon:unknown){if(!['string','number'].includes(typeof lat)||!['string','number'].includes(typeof lon)||String(lat).trim()===''||String(lon).trim()==='')throw Error('请设置地点或经纬度。');const latitude=Number(lat),longitude=Number(lon);if(!Number.isFinite(latitude)||latitude< -90||latitude>90||!Number.isFinite(longitude)||longitude< -180||longitude>180)throw Error('经纬度超出有效范围。');return {latitude,longitude};}
export function meteoURL(latitude:number,longitude:number){return 'https://api.open-meteo.com/v1/forecast?'+new URLSearchParams({latitude:String(latitude),longitude:String(longitude),current:'temperature_2m,weather_code',daily:'weather_code,temperature_2m_max,temperature_2m_min',hourly:'precipitation_probability',forecast_days:'7',forecast_hours:'12',timezone:'auto',timeformat:'unixtime'});}
const codes=new Set([0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99]);
function kindOf(code:number):Weather['kind']{return code>=95?'storm':[71,73,75,77,85,86].includes(code)?'snow':code>=51?'rain':code>=45?'fog':code>=2?'cloud':'sun';}
const labelOf=(code:number)=>({sun:'晴',cloud:code===3?'阴':'多云',rain:'雨',snow:'雪',storm:'雷雨',fog:'雾'})[kindOf(code)];
const temp=(n:unknown):n is number=>typeof n==='number'&&Number.isFinite(n)&&n>=-100&&n<=70;
export function normalizeMeteo(p:any):Weather{
 const c=p?.current;if(!temp(c?.temperature_2m)||!codes.has(c?.weather_code)||!Number.isFinite(c?.time)||c.time<0)throw Error('天气数据不完整。');
 const offset=Number.isFinite(p.utc_offset_seconds)?p.utc_offset_seconds:0;
 const days:WeatherDay[]=[];
 for(let i=0;i<Math.min(7,p.daily?.time?.length??0);i++){
  const raw=p.daily.time[i],code=p.daily.weather_code?.[i],temperature=p.daily.temperature_2m_max?.[i];
  const date=typeof raw==='number'&&Number.isFinite(raw)?new Date((raw+offset)*1000).toISOString().slice(0,10):typeof raw==='string'?raw:'';
  if(!/^\d{4}-\d{2}-\d{2}$/.test(date)||!codes.has(code)||!temp(temperature)||days.some(d=>d.date===date))continue;
  days.push({date,temperature,label:labelOf(code),kind:kindOf(code)});
 }
 const rainSoon=Array.isArray(p.hourly?.time)&&p.hourly.time.some((time:unknown,i:number)=>typeof time==='number'&&time>=c.time&&time<=c.time+10800&&Number.isFinite(p.hourly.precipitation_probability?.[i])&&p.hourly.precipitation_probability[i]>=60&&p.hourly.precipitation_probability[i]<=100);
 return {temperature:c.temperature_2m,kind:kindOf(c.weather_code),label:labelOf(c.weather_code),observedAt:c.time*1000,fetchedAt:Date.now(),source:'Open-Meteo',forecast:rainSoon?'未来三小时降水概率较高，出门记得带伞。':'',rainSoon:Boolean(rainSoon),alert:'',days};
}
export function isFresh(w:Weather,now=Date.now()){return now-w.fetchedAt<15*60000&&w.fetchedAt-now<5*60000&&now-w.observedAt<90*60000&&w.observedAt-now<5*60000;}
export function weatherAdvice(w:Weather,now=new Date()){return weatherCopy(w,now);}
export async function fetchWeather(config:WeatherConfig,signal?:AbortSignal):Promise<Weather>{
 const {latitude,longitude}=coordinates(config.lat,config.lon);
 const response=isAndroid()?await networkFetch(meteoURL(latitude,longitude),{signal}):await fetch('/api/weather',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({lat:config.lat,lon:config.lon}),signal});
 if(!response.ok)throw Error(`天气暂时无法更新（${response.status}），请稍后重试。`);
 const payload=await response.json();return isAndroid()?normalizeMeteo(payload):payload as Weather;
}
