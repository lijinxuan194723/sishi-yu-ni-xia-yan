import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fastTyping } from '../../lib/fast-typing.ts';
import { swapPhoto } from '../../lib/photo-transition.ts';
import { MOTION, settleAnimation } from '../../lib/motion.ts';

function environment() {
  const saved = new Map();
  const doc = new EventTarget(); doc.hidden = false;
  const media = new EventTarget(); media.matches = false;
  const queue = new Map(); let id = 0, clock = 0;
  for (const [key, value] of Object.entries({document:doc, matchMedia:()=>media,
    requestAnimationFrame: callback => { queue.set(++id, callback); return id; },
    cancelAnimationFrame: handle => queue.delete(handle)})) {
    saved.set(key, Object.getOwnPropertyDescriptor(globalThis, key));
    Object.defineProperty(globalThis, key, { value, configurable:true });
  }
  return {doc,media,queue,step(ms=16){clock+=ms;const pending=[...queue.values()];queue.clear();pending.forEach(fn=>fn(clock));},
    restore(){for(const [key,descriptor] of saved) if(descriptor)Object.defineProperty(globalThis,key,descriptor);else delete globalThis[key];}};
}
function animation() {
  let resolve,reject;const value={cancelled:false,finished:new Promise((ok,no)=>{resolve=ok;reject=no;}),
    finish(){resolve();},cancel(){this.cancelled=true;reject(new Error('cancelled'));}};
  return value;
}
function image() { return { style:{opacity:'1'}, src:'old',decode:()=>Promise.resolve() }; }

for (const text of ['自然的动画，不要突然结束。','A😀B𝄞你好','x'.repeat(2000),'a'.repeat(20000)]) {
  test(`stream preserves all ${text.length} UTF-16 units without partial surrogate pairs`, async()=>{
    const env=environment();try{
      const seen=[];const writer=fastTyping(s=>seen.push(s));writer.update(text);
      const done=writer.finish();let frames=0;
      while(env.queue.size&&frames++<1000){const before=seen.length;env.step();assert.ok(seen.length-before<=1,'at most one publication per frame');}
      await done;assert.equal(seen.at(-1),text);assert.ok(frames<300,'bounded buffered tail');
      for(const value of seen){assert.ok(text.startsWith(value));assert.ok(!/[\uD800-\uDBFF]$/.test(value));}
      assert.equal(env.queue.size,0);
    }finally{env.restore();}
  });
}
test('bursty stream updates coalesce rather than enqueue renders',async()=>{
 const env=environment();try{const seen=[],writer=fastTyping(s=>seen.push(s));
 for(let i=1;i<=200;i++)writer.update('好'.repeat(i));assert.equal(env.queue.size,1);assert.equal(seen.length,0);
 const done=writer.finish();for(let n=0;env.queue.size&&n<300;n++)env.step();await done;assert.equal(seen.at(-1),'好'.repeat(200));
 }finally{env.restore();}
});
test('stop preserves received text and releases scheduled work',async()=>{
 const env=environment();try{const abort=new AbortController(),seen=[],writer=fastTyping(s=>seen.push(s),abort.signal);
 writer.update('收到的内容'.repeat(100));env.step();abort.abort();await writer.finish();
 assert.equal(seen.at(-1),'收到的内容'.repeat(100));assert.equal(env.queue.size,0);
 }finally{env.restore();}
});
for(const mode of ['hidden','reduced'])test(`${mode} stream finishes without an animation timer`,async()=>{
 const env=environment();try{if(mode==='hidden')env.doc.hidden=true;else env.media.matches=true;
 const seen=[],writer=fastTyping(s=>seen.push(s));writer.update('完整文字');await writer.finish();
 assert.deepEqual(seen,['完整文字']);assert.equal(env.queue.size,0);
 }finally{env.restore();}
});
test('hiding during a stream does not strand finish()',async()=>{
 const env=environment();try{const seen=[],writer=fastTyping(s=>seen.push(s));writer.update('后台保留'.repeat(30));
 const done=writer.finish();env.doc.hidden=true;env.doc.dispatchEvent(new Event('visibilitychange'));await done;
 assert.equal(seen.at(-1),'后台保留'.repeat(30));assert.equal(env.queue.size,0);
 }finally{env.restore();}
});
test('changed provider prefix replaces content and finish is idempotent',async()=>{
 const env=environment();try{const seen=[],writer=fastTyping(s=>seen.push(s));writer.update('first');env.step();writer.update('最终内容');
 const done=writer.finish();for(let n=0;env.queue.size&&n<100;n++)env.step();await done;await writer.finish();
 writer.update('must not publish');assert.equal(seen.at(-1),'最终内容');
 }finally{env.restore();}
});
test('animation abort returns false, not an unhandled rejection',async()=>{
 const env=environment();try{const a=animation(),controller=new AbortController(),result=settleAnimation(a,controller.signal);controller.abort();
 assert.equal(await result,false);assert.ok(a.cancelled);
 }finally{env.restore();}
});
for(const mode of ['hidden','reduced'])test(`${mode} completes a running presence animation`,async()=>{
 const env=environment();try{const a=animation(),result=settleAnimation(a);
 if(mode==='hidden'){env.doc.hidden=true;env.doc.dispatchEvent(new Event('visibilitychange'));}
 else{env.media.matches=true;env.media.dispatchEvent(new Event('change'));}
 assert.equal(await result,true);
 }finally{env.restore();}
});
test('photo retains the previous opaque image until decoding and fade complete',async()=>{
 const env=environment();try{const front=image(),back=image(),a=animation();let options;
 back.animate=(_frames,o)=>{options=o;return a;};
 const result=swapPhoto(front,back,'new','center',()=>false);await Promise.resolve();
 assert.equal(front.style.opacity,'1');assert.equal(back.style.opacity,'0');assert.equal(options.duration,MOTION.photo);
 a.finish();assert.equal(await result,true);assert.equal(front.style.opacity,'0');assert.equal(back.style.opacity,'1');
 }finally{env.restore();}
});
test('stale undecoded request never becomes visible',async()=>{
 const env=environment();try{const front=image(),back=image();let called=false;back.animate=()=>{called=true;return animation();};
 assert.equal(await swapPhoto(front,back,'new','center',()=>true),false);assert.equal(called,false);assert.equal(front.style.opacity,'1');
 }finally{env.restore();}
});
test('a newer selection cannot flash backwards at the end of an in-flight fade',async()=>{
 const env=environment();try{const front=image(),back=image(),a=animation();let stale=false;back.animate=()=>a;
 const result=swapPhoto(front,back,'new','center',()=>stale);await Promise.resolve();stale=true;a.finish();
 assert.equal(await result,true);assert.equal(front.style.opacity,'0');assert.equal(back.style.opacity,'1');
 }finally{env.restore();}
});
test('a corrupt photo does not blank the previous frame',async()=>{
 const env=environment();try{const front=image(),back=image();back.decode=()=>Promise.reject(new Error('decode'));
 await assert.rejects(swapPhoto(front,back,'bad','center',()=>false));assert.equal(front.style.opacity,'1');assert.equal(back.style.opacity,'0');
 }finally{env.restore();}
});
test('reduced motion commits a decoded photo immediately',async()=>{
 const env=environment();try{env.media.matches=true;const front=image(),back=image();back.animate=()=>{throw new Error('must not animate');};
 assert.equal(await swapPhoto(front,back,'new','center',()=>false),true);assert.equal(back.style.opacity,'1');
 }finally{env.restore();}
});
// Wiring contracts supplement, but do not replace, browser and Android frame tests.
const read=path=>readFileSync(new URL('../../'+path,import.meta.url),'utf8');
test('obsolete startup animation gates are absent from both app entry points',()=>{
 for(const path of ['app/ambience.css','app/notes-polish.css'])assert.ok(!read(path).includes(':root:not([data-started])'));
 assert.ok(!read('app/notes-polish.css').includes('.app-shell *,:root .settings'));
 for(const path of ['mobile/main.tsx','app/layout.tsx']){assert.ok(read(path).includes('motion.css'));assert.ok(!/import .*startup-(splash|reveal|nav)/.test(read(path)));}
});
test('topic close waits for Base UI exit completion',()=>{
 const s=read('components/topic-chat.tsx');assert.ok(s.includes('if (flush()) setOpen(false)'));assert.ok(s.includes('onOpenChangeComplete='));
});
test('native motion retains the vector until removal and uses a balanced curve',()=>{
 const s=read('mobile/android/MainActivity.java'),ready=s.slice(s.indexOf('  void ready(){'),s.indexOf('  private void transitionTheme(){'));
 assert.ok(!ready.includes('stopMark()'));assert.ok(s.includes('EXIT_MS=380L'));
 const xml=read('mobile/android/res/interpolator/luke_launch_ease.xml');assert.ok(xml.includes('controlY1="0"'));assert.ok(xml.includes('controlX1="0.4"'));
});
