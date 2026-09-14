import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';
import {longPressController,LONG_PRESS_MS} from '../../lib/long-press.ts';
function rig(){let fn=null,time=0,opened=[];const g=longPressController(id=>opened.push(id),{set:f=>(fn=f,1),clear:()=>fn=null,now:()=>time});return {g,opened,fire:()=>fn?.(),time:n=>time=n};}
test('hold waits before opening',()=>{const r=rig();r.g.start('one',1,2,3);assert.equal(r.opened.length,0);r.fire();assert.deepEqual(r.opened,['one']);assert.equal(LONG_PRESS_MS,480);});
test('finger scrolling cancels hold',()=>{const r=rig();r.g.start('one',1,0,0);r.g.move(1,0,20);r.fire();assert.equal(r.opened.length,0);});
test('small jitter is not a scroll',()=>{const r=rig();r.g.start('one',1,0,0);r.g.move(1,3,4);r.fire();assert.equal(r.opened.length,1);});
test('a second pointer cancels hold',()=>{const r=rig();r.g.start('one',1,0,0);r.g.start('one',2,0,0,false);r.fire();assert.equal(r.opened.length,0);});
test('release before deadline is a normal tap',()=>{const r=rig();r.g.start('one',1,0,0);r.g.cancel();r.fire();assert.equal(r.opened.length,0);assert.equal(r.g.consume('one'),false);});
test('post-hold click suppressed exactly once',()=>{const r=rig();r.g.start('one',1,0,0);r.fire();assert.equal(r.g.consume('one'),true);assert.equal(r.g.consume('one'),false);});
test('unrelated note never loses click',()=>{const r=rig();r.g.start('one',1,0,0);r.fire();assert.equal(r.g.consume('two'),false);});
test('keyboard activation never lost after hold',()=>{const r=rig();r.g.start('one',1,0,0);r.fire();assert.equal(r.g.consume('one',true),false);});
test('stale suppression expires',()=>{const r=rig();r.g.start('one',1,0,0);r.fire();r.time(1000);assert.equal(r.g.consume('one'),false);});
test('native contextmenu after hold does not reopen',()=>{const r=rig();r.g.start('one',1,0,0);r.fire();r.g.context('one');assert.equal(r.opened.length,1);});
const native=readFileSync(new URL('../../mobile/android/MainActivity.java',import.meta.url),'utf8');
test('hot return separate from cold startup',()=>{assert.match(native,/class WarmReturn/);assert.match(native,/!externalReturn/);assert.match(native,/web\.onPause\(\)/);assert.match(native,/web\.onResume\(\)/);});
test('return animation never recreates or scales the WebView',()=>{const warm=native.split('private final class WarmReturn')[1].split('private String launchSeason')[0];assert.doesNotMatch(warm,/web\.loadUrl|web\.reload|web\.setScale/);assert.match(warm,/postVisualStateCallback/);assert.match(warm,/650L/);});
test('context sheet requires trash confirmation',()=>{const s=readFileSync(new URL('../../components/memo-actions.tsx',import.meta.url),'utf8');assert.match(s,/确认移到回收站/);assert.match(s,/onOpenChangeComplete/);});
