import {coordinates,validWeatherDate,type Weather,type WeatherConfig} from './weather.ts';
export const WEATHER_CACHE_KEY='luke-weather-cache-v210';
export function weatherIdentity(c:WeatherConfig){const p=coordinates(c.lat,c.lon);return `open-meteo:${p.latitude.toFixed(4)},${p.longitude.toFixed(4)}`;}
const numeric=(v:unknown,a:number,b:number):v is number=>typeof v==='number'&&Number.isFinite(v)&&v>=a&&v<=b;
const kinds=['sun','cloud','rain','snow','storm','fog'];
export function validCachedWeather(value:unknown,now=Date.now()):value is Weather{const w=value as Weather;if(!w||w.source!=='Open-Meteo'||!numeric(w.temperature,-100,70)||!kinds.includes(w.kind)||typeof w.label!=='string'||w.label.length>40||!numeric(w.fetchedAt,now-7*86400000,now+300000)||!numeric(w.observedAt,0,now+300000)||typeof w.forecast!=='string'||w.forecast.length>500||typeof w.alert!=='string'||w.alert.length>500||typeof w.rainSoon!=='boolean'||(w.utcOffset!==undefined&&!numeric(w.utcOffset,-86400,86400))||(w.isDay!==undefined&&typeof w.isDay!=='boolean')||(w.apparent!==undefined&&!numeric(w.apparent,-100,70))||(w.humidity!==undefined&&!numeric(w.humidity,0,100))||(w.wind!==undefined&&!numeric(w.wind,0,500)))return false;if(w.days!==undefined&&(!Array.isArray(w.days)||w.days.length>7||w.days.some(d=>!d||typeof d!=='object'||!validWeatherDate(d.date)||!numeric(d.temperature,-100,70)||!kinds.includes(d.kind)||typeof d.label!=='string'||d.label.length>40||(d.minimum!==undefined&&(!numeric(d.minimum,-100,70)||d.minimum>d.temperature)))))return false;if(w.hours!==undefined&&(!Array.isArray(w.hours)||w.hours.length>24||w.hours.some(h=>!h||typeof h!=='object'||!numeric(h.at,0,now+8*86400000)||!numeric(h.temperature,-100,70)||!kinds.includes(h.kind)||typeof h.label!=='string'||h.label.length>40||(h.rain!==undefined&&!numeric(h.rain,0,100)))))return false;if(w.days&&new Set(w.days.map(d=>d.date)).size!==w.days.length)return false;if(w.hours&&new Set(w.hours.map(h=>h.at)).size!==w.hours.length)return false;return true;}
export function readWeatherCache(config:WeatherConfig,now=Date.now()):Weather|null{try{const raw=localStorage.getItem(WEATHER_CACHE_KEY);if(!raw||raw.length>150000)return null;const all=JSON.parse(raw),value=all[weatherIdentity(config)];return validCachedWeather(value,now)?value:null;}catch{return null;}}
export function saveWeatherCache(config:WeatherConfig,w:Weather){if(!validCachedWeather(w))return false;try{let all:Record<string,Weather>={};try{const parsed=JSON.parse(localStorage.getItem(WEATHER_CACHE_KEY)||'{}');if(parsed&&typeof parsed==='object'&&!Array.isArray(parsed))all=parsed;}catch{}all[weatherIdentity(config)]=w;const bounded=Object.fromEntries(Object.entries(all).filter(([,v])=>validCachedWeather(v)).sort(([,a],[,b])=>b.fetchedAt-a.fetchedAt).slice(0,6));localStorage.setItem(WEATHER_CACHE_KEY,JSON.stringify(bounded));return true;}catch{return false;}}
export const temperatureValue=(value:number,unit:'C'|'F')=>Math.round(unit==='C'?value:value*9/5+32);
export function weatherTime(ms:number,offset=0){const d=new Date(ms+offset*1000);return String(d.getUTCHours()).padStart(2,'0')+':'+String(d.getUTCMinutes()).padStart(2,'0');}

/** Labels use the selected city's calendar date, not the phone timezone or item index. */
export function weatherDate(ms:number,offset=0){const d=new Date(ms+offset*1000);return Number.isFinite(d.getTime())?d.toISOString().slice(0,10):'';}
export function forecastDayLabel(date:string,now=Date.now(),offset=0){
 if(!validWeatherDate(date))return '—';
 if(date===weatherDate(now,offset))return '今天';
 if(date===weatherDate(now+86400000,offset))return '明天';
 return ['周日','周一','周二','周三','周四','周五','周六'][new Date(date+'T00:00:00Z').getUTCDay()];
}

export function weatherObservationLabel(at:number,now=Date.now(),offset=0){const date=weatherDate(at,offset);return date&&date!==weatherDate(now,offset)?date+' '+weatherTime(at,offset):weatherTime(at,offset);}
