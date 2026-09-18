/** A dial is a view of the existing timestamp, never another timer or stored clock. */
export type DialSource =
 | {kind:'clock'}
 | {kind:'elapsed';elapsedMs:number;startedAt?:number}
 | {kind:'countdown';remainingMs:number;durationMs:number;endsAt?:number};
export type DialSample={milliseconds:number;second:number;minute:number;hour:number;progress:number};
const finite=(n:unknown,fallback=0):number=>typeof n==='number'&&Number.isFinite(n)?n:fallback;
const turn=(degrees:number)=>((degrees%360)+360)%360;
export function dialSample(source:DialSource,now=Date.now(),smooth=true):DialSample {
 const wall=finite(now);let ms:number;
 if(source.kind==='clock'){
  const local=new Date(wall);ms=((local.getHours()*60+local.getMinutes())*60+local.getSeconds())*1000+local.getMilliseconds();
 }else if(source.kind==='elapsed'){
  ms=Math.max(0,source.startedAt===undefined?finite(source.elapsedMs):wall-finite(source.startedAt,wall));
 }else{
  const limit=Math.max(0,finite(source.durationMs));ms=Math.max(0,Math.min(limit,source.endsAt===undefined?finite(source.remainingMs):finite(source.endsAt,wall)-wall));
 }
 // Reduced motion changes only interpolation. Even with decoration disabled, a
 // functional hand still ticks once a second and always shows the correct time.
 const visual=smooth?ms:Math.floor(ms/1000)*1000;
 return {milliseconds:ms,second:turn(visual*.006),minute:turn(visual*.0001),hour:turn(visual/120000),progress:source.kind==='countdown'&&source.durationMs>0?Math.min(1,ms/source.durationMs):(ms%60000)/60000};
}
export function dialRunning(source:DialSource){return source.kind==='clock'||source.kind==='elapsed'&&source.startedAt!==undefined||source.kind==='countdown'&&source.endsAt!==undefined;}
export function elapsedText(ms:number){const sec=Math.floor(Math.max(0,finite(ms))/1000);return [Math.floor(sec/3600),Math.floor(sec%3600/60),sec%60].map(n=>String(n).padStart(2,'0')).join(':');}
export function remainingText(ms:number){const sec=Math.ceil(Math.max(0,finite(ms))/1000);return `${String(Math.floor(sec/60)).padStart(2,'0')}:${String(sec%60).padStart(2,'0')}`;}
export function fitClockDiameter(width:number,availableHeight:number,readoutHeight:number,maximum=300){
 const fit=Math.min(finite(width)-8,finite(availableHeight)-Math.max(0,finite(readoutHeight))-12,finite(maximum,300));
 return Math.max(112,Math.floor(fit)); // very small/landscape viewports keep native scroll rather than losing controls
}
