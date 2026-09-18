'use client';
import {useEffect,useRef,useState,memo,type ReactNode} from 'react';
import {ImagePlus,RotateCcw,Check} from 'lucide-react';
import {BUBBLES,COMPANIONS,DEFAULT_DISPLAY,prepareAvatar,type DisplayPreferences} from '@/lib/display-preferences';
import {useDisplayPreferences} from '@/hooks/use-display-preferences';
import './chat-compat.css';
export const ChatAvatar=memo(function ChatAvatar({mine,preferences,name}:{mine:boolean;preferences:DisplayPreferences;name:string}){
 const src=mine?preferences.avatarMine:preferences.avatarLuke,fallback=mine?COMPANIONS.dog:COMPANIONS.cat;
 return <img key={src} className="chat-avatar" data-personal-photo={src.startsWith('data:')} src={src} width="36" height="36" alt={mine?`${name}的头像`:'夏彦的头像'} decoding="async" onError={event=>{if(event.currentTarget.dataset.fallback)return;event.currentTarget.dataset.fallback='true';event.currentTarget.src=fallback;}}/>;
});
export function ChatBubble({mine=false,preferences,children}:{mine?:boolean;preferences:DisplayPreferences;children:ReactNode}){
 return <div className="bubble-frame bubble-compat210" data-skin={mine?preferences.bubbleMine:preferences.bubbleLuke}>{children}</div>;
}
export function FontSettings({chatOnly=false}:{chatOnly?:boolean}){
 const {preferences:p,update,error}=useDisplayPreferences();
 return <section className="preference-card font-controls" aria-label={chatOnly?'聊天字号':'文字大小'}><h3>{chatOnly?'聊天文字':'文字大小'}</h3>{!chatOnly&&<label>全局字号 <output>{Math.round(p.scale*100)}%</output><input aria-label="全局字号" type="range" min="75" max="160" step="5" value={Math.round(p.scale*100)} onChange={e=>update({scale:Number(e.target.value)/100})}/></label>}<label>聊天字号 <output>{p.chatSize} px</output><input aria-label="聊天字号" type="range" min="10" max="32" step="1" value={p.chatSize} onChange={e=>update({chatSize:Number(e.target.value)})}/></label><p className="font-sample">今天，也想听你说说身边的小事。</p><button type="button" className="text-button" onClick={()=>update(chatOnly?{chatSize:DEFAULT_DISPLAY.chatSize}:{scale:DEFAULT_DISPLAY.scale,chatSize:DEFAULT_DISPLAY.chatSize})}><RotateCcw size={15}/>恢复默认字号</button>{error&&<p role="alert">{error}</p>}</section>;
}
export function ChatAppearance(){
 const {preferences:p,update,error}=useDisplayPreferences(),[side,setSide]=useState<'mine'|'luke'>('mine'),[notice,setNotice]=useState(''),[busy,setBusy]=useState(false),selection=useRef<AbortController|null>(null);
 useEffect(()=>()=>selection.current?.abort(),[]);
 const mine=side==='mine',name=mine?'我':'夏彦',src=mine?p.avatarMine:p.avatarLuke,skin=mine?p.bubbleMine:p.bubbleLuke;
 function cancel(){selection.current?.abort();selection.current=null;setBusy(false);}
 function avatar(value:string){cancel();update(mine?{avatarMine:value}:{avatarLuke:value});}
 async function choose(file:File){cancel();const controller=new AbortController(),target=side;selection.current=controller;setBusy(true);setNotice('');try{const value=await prepareAvatar(file,controller.signal);if(!controller.signal.aborted&&selection.current===controller)update(target==='mine'?{avatarMine:value}:{avatarLuke:value});}catch(e){if(!controller.signal.aborted)setNotice(e instanceof Error?e.message:'头像处理失败。');}finally{if(selection.current===controller){setBusy(false);selection.current=null;}}}
 return <div className="settings-stack chat-appearance chat-compat-settings"><div className="chat-person-switch" role="group" aria-label="分别设置聊天外观">{(['mine','luke'] as const).map(v=><button key={v} type="button" aria-pressed={side===v} onClick={()=>{cancel();setNotice('');setSide(v);}}>{v==='mine'?'我的外观':'夏彦的外观'}</button>)}</div>
 <section className="preference-card"><h3>{name}的头像</h3><div className="avatar-personal"><ChatAvatar mine={mine} preferences={p} name="我"/><label className="avatar-upload"><ImagePlus size={17}/><span>{busy?'正在处理…':'从相册选择'}</span><input type="file" accept="image/jpeg,image/png,image/webp" aria-label={mine?'选择我的头像':'选择夏彦的头像'} onChange={e=>{const f=e.currentTarget.files?.[0];e.currentTarget.value='';if(f)void choose(f);}}/></label><button type="button" className="text-button" onClick={()=>avatar(mine?DEFAULT_DISPLAY.avatarMine:DEFAULT_DISPLAY.avatarLuke)}>恢复默认头像</button></div><div className="avatar-choices" aria-label={`${name}的内置头像`}>{(Object.keys(COMPANIONS) as Array<keyof typeof COMPANIONS>).map((key,i)=><button type="button" key={key} aria-label={`${name}：${['小狗','蜂蜜吐司','猫耳'][i]}头像`} aria-pressed={src===COMPANIONS[key]} onClick={()=>avatar(COMPANIONS[key])}><img src={COMPANIONS[key]} alt="" width="48" height="48"/></button>)}</div></section>
 <section className="preference-card"><h3>{name}的聊天气泡</h3><div className="bubble-choices">{BUBBLES.map(b=><button type="button" key={b.id} aria-label={`${name}的气泡：${b.name}`} aria-pressed={skin===b.id} onClick={()=>update(mine?{bubbleMine:b.id}:{bubbleLuke:b.id})}>{b.image?<img src={b.image} alt="" width="280" height="150" loading="lazy"/>:<span className="bubble-plain-sample">把今天说给我听。</span>}<span className="bubble-choice-label">{b.name}{skin===b.id&&<Check size={16}/>}</span></button>)}</div></section><FontSettings chatOnly/>{(notice||error)&&<p role="alert">{notice||error}</p>}</div>;
}
