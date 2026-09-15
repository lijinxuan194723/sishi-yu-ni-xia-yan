'use client';
import {useEffect,useRef,useState} from 'react';
import {MapPin,PlugZap,CheckCircle2,PenLine} from 'lucide-react';
import {complete,type ModelConfig} from '@/lib/model';
import {locate} from '@/lib/location';
import {fetchWeather} from '@/lib/weather';
import {useSettingsDraft} from '@/hooks/use-settings-draft';
import type {useConnections} from '@/components/connections';
type Connections=ReturnType<typeof useConnections>;

function DraftStatus({dirty,onDiscard}:{dirty:boolean;onDiscard:()=>void}){
 return <div className="settings-form-status" data-unsaved={dirty}>
  {dirty?<PenLine size={16} aria-hidden="true"/>:<CheckCircle2 size={16} aria-hidden="true"/>}
  <span>{dirty?'尚未保存 · 返回设置不会丢失本次输入':'当前使用的设置'}</span>
  {dirty&&<button type="button" onClick={onDiscard}>撤销本次修改</button>}
 </div>;
}

export function ModelSettings({connections}:{connections:Connections}){
 const draft=useSettingsDraft('model',connections.model), model=draft.value;
 const [notice,setNotice]=useState(''),[testing,setTesting]=useState(false);
 const task=useRef<AbortController|null>(null),timer=useRef<ReturnType<typeof setTimeout>|null>(null);
 useEffect(()=>()=>{task.current?.abort();task.current=null;if(timer.current)clearTimeout(timer.current);},[]);
 function cancel(message='已取消测试'){
  task.current?.abort();task.current=null;if(timer.current)clearTimeout(timer.current);
  timer.current=null;setTesting(false);setNotice(message);
 }
 function patch(value:Partial<ModelConfig>){cancel('');draft.update(previous=>({...previous,...value}));}
 async function test(){
  if(task.current)return;
  const controller=new AbortController();task.current=controller;setTesting(true);setNotice('正在测试连接');
  timer.current=setTimeout(()=>{if(task.current===controller)cancel('连接测试超时，输入已保留。');},20000);
  try{
   await complete(model,[{role:'user',content:'请只回复：连接成功。'}],controller.signal,100);
   if(!controller.signal.aborted)setNotice(draft.dirty?'连接成功，保存后应用设置。':'连接成功');
  }catch(error){if(!controller.signal.aborted)setNotice(error instanceof Error?error.message:'连接失败');}
  finally{if(task.current===controller){task.current=null;if(timer.current)clearTimeout(timer.current);timer.current=null;setTesting(false);}}
 }
 return <form className="settings-stack" data-connection-form="model" onSubmit={event=>{
  event.preventDefault();if(testing||!connections.loaded)return;
  try{connections.apply(model,connections.weather);draft.committed();setNotice('模型设置已保存');}
  catch(error){setNotice(error instanceof Error?error.message:'设置未保存');}
 }}>
  <h3><PlugZap size={18}/> 对话连接</h3>
  <DraftStatus dirty={draft.dirty} onDiscard={()=>{cancel('');draft.discard();}}/>
  <label>接口地址<input type="url" required aria-label="模型接口地址" placeholder="https://服务地址/v1" autoCapitalize="none" spellCheck={false} value={model.baseUrl} onChange={event=>patch({baseUrl:event.target.value})}/></label>
  <label>模型名称<input required maxLength={150} aria-label="模型名称" autoCapitalize="none" spellCheck={false} value={model.model} onChange={event=>patch({model:event.target.value})}/></label>
  <label>API Key<input type="password" aria-label="模型密钥" autoComplete="off" autoCapitalize="none" spellCheck={false} value={model.key} onChange={event=>patch({key:event.target.value})}/></label>
  <details className="settings-advanced"><summary>备用模型</summary><div className="settings-stack">
   <label>备用接口地址<input type="url" value={model.fallback?.baseUrl??''} onChange={event=>patch({fallback:{...model.fallback,baseUrl:event.target.value,model:model.fallback?.model??'',key:model.fallback?.key??''}})}/></label>
   <label>备用模型名称<input maxLength={150} value={model.fallback?.model??''} onChange={event=>patch({fallback:{...model.fallback,baseUrl:model.fallback?.baseUrl??'',model:event.target.value,key:model.fallback?.key??''}})}/></label>
   <label>备用密钥<input type="password" autoComplete="off" value={model.fallback?.key??''} onChange={event=>patch({fallback:{...model.fallback,baseUrl:model.fallback?.baseUrl??'',model:model.fallback?.model??'',key:event.target.value}})}/></label>
   <button type="button" className="text-button" onClick={()=>patch({fallback:undefined})}>重置备用输入</button>
  </div></details>
  <p className="setting-caption">密钥保存在本机，不随完整备份导出。聊天与记忆整理发送到所选服务，按该服务产生用量。</p>
  <div className="settings-form-actions">
   {testing?<button type="button" className="soft-button" onClick={()=>cancel()}>取消连接测试</button>:<button type="button" className="soft-button" disabled={!connections.loaded||!model.baseUrl||!model.model||!model.key} onClick={()=>void test()}>测试连接</button>}
   <button type="submit" className="primary" data-haptic="confirm" disabled={testing||!connections.loaded||!model.baseUrl||!model.model||!model.key}>保存模型设置</button>
  </div>
  <p className="settings-request-notice" role="status">{notice}</p>
 </form>;
}

export function WeatherSettings({connections}:{connections:Connections}){
 const draft=useSettingsDraft('weather',connections.weather),weather=draft.value;
 const [locating,setLocating]=useState(false),[notice,setNotice]=useState('');
 const task=useRef<AbortController|null>(null);
 useEffect(()=>()=>{task.current?.abort();task.current=null;},[]);
 function cancel(message='已取消操作'){task.current?.abort();task.current=null;setLocating(false);setNotice(message);}
 function patch(value:Partial<typeof weather>){cancel('');draft.update(previous=>({...previous,...value}));}
 async function position(){
  if(task.current)return;
  const controller=new AbortController();task.current=controller;setLocating(true);setNotice('正在获取位置');
  try{
   const result=await locate(controller.signal,false,undefined,false);
   if(!controller.signal.aborted){draft.update(previous=>({...previous,...result}));setNotice(result.warning||'已填入 '+result.place+'，保存后用于天气。');}
  }catch(error){if(!controller.signal.aborted)setNotice(error instanceof Error?error.message:'定位失败，可以手动填写位置。');}
  finally{if(task.current===controller){task.current=null;setLocating(false);}}
 }
 return <form className="settings-stack" data-connection-form="weather" onSubmit={event=>{
  event.preventDefault();if(locating||!connections.loaded)return;
  try{connections.saveWeather(weather);draft.committed();setNotice('天气位置已保存');}
  catch(error){setNotice(error instanceof Error?error.message:'位置未保存');}
 }}>
  <DraftStatus dirty={draft.dirty} onDiscard={()=>{cancel('');draft.discard();}}/>
  <label>地点名称<input maxLength={50} value={weather.place} onChange={event=>patch({place:event.target.value})}/></label>
  <div className="coordinate-inputs"><label>纬度<input type="number" step="any" min={-90} max={90} required value={weather.lat} onChange={event=>patch({lat:event.target.value})}/></label><label>经度<input type="number" step="any" min={-180} max={180} required value={weather.lon} onChange={event=>patch({lon:event.target.value})}/></label></div>
  <p className="setting-caption">点击定位后申请位置权限；也可手动填写坐标。坐标会发给 Open-Meteo，用于当前地区的天气。</p>
  <div className="settings-form-actions">{locating?<button type="button" className="soft-button" onClick={()=>cancel()}>取消操作</button>:<button type="button" className="soft-button" onClick={()=>void position()}><MapPin size={17}/>使用当前位置</button>}</div>
  <label className="settings-toggle"><span>天气小特效</span><input type="checkbox" checked={weather.effects} onChange={event=>patch({effects:event.target.checked})}/></label>
  <button type="button" className="soft-button" disabled={locating||!connections.loaded} onClick={async()=>{if(task.current)return;const controller=new AbortController();task.current=controller;setLocating(true);setNotice('正在测试天气连接');try{const result=await fetchWeather(weather,AbortSignal.any([controller.signal,AbortSignal.timeout(20000)]));if(!controller.signal.aborted)setNotice(`连接成功 · ${result.label} ${Math.round(result.temperature)}° · ${result.days?.length??0} 天天气`);}catch(e){if(!controller.signal.aborted)setNotice(e instanceof Error?e.message:'天气连接失败');}finally{if(task.current===controller){task.current=null;setLocating(false);}}}}>测试天气连接</button>
  <button type="submit" className="primary" data-haptic="confirm" disabled={locating||!connections.loaded}>保存天气设置</button>
  <p className="settings-request-notice" role="status">{notice}</p>
 </form>;
}
