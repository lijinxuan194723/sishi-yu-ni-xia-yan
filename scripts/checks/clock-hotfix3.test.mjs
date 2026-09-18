import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {dialSample,dialRunning,remainingText,elapsedText,fitClockDiameter} from '../../lib/analog-time.ts';
import {countdownRemaining,pauseCountdown,resumeCountdown,completeCountdown} from '../../lib/countdown.ts';
const read=p=>fs.readFileSync(p,'utf8');
const now=Date.parse('2026-09-18T06:12:34.567Z');
for(const seconds of [0,.125,1,14.5,29.75,59.99,60,60.125,3599,3600,3661,43200,86401])test(`elapsed hands reflect actual time ${seconds}s without frame accumulation`,()=>{
 const s=dialSample({kind:'elapsed',startedAt:now-seconds*1000,elapsedMs:0},now);
 assert.equal(s.milliseconds,seconds*1000);
 assert.ok(Math.abs(s.second-((seconds%60)*6))<1e-5);
 assert.ok(Math.abs(s.minute-((seconds/60)%60)*6)<1e-5);
 for(const k of ['second','minute','hour'])assert.ok(s[k]>=0&&s[k]<360);
});
for(const left of [180000,61500,60000,59999,1234,0,-10000])test(`countdown pointer and data use the same deadline (${left} ms)`,()=>{
 const c={seconds:180,remainingMs:180000,endsAt:now+left};const s=dialSample({kind:'countdown',durationMs:180000,remainingMs:180000,endsAt:c.endsAt},now);
 assert.equal(s.milliseconds,countdownRemaining(c,now));assert.ok(s.progress>=0&&s.progress<=1);
});
test('local clock includes hours, minutes and fractional seconds',()=>{const d=new Date(now),s=dialSample({kind:'clock'},now);assert.equal(s.milliseconds,((d.getHours()*60+d.getMinutes())*60+d.getSeconds())*1000+d.getMilliseconds());assert.equal(s.second,207.40200000000186);});
test('reduced animation steps essential hands rather than freezing the timer',()=>{const source={kind:'elapsed',startedAt:now-9950,elapsedMs:0};const a=dialSample(source,now,false),b=dialSample(source,now+200,false);assert.equal(a.second,54);assert.equal(b.second,60);assert.equal(a.milliseconds,9950);});
test('pause is stable, resume and wake use persisted clock not animation count',()=>{const original={countdown:{seconds:120,remainingMs:120000,endsAt:now+90500}};const patch=pauseCountdown(original,original.countdown.endsAt,now);const paused={...original,...patch};for(const delta of [0,1000,3600000])assert.equal(dialSample({kind:'countdown',durationMs:120000,remainingMs:paused.countdown.remainingMs},now+delta).milliseconds,90500);const resumed={...paused,...resumeCountdown(paused,now+3600000)};assert.equal(dialSample({kind:'countdown',durationMs:120000,remainingMs:0,endsAt:resumed.countdown.endsAt},now+3601000).milliseconds,89500);});
test('cancel/end operations still have deadline identities',()=>{const data={countdown:{seconds:60,remainingMs:60000,endsAt:now}};const done=completeCountdown(data,now,now);assert.deepEqual(completeCountdown({...data,...done},now,now),{});assert.deepEqual(pauseCountdown(data,now+1,now),{});});
test('idle stopwatch stays at zero and reminder clock remains live',()=>{assert.equal(dialRunning({kind:'elapsed',elapsedMs:0}),false);assert.equal(dialRunning({kind:'clock'}),true);assert.equal(dialRunning({kind:'countdown',remainingMs:20000,durationMs:20000}),false);assert.equal(dialRunning({kind:'countdown',remainingMs:20000,durationMs:20000,endsAt:now}),true);});
test('duration formatting handles zero, hour boundary and subsecond completion',()=>{assert.equal(elapsedText(3661999),'01:01:01');assert.equal(remainingText(1),'00:01');assert.equal(remainingText(3600000),'60:00');assert.equal(remainingText(-1),'00:00');assert.equal(elapsedText(Infinity),'00:00:00');});
test('small dials never become oval or consume the readout allocation',()=>{assert.equal(fitClockDiameter(300,400,90),292);assert.equal(fitClockDiameter(230,270,80),178);assert.equal(fitClockDiameter(200,100,80),112);assert.ok(Number.isFinite(fitClockDiameter(NaN,Infinity,NaN)));});
test('all real hands have the same explicit SVG world center',()=>{const s=read('components/analog-clock.tsx');assert.match(s,/rotate\(\$\{sample.second\} 100 100\)/);assert.match(s,/rotate\(\$\{sample.minute\} 100 100\)/);assert.match(s,/rotate\(\$\{sample.hour\} 100 100\)/);assert.doesNotMatch(s,/rotate\s*:\s*|<motion\.g/);assert.match(read('app/clock-hotfix3.css'),/transform-origin:0px 0px!important/);});
test('visible long pointer is distinct from an outer progress marker',()=>{const s=read('components/analog-clock.tsx');assert.match(s,/d="M100 20V116"/);assert.match(s,/data-hand="minute"/);assert.match(s,/data-hand="hour"/);assert.match(s,/clock-hub-hf3/);});
test('essential clock continues with reduced-motion, only interpolation is removed',()=>{const hook=read('hooks/use-clock-activity.ts'),component=read('components/analog-clock.tsx');assert.match(hook,/const visible=intersecting&&!document.hidden/);assert.match(hook,/smooth=visible&&!media.matches/);assert.match(component,/if\(smooth\)frame=requestAnimationFrame\(tick\);else timer=setTimeout/);assert.match(component,/cancelAnimationFrame\(frame\);clearTimeout\(timer\)/);assert.doesNotMatch(component,/localStorage|setInterval|performance.now\(\)\s*\+/);});
test('digital focus readout is outside the full-radius clock hands',()=>{const s=read('components/study-dial.tsx');assert.ok(s.indexOf('<AnalogClock ')<s.indexOf('<div className="focus-readout-hf3"'));assert.match(s,/startedAt/);assert.match(s,/fitClockDiameter/);});
test('reminder idle, countdown, and pomodoro reuse the same actual hand engine',()=>{assert.match(read('components/countdown-ruler.tsx'),/source=\{c\?\{kind:'countdown'/);assert.match(read('components/countdown-ruler.tsx'),/\{kind:'clock'\}/);assert.match(read('components/focus-moment.tsx'),/<AnalogClock source=\{\{kind:'countdown'/);});
test('both application entries include the new stylesheet last',()=>{for(const p of ['app/layout.tsx','mobile/main.tsx']){const css=[...read(p).matchAll(/import ['"]([^'"]+\.css)['"]/g)].map(m=>m[1]);assert.ok(css.at(-1).endsWith('/clock-hotfix3.css'));}});
test('system alarm functionality stays available behind explicit disclosure',()=>{assert.match(read('components/timer-workspace.tsx'),/label="系统闹钟与锁屏提醒"/);assert.match(read('components/timer-workspace.tsx'),/<ReminderPanel\/>/);assert.match(read('components/reminder-panel.tsx'),/bridge.clockAction!/);});

test('compact statistics dates remain one date and do not alter the value or picker behavior',()=>{assert.match(read('components/timer-workspace.tsx'),/<DateField compact aria-label="学习统计日期"/);assert.match(read('components/date-field.tsx'),/compact\?'\/':' \/ '/);});
