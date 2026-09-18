type NativeBridge={defaultModel?:()=>string;haptic?:()=>void;pageReady?:()=>void;systemTheme?:(color:string,dark:boolean)=>void;geocode?:(id:string,lat:number,lon:number)=>void;request:(id:string,url:string,method:string,headers:string,body:string)=>void;requestStream?:(id:string,url:string,method:string,headers:string,body:string)=>void;cancel:(id:string)=>void;saveBackup:(name:string,text:string)=>void};
declare global {interface Window {LukeAndroid?:NativeBridge;__lukeBack?:()=>boolean;__lukeGeocode?:(id:string,place:string)=>void;__lukeNetwork?:(id:string,status:number,body:string)=>void;__lukeStreaming?:(id:string,status:number,type:string,chunk:string,done:boolean)=>void}}
export function isAndroid(){return typeof window!=='undefined'&&!!window.LukeAndroid;}
const pending=new Map<string,{resolve:(r:Response)=>void;reject:(e:Error)=>void;cleanup:()=>void}>();
export function networkFetch(url:string,init:RequestInit={}):Promise<Response>{
 if(!isAndroid())return fetch(url,init);
 if(init.signal?.aborted)return Promise.reject(new DOMException('Aborted','AbortError'));
 window.__lukeNetwork=(id,status,body)=>{const p=pending.get(id);if(!p)return;pending.delete(id);p.cleanup();if(status===0)p.reject(Error('网络连接失败，请检查手机网络和接口地址。'));else p.resolve(new Response([204,205,304].includes(status)?null:body,{status,headers:{'Content-Type':'application/json'}}));};
 return new Promise((resolve,reject)=>{const id=crypto.randomUUID();const stop=()=>{pending.delete(id);cleanup();window.LukeAndroid!.cancel(id);reject(new DOMException('Aborted','AbortError'));};const timer=setTimeout(stop,90000);const cleanup=()=>{clearTimeout(timer);init.signal?.removeEventListener('abort',stop);};pending.set(id,{resolve,reject,cleanup});init.signal?.addEventListener('abort',stop,{once:true});try{window.LukeAndroid!.request(id,url,init.method??'GET',JSON.stringify(Object.fromEntries(new Headers(init.headers))),String(init.body??''));}catch(e){pending.delete(id);cleanup();reject(e);}});
}
export function saveBackupFile(name:string,text:string){if(isAndroid()){window.LukeAndroid!.saveBackup(name,text);return;}const url=URL.createObjectURL(new Blob([text],{type:/\.md$/i.test(name)?'text/markdown':/\.txt$/i.test(name)?'text/plain':'application/json'}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
const streaming=new Map<string,(status:number,type:string,chunk:string,done:boolean)=>void>();
export function networkStream(url:string,init:RequestInit):Promise<Response>{
 if(!isAndroid())return fetch(url,init);
 if(!window.LukeAndroid?.requestStream)return Promise.reject(Error('请更新安装包以使用流式回复。'));
 if(init.signal?.aborted)return Promise.reject(new DOMException('Aborted','AbortError'));
 window.__lukeStreaming=(id,status,type,chunk,done)=>streaming.get(id)?.(status,type,chunk,done);
 return new Promise((resolve,reject)=>{const id=crypto.randomUUID();let controller:ReadableStreamDefaultController<Uint8Array>,started=false,dangling='';
 const cleanup=()=>{streaming.delete(id);clearTimeout(timer);init.signal?.removeEventListener('abort',stop);};
 const stop=()=>{cleanup();window.LukeAndroid!.cancel(id);const error=new DOMException('Aborted','AbortError');if(started)controller.error(error);else reject(error);};
 const timer=setTimeout(stop,90000);init.signal?.addEventListener('abort',stop,{once:true});
 streaming.set(id,(status,type,chunk,done)=>{if(!status){cleanup();const error=Error('连接中断，已收到的回复会保留。');if(started)controller.error(error);else reject(error);return;}if(!started){started=true;const body=new ReadableStream<Uint8Array>({start(c){controller=c;},cancel(){cleanup();window.LukeAndroid!.cancel(id);}});resolve(new Response(body,{status,headers:{'Content-Type':type||'application/json'}}));}chunk=dangling+chunk;dangling='';if(!done&&/[\uD800-\uDBFF]$/.test(chunk)){dangling=chunk.slice(-1);chunk=chunk.slice(0,-1);}if(chunk)controller.enqueue(new TextEncoder().encode(chunk));if(done){controller.close();cleanup();}});
 try{window.LukeAndroid!.requestStream!(id,url,init.method??'GET',JSON.stringify(Object.fromEntries(new Headers(init.headers))),String(init.body??''));}catch(e){cleanup();reject(e);}
 });
}

const geoPending=new Map<string,(place:string)=>void>();
export function nativeDistrict(lat:number,lon:number,signal?:AbortSignal):Promise<string>{
 if(typeof window==='undefined'||!window.LukeAndroid?.geocode)return Promise.resolve('');
 if(signal?.aborted)return Promise.reject(new DOMException('Aborted','AbortError'));
 window.__lukeGeocode=(id,place)=>geoPending.get(id)?.(place);
 return new Promise((resolve,reject)=>{const id=crypto.randomUUID();const cleanup=()=>{clearTimeout(timer);geoPending.delete(id);signal?.removeEventListener('abort',abort);};const abort=()=>{cleanup();reject(new DOMException('Aborted','AbortError'));};const done=(place:string)=>{cleanup();resolve(place);};const timer=setTimeout(()=>done(''),8000);geoPending.set(id,done);signal?.addEventListener('abort',abort,{once:true});try{window.LukeAndroid!.geocode!(id,lat,lon);}catch{done('');}});
}
