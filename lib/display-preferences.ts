export const DISPLAY_KEY='luke-display-v205';
export const COMPANIONS={dog:'/images/companions/dog.webp',toast:'/images/companions/toast.webp',cat:'/images/companions/cat.webp'} as const;
export const BUBBLES=[{id:'plain',name:'四季',image:''},{id:'tea',name:'暖茶',image:'/images/chat-skins/tea.webp'},{id:'sunflower',name:'向阳',image:'/images/chat-skins/sunflower.webp'},{id:'hearts',name:'心意',image:'/images/chat-skins/hearts.webp'}] as const;
export type Bubble=typeof BUBBLES[number]['id'];
export type DisplayPreferences={version:1;scale:number;chatSize:number;bubble:Bubble;avatar:string;lukeAvatar:'toast'|'cat'};
export const DEFAULT_DISPLAY:DisplayPreferences={version:1,scale:1,chatSize:16,bubble:'tea',avatar:COMPANIONS.dog,lukeAvatar:'cat'};
export function validAvatar(value:unknown):value is string{return typeof value==='string'&&(Object.values(COMPANIONS).includes(value as typeof COMPANIONS[keyof typeof COMPANIONS])||value.length<=300000&&/^data:image\/(?:png|jpeg|webp);base64,[A-Za-z0-9+/]+=*$/.test(value));}
export function parseDisplay(value:unknown):DisplayPreferences{
 const p=(value&&typeof value==='object'?value:{}) as Partial<DisplayPreferences>;
 return {version:1,scale:typeof p.scale==='number'&&Number.isFinite(p.scale)?Math.max(.85,Math.min(1.3,Math.round(p.scale*100)/100)):1,chatSize:typeof p.chatSize==='number'&&Number.isFinite(p.chatSize)?Math.max(13,Math.min(24,Math.round(p.chatSize))):16,bubble:BUBBLES.some(b=>b.id===p.bubble)?p.bubble!:DEFAULT_DISPLAY.bubble,avatar:validAvatar(p.avatar)?p.avatar:DEFAULT_DISPLAY.avatar,lukeAvatar:p.lukeAvatar==='toast'?'toast':'cat'};
}
export function readDisplay():DisplayPreferences{try{return parseDisplay(JSON.parse(localStorage.getItem(DISPLAY_KEY)??'null'));}catch{return DEFAULT_DISPLAY;}}
export function applyDisplay(p:DisplayPreferences){const root=document.documentElement;root.style.fontSize=`${16*p.scale}px`;root.style.setProperty('--chat-font-size',`${p.chatSize}px`);root.dataset.bubble=p.bubble;}
export function saveDisplay(p:DisplayPreferences){const clean=parseDisplay(p);localStorage.setItem(DISPLAY_KEY,JSON.stringify(clean));applyDisplay(clean);window.dispatchEvent(new Event('luke-display-change'));return clean;}
/** Decode once, crop locally and store a bounded image, never the original multi-megabyte file. */
export async function prepareAvatar(file:File,signal?:AbortSignal):Promise<string>{
 if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>8*1024*1024||file.size===0)throw Error('请选择不超过 8 MB 的 JPG、PNG 或 WebP 图片。');
 if(signal?.aborted)throw new DOMException('已取消','AbortError');
 const url=URL.createObjectURL(file),image=new Image();
 try{image.src=url;await image.decode();if(signal?.aborted)throw new DOMException('已取消','AbortError');
  const {naturalWidth:w,naturalHeight:h}=image;if(!w||!h||w*h>30000000)throw Error('图片尺寸过大，请先缩小图片。');
  const canvas=document.createElement('canvas');canvas.width=canvas.height=224;const context=canvas.getContext('2d');if(!context)throw Error('当前设备无法处理图片。');
  const size=Math.min(w,h);context.drawImage(image,(w-size)/2,(h-size)/2,size,size,0,0,224,224);
  const value=canvas.toDataURL('image/webp',.86);if(!validAvatar(value))throw Error('头像无法保存，请换一张图片。');return value;
 }finally{URL.revokeObjectURL(url);}
}
