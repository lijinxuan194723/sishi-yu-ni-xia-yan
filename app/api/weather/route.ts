import {json,sameOrigin,userId} from '@/lib/server';
import {coordinates,meteoURL,normalizeMeteo} from '@/lib/weather';
export async function POST(request:Request){try{
 userId(request);if(!sameOrigin(request))return json({error:'来源无效'},403);
 const text=await request.text();if(text.length>6000)return json({error:'配置过长'},400);
 let point;try{const p=JSON.parse(text);point=coordinates(p.lat,p.lon);}catch{return json({error:'请填写有效坐标。'},400);}
 const response=await fetch(meteoURL(point.latitude,point.longitude),{signal:AbortSignal.any([request.signal,AbortSignal.timeout(15000)]),redirect:'error',headers:{Accept:'application/json'}});
 if(!response.ok)return json({error:'天气服务暂不可用'},502);return json(normalizeMeteo(await response.json()));
 }catch(e){return json({error:e instanceof Error&&e.message==='UNAUTHORIZED'?'请先登录后获取天气。':'天气更新失败，请稍后重试。'},e instanceof Error&&e.message==='UNAUTHORIZED'?401:502);}}
