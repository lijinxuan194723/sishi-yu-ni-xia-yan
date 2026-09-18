/** Compatible with saved 2.0.6–2.0.9 preferences. Never relabels or downgrades a source tree. */
export const DISPLAY_KEY='luke-display-v206';
export const LEGACY_DISPLAY_KEY='luke-display-v205';
export const COMPANIONS={dog:'/images/companions/dog.webp',toast:'/images/companions/toast.webp',cat:'/images/companions/cat.webp'} as const;
export const BUBBLES=[{id:'plain',name:'四季',image:''},{id:'tea',name:'暖茶',image:'/images/chat-skins/tea.webp'},{id:'sunflower',name:'向阳',image:'/images/chat-skins/sunflower.webp'},{id:'hearts',name:'心意',image:'/images/chat-skins/hearts.webp'}] as const;
export type Bubble=typeof BUBBLES[number]['id'];
export type DisplayPreferences={version:2;scale:number;chatSize:number;bubbleMine:Bubble;bubbleLuke:Bubble;avatarMine:string;avatarLuke:string;/** Compatibility aliases for older consumers. */bubble:Bubble;avatar:string;lukeAvatar:'toast'|'cat'};
export const DEFAULT_DISPLAY:DisplayPreferences={version:2,scale:.95,chatSize:13,bubbleMine:'plain',bubbleLuke:'tea',avatarMine:COMPANIONS.dog,avatarLuke:COMPANIONS.cat,bubble:'tea',avatar:COMPANIONS.dog,lukeAvatar:'cat'};
export function validAvatar(value:unknown):value is string {
 return typeof value==='string'&&(Object.values(COMPANIONS).includes(value as typeof COMPANIONS[keyof typeof COMPANIONS])||/^\/images\/details206\/[a-z0-9-]+\.webp$/i.test(value)||value.length<=300000&&/^data:image\/(?:png|jpeg|webp);base64,[A-Za-z0-9+/]+=*$/.test(value));
}
function bubble(value:unknown,fallback:Bubble):Bubble{return BUBBLES.some(b=>b.id===value)?value as Bubble:fallback;}
export function parseDisplay(value:unknown):DisplayPreferences{
 const p=(value&&typeof value==='object'&&!Array.isArray(value)?value:{}) as Partial<DisplayPreferences>;
 const legacyBubble=bubble(p.bubble,DEFAULT_DISPLAY.bubbleLuke);
 const bubbleMine=bubble(p.bubbleMine,p.bubble!==undefined?legacyBubble:DEFAULT_DISPLAY.bubbleMine),bubbleLuke=bubble(p.bubbleLuke,legacyBubble);
 const avatarMine=validAvatar(p.avatarMine)?p.avatarMine:validAvatar(p.avatar)?p.avatar:DEFAULT_DISPLAY.avatarMine;
 const avatarLuke=validAvatar(p.avatarLuke)?p.avatarLuke:COMPANIONS[p.lukeAvatar==='toast'?'toast':'cat'];
 return {version:2,scale:typeof p.scale==='number'&&Number.isFinite(p.scale)?Math.max(.75,Math.min(1.6,Math.round(p.scale*100)/100)):DEFAULT_DISPLAY.scale,chatSize:typeof p.chatSize==='number'&&Number.isFinite(p.chatSize)?Math.max(10,Math.min(32,Math.round(p.chatSize))):DEFAULT_DISPLAY.chatSize,bubbleMine,bubbleLuke,avatarMine,avatarLuke,bubble:bubbleLuke,avatar:avatarMine,lukeAvatar:avatarLuke===COMPANIONS.toast?'toast':'cat'};
}
function savedDisplay():Record<string,unknown>{
 const raw=localStorage.getItem(DISPLAY_KEY)??localStorage.getItem(LEGACY_DISPLAY_KEY);
 if(raw===null)return {};
 let parsed:unknown;try{parsed=JSON.parse(raw);}catch{throw Error('外观配置无法读取，原数据已保留；请先备份后检查。');}
 if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))throw Error('外观配置格式异常，未覆盖原设置。');
 const value=parsed as Record<string,unknown>;
 if(value.version!==undefined&&value.version!==1&&value.version!==2)throw Error('外观配置来自更新的版本，当前版本不会覆盖它。');
 return value;
}
export function readDisplay():DisplayPreferences{return parseDisplay(savedDisplay());}
export function applyDisplay(p:DisplayPreferences){const root=document.documentElement;root.style.fontSize=`${16*p.scale}px`;root.style.setProperty('--chat-font-size',`${p.chatSize}px`);root.dataset.bubble=p.bubbleLuke;root.dataset.bubbleMine=p.bubbleMine;root.dataset.bubbleLuke=p.bubbleLuke;}
export function saveDisplay(p:DisplayPreferences){
 // Read before writing: corrupt/future settings must never be overwritten by defaults.
 const previous=savedDisplay(),clean=parseDisplay(p);
 localStorage.setItem(DISPLAY_KEY,JSON.stringify({...previous,...clean}));
 applyDisplay(clean);window.dispatchEvent(new Event('luke-display-change'));return clean;
}
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
