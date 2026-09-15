"""Mock the APK's native network bridge. Keep its connect-src 'none' policy intact.
No real provider, key, or network request is used by this fixture.
"""
BRIDGE = r'''() => {
 const previous=window.LukeAndroid;
 const f={summary:'长'.repeat(2500),hold:false,requests:[],pending:[],cancelled:[]};
 f.reply=(id,text,stream=false,status=200)=>{
  const body=JSON.stringify({choices:[{message:{content:text},finish_reason:'stop'}]});
  if(stream)window.__lukeStreaming?.(id,status,'application/json',body,true);
  else window.__lukeNetwork?.(id,status,body);
 };
 const request=(id,url,method,headers,body,stream=false)=>{
  if(url!=='https://memory-fixture.invalid/v1/chat/completions'||method!=='POST')throw Error('Unexpected fixture request');
  const payload=JSON.parse(body);
  const archive=payload.messages.some(m=>m.content.includes('你是聊天档案整理器'));
  if(!archive&&!stream){queueMicrotask(()=>f.reply(id,'unrelated fixture request',false,503));return;}
  f.requests.push({...payload,requestId:id});
  if(f.hold&&archive){f.pending.push(id);return;}
  const text=stream?'记得：旧书店和茉莉花，这是测试回复。':f.summary;
  queueMicrotask(()=>f.reply(id,text,stream));
 };
 window.LukeAndroid={...previous,request:(...args)=>request(...args,false),requestStream:(...args)=>request(...args,true),cancel:id=>{f.cancelled.push(id);}};
 f.restore=()=>{if(previous===undefined)delete window.LukeAndroid;else window.LukeAndroid=previous;delete window.__memoryFixture;};
 window.__memoryFixture=f;
}'''
