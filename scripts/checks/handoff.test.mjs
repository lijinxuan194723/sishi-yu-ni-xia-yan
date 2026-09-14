import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=path=>readFileSync(new URL('../../'+path,import.meta.url),'utf8');

test('native handoff keeps backup cover until system fade begins',()=>{
 const java=read('mobile/android/MainActivity.java');
 const ready=java.slice(java.indexOf('  void ready(){'),java.indexOf('  private void transitionTheme(){'));
 assert.ok(ready.includes('if(platformView!=null)startSystemExit()'));
 assert.ok(java.includes('if(surface==platformView&&contentReady)removeLayer()'));
 assert.ok(!java.includes('OnPreDrawListener'));
 assert.ok(java.includes('if(disposed||nativeExitStarted){remove.run();return;}'));
});
test('startup has no hard-coded green placeholder and publishes theme before readiness',()=>{
 assert.ok(!read('mobile/index.html').includes('#214f4c'));
 assert.ok(!read('mobile/android/MainActivity.java').includes('#214f4c'));
 const s=read('components/ambience.tsx');
 assert.ok(s.indexOf('window.LukeAndroid?.systemTheme?.')<s.indexOf('flushSync(()=>setScene(next))'));
});
test('covered WebView draws normally without offscreen pre-raster memory',()=>{
 const java=read('mobile/android/MainActivity.java');
 assert.ok(!java.includes('setOffscreenPreRaster'));
 assert.ok(java.includes('web.setVisibility(android.view.View.VISIBLE);web.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)'));
 assert.equal(java.split('web.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)').length-1,2);
});
