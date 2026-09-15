import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { albumIndex, photoPosition } from '../../lib/gallery-state.ts';
import { companionNote, LUKE_NOTES, completedMinutesByDay, formatCompanionMinutes, studyMonth } from '../../lib/luke-companion.ts';
for (const [index,count,want] of [[0,5,0],[4,4,3],[5,6,5],[-1,4,0],[NaN,5,0],[Infinity,4,0],[3.9,5,3],[1,0,0]]) {
 test(`album selection clamps ${index}/${count}`,()=>assert.equal(albumIndex(index,count),want));
}
test('framing belongs to the displayed photograph, not root photo-y',()=>{
 for(const season of ['spring','summer','autumn','winter'])assert.ok(!photoPosition(season).includes('--photo-y'));
 assert.ok(photoPosition('spring').endsWith('40%'));assert.ok(photoPosition('summer').endsWith('32%'));
});
test('daily fan-work note is stable and explicit cycling reaches all notes',()=>{
 assert.equal(companionNote('2026-09-14'),companionNote('2026-09-14'));
 assert.equal(new Set(Array.from({length:LUKE_NOTES.length},(_,i)=>companionNote('2026-09-14',i))).size,LUKE_NOTES.length);
 assert.equal(companionNote('2026-09-14',NaN),companionNote('2026-09-14'));
});
test('empty history has no fabricated stamp',()=>assert.equal(Object.keys(completedMinutesByDay(undefined)).length,0));
test('positive saved fractions are retained, invalid entries excluded, input untouched',()=>{
 const logs=Object.freeze([{at:'2026-09-14T12:00:00',minutes:25},{at:'2026-09-14T13:00:00',minutes:.5},{at:'2026-09-15T12:00:00',minutes:0},{at:'broken',minutes:30},{at:'2026-09-15T12:00:00',minutes:-2},{at:'2026-09-15T12:00:00',minutes:NaN}].map(Object.freeze));
 const before=JSON.stringify(logs),daily=completedMinutesByDay(logs);
 assert.equal(daily['2026-09-14'],25.5);assert.equal(daily['2026-09-15'],undefined);assert.equal(JSON.stringify(logs),before);
});
for(const [day,count] of [['2024-02-10',29],['2026-02-10',28],['2026-09-14',30],['2026-12-20',31]]){
 test(`study calendar contains exactly the real days of ${day}`,()=>{
  const m=studyMonth(day);assert.equal(m.days.length,count);assert.ok(m.days.every(d=>d.startsWith(day.slice(0,7))));assert.equal(new Set(m.days).size,count);
 });
}
test('calendar month navigation crosses the year correctly',()=>{assert.equal(studyMonth('2026-12-01').next,'2027-01-01');assert.equal(studyMonth('2026-01-01').previous,'2025-12-01');assert.equal(studyMonth('2026-09-01').offset,1);});
test('invalid date cannot produce phantom calendar days',()=>assert.throws(()=>studyMonth('2026-02-31')));
for(const [minutes,label] of [[0,'0 分钟'],[-1,'0 分钟'],[.5,'不足 1 分钟'],[60,'1 小时'],[61.9,'1 小时 1 分钟']])test(`display ${minutes} minutes without inventing duration`,()=>assert.equal(formatCompanionMinutes(minutes),label));
test('gallery decodes actual cover before changing slides and explicitly controls reinitialization',()=>{
 const src=readFileSync('components/hero-gallery.tsx','utf8');
 assert.ok(src.indexOf('await coverNode.current?.decode()')<src.indexOf('setDisplayed(season)'));
 assert.ok(src.includes('watchSlides: false'));assert.ok(src.includes('key={i}'));assert.ok(src.includes('style={{ objectPosition: photoPosition(displayed) }}'));
});
test('card skin uses solid colour and never fades the home content to hide a flash',()=>{
 const src=readFileSync('app/luke-companion.css','utf8');assert.ok(src.includes('background-image:none;background-color:var(--scene-paper)'));
 assert.ok(!/home-grid[^}]*opacity\s*:\s*0/.test(src));
});
test('settings and main use a single live root palette without waiting for dismissal',()=>{const src=readFileSync('components/ambience.tsx','utf8');assert.ok(src.includes('luke-appearance-change'));assert.ok(!src.includes("!preview&&document.querySelector('.settings')"));assert.ok(!readFileSync('app/theme.css','utf8').includes(':is(:root,.settings)'));});
