import {networkFetch,nativeDistrict} from './mobile.ts';
export function locationLabel(p:any){
 const names=(Array.isArray(p?.localityInfo?.administrative)?p.localityInfo.administrative:[]).map((a:any)=>typeof a?.name==='string'?a.name:'').filter(Boolean);
 const geo=p?.features?.[0]?.properties?.geocoding;
 const a=p?.address??geo??{};
 const candidates=[...names].reverse().concat([geo?.admin?.level6,geo?.admin?.level7,geo?.admin?.level8,a.county,a.city_district,a.district,a.borough,a.suburb,p?.locality]);
 const district=candidates.find((s:unknown)=>typeof s==='string'&&/(区|县|旗)$/.test(s)&&!/(自治区|特别行政区|社区|小区)$/.test(s))??candidates.find((s:unknown)=>typeof s==='string'&&s.trim()&&!/(市|省|自治区|特别行政区|社区|小区)$/.test(s));
 if(!district)throw Error('未能获取县区名称，请重试定位。');
 const city=typeof p?.city==='string'?p.city:typeof a.city==='string'?a.city:'';
 return [...new Set([city,district].filter(Boolean))].join(' · ').slice(0,80);
}
const cache=new Map<string,string>();
let lastLookup=0;
export function districtEndpoint(value?:string){const u=new URL(value||"https://nominatim.openstreetmap.org/reverse");if(u.protocol!=="https:"||u.username||u.password||u.hash)throw Error("县区服务地址需要使用 HTTPS");return u;}
export async function resolveDistrict(lat:string,lon:string,signal?:AbortSignal,fresh=false,fallback=false,endpoint?:string){
 if(!lat.trim()||!lon.trim()||!Number.isFinite(+lat)||!Number.isFinite(+lon)||Math.abs(+lat)>90||Math.abs(+lon)>180)throw Error('定位坐标无效');
 const key=lat+','+lon;
 if(cache.has(key))return cache.get(key)!;
 const native=await nativeDistrict(+lat,+lon,signal);
 if(native){cache.set(key,native);return native;}
 // BigDataCloud's free endpoint is only used immediately after a device GPS request.
 const extra=fallback?districtEndpoint(endpoint):null;if(extra){extra.searchParams.set("format","geocodejson");extra.searchParams.set("lat",lat);extra.searchParams.set("lon",lon);extra.searchParams.set("zoom","18");extra.searchParams.set("addressdetails","1");extra.searchParams.set("accept-language","zh-CN");}
 const urls=[...(fresh?[`https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lon}&localityLanguage=zh`]:[]),...(extra?[extra.href]:[])];
 for(const url of urls){
  if(signal?.aborted)throw new DOMException('Aborted','AbortError');
  try{const wait=Math.max(0,1100-(Date.now()-lastLookup));if(wait)await new Promise(r=>setTimeout(r,wait));if(signal?.aborted)throw new DOMException("Aborted","AbortError");lastLookup=Date.now();const r=await networkFetch(url,{signal:signal?AbortSignal.any([signal,AbortSignal.timeout(12000)]):AbortSignal.timeout(12000)});if(!r.ok)continue;const place=locationLabel(await r.json());cache.set(key,place);return place;}catch(e){if(signal?.aborted)throw e;}
 }
 throw Error('县区查询暂时不可用，请重试定位或填写县区名称。');
}
export async function locate(signal?:AbortSignal,fallback=false,endpoint?:string,requireName=true){
 if(!navigator.geolocation)throw Error('当前设备不支持定位，请手动填写位置。');
 const p=await new Promise<GeolocationPosition>((resolve,reject)=>navigator.geolocation.getCurrentPosition(resolve,()=>reject(Error('定位未成功，请允许位置权限或手动填写。')),{enableHighAccuracy:true,timeout:15000,maximumAge:0}));
 if(signal?.aborted)throw new DOMException('Aborted','AbortError');
 const lat=p.coords.latitude.toFixed(5),lon=p.coords.longitude.toFixed(5);
 try{const place=await resolveDistrict(lat,lon,signal,true,fallback,endpoint);return {lat,lon,place,warning:''};}
 catch(error){if(signal?.aborted||requireName)throw error;return {lat,lon,place:'我的位置',warning:'坐标已取得，可自行填写地点名称。'};}
}
