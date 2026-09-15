'use client';
import {useState,useLayoutEffect,useEffect,useRef} from 'react';
import {ChevronLeft,ChevronRight,Search,Palette,MessageCircle,BookHeart,Shield,Database,CloudSun,Gift,UserRound,SlidersHorizontal,X} from 'lucide-react';
import '@/app/interaction-refinement.css';
import {DialogTitle,DialogDescription} from '@/components/ui/dialog';
const sections=[
 {group:'日常与外观',items:[{id:'appearance',title:'外观与字号',hint:'四季、昼夜、背景与字体大小',Icon:Palette},{id:'chat-style',title:'头像与聊天样式',hint:'我的头像、聊天气泡与字号',Icon:MessageCircle},{id:'celebrations',title:'生日与纪念日',hint:'生日祝福与相伴日期',Icon:Gift}]},
 {group:'聊天与记忆',items:[{id:'model',title:'聊天模型',hint:'服务地址、模型与连接',Icon:SlidersHorizontal},{id:'memory',title:'长期聊天记忆',hint:'摘要、重要约定与聊天备份',Icon:BookHeart},{id:'luke-memory',title:'夏彦的记忆',hint:'角色资料与内容范围',Icon:UserRound},{id:'context',title:'聊天近况',hint:'选择向对话分享的近况',Icon:MessageCircle}]},
 {group:'数据与权限',items:[{id:'general',title:'日常与数据',hint:'称呼、备份与恢复',Icon:Database},{id:'weather',title:'天气与位置',hint:'天气服务与定位',Icon:CloudSun},{id:'permissions',title:'权限与隐私',hint:'何时申请、数据去向',Icon:Shield},{id:'disclaimer',title:'关于与免责声明',hint:'版本、资料来源与使用说明',Icon:BookHeart}]}];
export function SettingsNavigation({value,onChange}:{value:string;onChange:(value:string)=>void}) {
 const [query,setQuery]=useState('');
 const directory=useRef<HTMLDivElement>(null), heading=useRef<HTMLHeadingElement>(null);
 const lastItem=useRef(''), scroll=useRef(0), previous=useRef(value);
 const current=sections.flatMap(group=>group.items).find(item=>item.id===value);
 const needle=query.normalize('NFKC').trim().toLocaleLowerCase();
 const groups=sections.map(group=>({...group,items:group.items.filter(item=>`${item.title} ${item.hint}`.toLocaleLowerCase().includes(needle))}));
 const count=groups.reduce((total,group)=>total+group.items.length,0);
 useLayoutEffect(()=>{
  const body=document.querySelector<HTMLElement>('.settings [data-slot=dialog-scroll-body]');
  if(previous.current!==value){
   if(value==='index'){
    if(body)body.scrollTop=scroll.current;
    directory.current?.querySelector<HTMLButtonElement>(`[data-settings-id="${lastItem.current}"]`)?.focus({preventScroll:true});
   } else {
    if(body)body.scrollTop=0;
    heading.current?.focus({preventScroll:true});
   }
  }
  previous.current=value;
 },[value]);
 useEffect(()=>{
  const escape=(event:KeyboardEvent)=>{
   if(event.key!=='Escape'||event.isComposing||event.defaultPrevented)return;
   const top=[...document.querySelectorAll<HTMLElement>('[role=dialog]')].filter(node=>node.getClientRects().length&&!node.hasAttribute('data-ending-style')).at(-1);
   if(!top?.classList.contains('settings'))return;
   if(value!=='index'){
    event.preventDefault();event.stopImmediatePropagation();onChange('index');
   } else if(query && directory.current?.contains(document.activeElement)) {
    event.preventDefault();event.stopImmediatePropagation();setQuery('');
   }
  };
  document.addEventListener('keydown',escape,true);
  return()=>document.removeEventListener('keydown',escape,true);
 },[value,query,onChange]);
 function enter(id:string){
  scroll.current=document.querySelector<HTMLElement>('.settings [data-slot=dialog-scroll-body]')?.scrollTop??0;
  lastItem.current=id;onChange(id);
 }
 if(value!=='index')return null;
 return <div ref={directory} className="settings-directory">
  <div className="settings-search"><Search size={18} aria-hidden="true"/><input aria-label="搜索设置" placeholder="搜索设置" value={query} onChange={event=>setQuery(event.target.value)}/>{query&&<button type="button" className="settings-clear" aria-label="清除设置搜索" onClick={()=>{setQuery('');directory.current?.querySelector('input')?.focus();}}><X size={17}/></button>}</div>
  {needle&&<p className="settings-search-count" role="status">{count ? `找到 ${count} 项设置` : '没有匹配的设置'}</p>}
  <div role="tablist" aria-label="设置目录" className="settings-sections" aria-orientation="vertical" onKeyDown={event=>{
   if(!['ArrowDown','ArrowUp','Home','End'].includes(event.key))return;
   const buttons=[...event.currentTarget.querySelectorAll<HTMLButtonElement>('[role=tab]')];
   const index=buttons.indexOf(document.activeElement as HTMLButtonElement);if(index<0)return;
   event.preventDefault();
   const next=event.key==='Home'?0:event.key==='End'?buttons.length-1:(index+(event.key==='ArrowDown'?1:-1)+buttons.length)%buttons.length;
   buttons[next]?.focus();
  }}>
   {groups.filter(group=>group.items.length).map(group=><section key={group.group}><h3>{group.group}</h3>{group.items.map(({id,title,hint,Icon})=><button role="tab" type="button" data-settings-id={id} aria-label={title} aria-selected={false} key={id} onClick={()=>enter(id)}><span className="settings-item-icon"><Icon size={20}/></span><span><strong>{title}</strong><small>{hint}</small></span><ChevronRight size={18}/></button>)}</section>)}
  </div>
  {!count&&<div className="settings-no-results"><p>试试“背景”“记忆”或“备份”</p><button type="button" className="soft-button" onClick={()=>setQuery('')}>查看全部设置</button></div>}
 </div>;
}
export function PermissionsPanel(){return <div className="settings-stack permission-cards"><section><h3>图片与文件</h3><p>只在选择图片、导入或导出时打开系统文件选择器。不索取整个相册、存储空间或摄像头权限。</p></section><section><h3>天气位置</h3><p>点击定位后才申请位置权限，用于获取天气。可以拒绝并手动填写坐标，不影响聊天、手记与计时。</p></section><section><h3>聊天与自动记忆</h3><p>聊天和记忆整理会把相关聊天内容发送到你配置的模型服务，产生该服务的用量。API 密钥不包含在完整备份中。重要约定可在“长期聊天记忆”中手动固定。</p></section><section><h3>按钮触感与系统提醒</h3><p>触感遵循手机设置。闹钟与倒计时交给系统时钟确认，不在后台自行常驻或索取通知、精确闹钟权限。</p></section></div>;}

export function SettingsHeader({value,onBack}:{value:string;onBack:()=>void}){
 const title=sections.flatMap(s=>s.items).find(i=>i.id===value)?.title??'设置';
 const ref=useRef<HTMLHeadingElement>(null);useEffect(()=>{ref.current?.focus({preventScroll:true});},[value]);
 return <header className="settings-fixed-head">{value!=='index'&&<button type="button" className="round" aria-label="返回设置目录" onClick={onBack}><ChevronLeft size={22}/></button>}<div><DialogTitle ref={ref} tabIndex={-1}>{title}</DialogTitle><DialogDescription className={value==='index'?'':'sr-only'}>{value==='index'?'属于你的日常':'设置子页面'}</DialogDescription></div></header>;
}
