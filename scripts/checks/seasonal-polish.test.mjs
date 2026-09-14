import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
import os from 'node:os';
import path from 'node:path';
import {feedbackMode, createFeedbackGate} from '../../lib/interaction-feedback.ts';
const read = p => fs.readFileSync(new URL('../../' + p, import.meta.url), 'utf8');
test('touch mode has a safe, backwards-compatible default', () => {
 for(const value of [null,'gentle','unexpected'])assert.equal(feedbackMode(value),'gentle');
 assert.equal(feedbackMode('off'),'off');
});
for(const scenario of ['untrusted','hidden','disabled','invalid-clock'])test(`no feedback for ${scenario}`,()=>{
 const gate=createFeedbackGate();assert.equal(gate(scenario==='invalid-clock'?NaN:100,scenario!=='untrusted',scenario!=='hidden',scenario!=='disabled'),false);
 assert.equal(gate(110,true,true,true),true);
});
test('double events coalesce, independent later actions do not',()=>{
 const gate=createFeedbackGate();assert.ok(gate(0,true,true,true));assert.equal(gate(20,true,true,true),false);assert.ok(gate(90,true,true,true));assert.equal(gate(120,false,true,true),false);assert.ok(gate(185,true,true,true));
});
test('native calendar and night selection run as Java for every month and boundary',()=>{
 const java=read('mobile/android/MainActivity.java');
 const methods=java.slice(java.indexOf(' private static String seasonAtMonth'),java.indexOf(' private int launchResource'));
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'luke-seasons-'));
 const source=`class CalendarCheck {${methods}\npublic static void main(String[] args){for(int month=0;month<12;month++)System.out.println(seasonAtMonth(month));for(double hour:new double[]{0,5.74,5.75,6,12,19,19.01,23.99})System.out.println(darkAtPeriod("auto",hour));System.out.println(darkAtPeriod("夜晚",12));System.out.println(darkAtPeriod("正午",23));}}`;
 try{
  fs.writeFileSync(path.join(tmp,'CalendarCheck.java'),source);
  execFileSync('javac',['--release','8',path.join(tmp,'CalendarCheck.java')]);
  const rows=execFileSync('java',['-cp',tmp,'CalendarCheck'],{encoding:'utf8'}).trim().split('\n');
  assert.deepEqual(rows.slice(0,12),['winter','winter','spring','spring','spring','summer','summer','summer','autumn','autumn','autumn','winter']);
  assert.deepEqual(rows.slice(12),['true','true','false','false','false','false','true','true','true','false']);
 }finally{fs.rmSync(tmp,{recursive:true,force:true});}
});
for(const season of ['spring','summer','autumn','winter'])test(`${season} has day/night native theme and rounded seasonal vector`,()=>{
 const xml=read('mobile/android/res/values/luke_launch_seasons.xml');
 const title=season[0].toUpperCase()+season.slice(1);
 for(const mode of ['Day','Night'])assert.ok(xml.includes(`name="Luke${title}${mode}"`));
 const vector=read(`mobile/android/res/drawable/luke_launch_${season}_mark.xml`);
 assert.ok(vector.includes('seasonMotif'));assert.ok(vector.includes('strokeLineCap="round"'));assert.ok(vector.includes('?attr/lukeLaunchKey'));
 assert.ok(read(`mobile/android/res/drawable/luke_launch_${season}_animated.xml`).includes(`@drawable/luke_launch_${season}_mark`));
});
test('native feedback is bounded and cannot bypass system settings',()=>{
 const java=read('mobile/android/MainActivity.java');
 assert.ok(java.includes('now-lastFeedback<90L'));assert.ok(java.includes('!web.hasWindowFocus()'));
 assert.ok(java.includes('web.performHapticFeedback(effect)'));
 for(const unsafe of ['FLAG_IGNORE_GLOBAL_SETTING','FLAG_IGNORE_VIEW_SETTING','createWaveform','VIBRATE'])assert.ok(!java.includes(unsafe));
});
test('birthday handoff is callback-driven and cannot trap on storage failure',()=>{
 const source=read('components/birthday-greeting.tsx');
 assert.ok(source.includes('settingsComplete'));assert.ok(source.includes('onOpenChangeComplete={onComplete}'));
 assert.ok(source.includes('setOpen(false)'));assert.ok(source.includes('shown.current.has(day)'));
 assert.ok(!source.includes('setTimeout'));assert.ok(source.includes('length: 12'));
});
test('birthday effects are finite and contrast uses the actual theme foreground',()=>{
 const css=read('app/interaction-polish.css');
 assert.ok(!css.includes('infinite'));assert.ok(css.includes('var(--on-accent,#fff)'));assert.ok(css.includes('overflow-y:auto'));
 assert.ok(css.includes('.birthday-refined .birthday-confetti{display:none}'));
});
test('automatic birthday waits for the native surface to leave before entrance',()=>{
 const source=read('components/birthday-greeting.tsx');
 assert.ok(source.includes('!nativeReady'));assert.ok(source.includes('data-native-launching'));
 assert.ok(source.includes('observer.disconnect()'));assert.ok(source.includes('document.hidden'));
});
test('button press uses scale without overwriting positional transforms',()=>{
 const css=read('app/motion.css');assert.ok(css.includes('{ scale: .97; }'));
 assert.ok(!css.includes('transform: scale(.97)'));
});

test('settings dismissal stays outside its inner scroll body in one modal scope',()=>{
 const source=read('components/ui/dialog.tsx');
 const popup=source.slice(source.indexOf('<DialogPrimitive.Popup'),source.indexOf('</DialogPrimitive.Popup>'));
 assert.ok(popup.includes('data-slot="dialog-scroll-body"'));
 assert.ok(popup.indexOf('aria-label="关闭"')<popup.indexOf('data-slot="dialog-scroll-body"'));
 assert.ok(!popup.includes('dialog-close-anchor'));
 const css=read('app/interaction-polish.css');
 assert.ok(css.includes('overflow-y:auto'));
 assert.ok(css.includes('.settings>[data-slot="dialog-close"]{position:absolute;top:8px;right:8px'));
 assert.ok(!css.includes('display:contents}'));
 assert.ok(!css.includes('position:sticky;top:0;height:0'));
});

test('renderer termination destroys the old view and offers manual non-destructive recovery',()=>{
 const java=read('mobile/android/MainActivity.java');
 assert.ok(java.includes('onRenderProcessGone(WebView view,RenderProcessGoneDetail detail)'));
 assert.ok(java.includes('rendererGone=true;contentReady=false;readyPosted=true'));
 assert.ok(java.includes('view.destroy();web=null;'));
 assert.ok(java.includes('if(canUseWeb())web.evaluateJavascript'));
 assert.ok(java.includes('if(!canUseWeb()||readyPosted)return'));
 const recovery=java.slice(java.indexOf('private boolean rendererTerminated'),java.indexOf('// Native startup only:'));
 assert.ok(recovery.includes('recreate()'));
 assert.ok(recovery.includes('return true;'));
 for(const destructive of ['clearCache','clearData','deleteDatabase','removeAllCookies','deleteAllData','loadUrl'])assert.ok(!recovery.includes(destructive));
});
