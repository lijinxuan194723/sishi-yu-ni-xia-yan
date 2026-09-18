'use client';
import {SeasonWeather} from './season-weather';
import {readWeatherCache,saveWeatherCache} from '@/lib/weather-cache';
import {useCallback,useEffect,useRef,useState} from 'react';
import {complete,completionURL,type ModelConfig} from '@/lib/model';
import {defaultModel,resolveModel,validModel} from '@/lib/default-model';
import {coordinates,fetchWeather,isFresh,weatherAdvice,type Weather,type WeatherConfig} from '@/lib/weather';

import {locate} from '@/lib/location';
import {Sun,CloudRain,Cloud,CloudSnow,CloudLightning,CloudFog,RefreshCw,MapPin,Settings} from 'lucide-react';
export function useConnections(){const [model,setModel]=useState<ModelConfig>({baseUrl:'',model:'',key:''});const [weather,setWeather]=useState<WeatherConfig>({provider:'open-meteo',lat:'',lon:'',place:'我的位置',key:'',effects:true});const [loaded,setLoaded]=useState(false);useEffect(()=>{const builtIn=defaultModel();setModel(resolveModel(undefined,builtIn));try{const settings=JSON.parse(localStorage.getItem('luke-connections-v1')||'{}');const persistent=JSON.parse(localStorage.getItem('luke-model-credentials-v1')||'null');const legacy=settings.model?{...settings.model,key:sessionStorage.getItem('luke-model-key')||''}:undefined;setModel(resolveModel(validModel(persistent)?persistent:legacy,builtIn));if(settings.weather){const clean={...settings.weather,provider:'open-meteo' as const,key:''};setWeather(clean);localStorage.setItem('luke-connections-v1',JSON.stringify({...settings,weather:{...clean,key:undefined}}));}sessionStorage.removeItem('luke-weather-key');}catch{}setLoaded(true);},[]);
 function apply(m:ModelConfig,w:WeatherConfig){if(m.baseUrl)completionURL(m.baseUrl);if(m.fallback?.baseUrl)completionURL(m.fallback.baseUrl);if(w.lat||w.lon)coordinates(w.lat,w.lon);const resolved=resolveModel(m,defaultModel());const keys=['luke-model-credentials-v1','luke-connections-v1'],old=keys.map(k=>localStorage.getItem(k));try{localStorage.setItem(keys[0],JSON.stringify(resolved));localStorage.setItem(keys[1],JSON.stringify({model:{baseUrl:resolved.baseUrl,model:resolved.model},weather:{...w,provider:'open-meteo',key:undefined}}));}catch(error){let failed=false;keys.forEach((k,i)=>{try{if(old[i]===null)localStorage.removeItem(k);else localStorage.setItem(k,old[i]!);}catch{failed=true;}});if(failed)throw Error('设置写入中断，请重新打开设置核对；未显示保存成功。');throw error;}try{sessionStorage.setItem('luke-model-key',resolved.key);sessionStorage.removeItem('luke-weather-key');}catch{}setModel(resolved);setWeather({...w,provider:'open-meteo',key:''});}
 function saveWeather(next:WeatherConfig){coordinates(next.lat,next.lon);const settings=JSON.parse(localStorage.getItem('luke-connections-v1')||'{}');localStorage.setItem('luke-connections-v1',JSON.stringify({...settings,weather:{...next,key:undefined}}));try{sessionStorage.removeItem('luke-weather-key');}catch{/* Non-authoritative legacy session cleanup must not turn a saved preference into a false failure. */}setWeather({...next,provider:'open-meteo',key:''});}
 return {model,weather,loaded,apply,saveWeather};}
export function useWeather(config:WeatherConfig,loaded:boolean){const [weather,setWeather]=useState<Weather|null>(null),[error,setError]=useState(''),[loading,setLoading]=useState(false),[clock,setClock]=useState(Date.now());const request=useRef<AbortController|null>(null),shown=useRef<Weather|null>(null);
 const refresh=useCallback(async()=>{if(!loaded||!config.lat||!config.lon||request.current||document.hidden)return;if(navigator.onLine===false){setError('当前离线，请联网后刷新。');return;}const controller=new AbortController();request.current=controller;setLoading(true);setError('');try{const result=await fetchWeather(config,AbortSignal.any([controller.signal,AbortSignal.timeout(20000)]));if(!controller.signal.aborted&&request.current===controller){shown.current=result;setWeather(result);setClock(Date.now());saveWeatherCache(config,result);}}catch(e){if(!controller.signal.aborted&&request.current===controller)setError(e instanceof Error?e.message:'天气获取失败，请重试。');}finally{if(request.current===controller){request.current=null;setLoading(false);}}},[config.lat,config.lon,loaded]);
 useEffect(()=>{
  request.current?.abort();request.current=null;const cached=readWeatherCache(config);shown.current=cached;setWeather(cached);setError('');setLoading(false);setClock(Date.now());
  let tick:ReturnType<typeof setInterval>|undefined;
  const wake=()=>{
   clearInterval(tick);tick=undefined;
   if(document.hidden||navigator.onLine===false){request.current?.abort();request.current=null;setLoading(false);if(!document.hidden&&navigator.onLine===false)setError('当前离线，请联网后刷新。');return;}
   setClock(Date.now());if(!shown.current||!isFresh(shown.current))void refresh();
   tick=setInterval(()=>{setClock(Date.now());if(!shown.current||!isFresh(shown.current))void refresh();},5*60000);
  };
  wake();document.addEventListener('visibilitychange',wake);window.addEventListener('online',wake);window.addEventListener('offline',wake);
  return()=>{request.current?.abort();request.current=null;clearInterval(tick);document.removeEventListener('visibilitychange',wake);window.removeEventListener('online',wake);window.removeEventListener('offline',wake);};
 },[refresh]);
 const fresh=weather&&isFresh(weather,clock)&&!error;return {weather,error,loading,refresh,fresh,context:fresh?`${config.place}：${weather.label}，${weather.temperature}°C。${weatherAdvice(weather)} ${weather.forecast}；${weather.alert}。数据时间 ${new Date(weather.observedAt).toISOString()}`:''};}
export function WeatherCard({state,config,configure}:{state:ReturnType<typeof useWeather>;config:WeatherConfig;configure:()=>void}){return <SeasonWeather state={state} config={config} configure={configure}/>;}
