import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  CHAT_BACKGROUND_KEY, MAX_BACKGROUND_BYTES, validateBackgroundFile, backgroundDimensions,
  isStoredBackground, saveChatBackground,
} from '../../lib/chat-background.ts';
import { topicViewportMetrics } from '../../lib/topic-viewport.ts';
import { RELEASE_INFO, releaseSummary, RELEASE_SOURCE_URL } from '../../lib/release-info.ts';

for (const type of ['image/jpeg', 'image/png', 'image/webp', 'IMAGE/PNG']) {
  test(`accept supported image ${type}`, () => assert.doesNotThrow(() => validateBackgroundFile({ size: 42, type })));
}
for (const type of ['image/svg+xml', 'image/gif', 'text/plain', '']) {
  test(`reject unsupported upload ${type || '(empty MIME)'}`, () => assert.throws(() => validateBackgroundFile({ size: 42, type })));
}
for (const size of [0, -1, NaN, Infinity, MAX_BACKGROUND_BYTES + 1]) {
  test(`reject invalid image size ${size}`, () => assert.throws(() => validateBackgroundFile({ size, type: 'image/png' })));
}
test('accept exact eight MiB limit', () => assert.doesNotThrow(() => validateBackgroundFile({ size: MAX_BACKGROUND_BYTES, type: 'image/png' })));
test('portrait keeps aspect ratio', () => assert.deepEqual(backgroundDimensions(2400, 3200), { width: 1080, height: 1440 }));
test('landscape keeps aspect ratio', () => assert.deepEqual(backgroundDimensions(3200, 2400), { width: 1440, height: 1080 }));
test('small images are not enlarged', () => assert.deepEqual(backgroundDimensions(300, 400), { width: 300, height: 400 }));
test('very narrow images retain one pixel', () => assert.deepEqual(backgroundDimensions(1, 10000), { width: 1, height: 1440 }));
for (const dimensions of [[0, 1], [-1, 1], [1.5, 2], [NaN, 100], [Infinity, 1], [10000, 10000]]) {
  test(`reject invalid or excessive resolution ${dimensions}`, () => assert.throws(() => backgroundDimensions(...dimensions)));
}
for (const format of ['png', 'jpeg', 'webp', 'gif']) {
  test(`existing data URL ${format} remains valid`, () => assert.equal(isStoredBackground(`data:image/${format};base64,AAAA`), true));
}
for (const value of ['https://example.com/x.png', 'javascript:alert(1)', 'data:image/svg+xml;base64,AAAA', 'data:image/png;base64,AAAA");background:url(x)', 'data:image/png;base64,']) {
  test(`reject unsafe background prefix ${value.slice(0, 35)}`, () => assert.equal(isStoredBackground(value), false));
}
function storage(initial = 'original') {
  const values = new Map([[CHAT_BACKGROUND_KEY, initial]]);
  return { values, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
}
test('save writes only the background storage key', () => {
  const s = storage(); s.values.set('luke-companion-v1', 'keep');
  saveChatBackground(s, 'data:image/webp;base64,AAAA');
  assert.equal(s.values.get(CHAT_BACKGROUND_KEY), 'data:image/webp;base64,AAAA');
  assert.equal(s.values.get('luke-companion-v1'), 'keep');
});
test('reset preserves unrelated data', () => {
  const s = storage(); s.values.set('luke-topic-chats-v1', 'keep'); saveChatBackground(s, null);
  assert.equal(s.values.has(CHAT_BACKGROUND_KEY), false); assert.equal(s.values.get('luke-topic-chats-v1'), 'keep');
});
test('invalid image never overwrites saved background', () => {
  const s = storage(); assert.throws(() => saveChatBackground(s, 'invalid')); assert.equal(s.values.get(CHAT_BACKGROUND_KEY), 'original');
});
test('quota error is surfaced with original data intact', () => {
  const s = storage(); s.setItem = () => { throw new Error('quota'); };
  assert.throws(() => saveChatBackground(s, 'data:image/png;base64,AAAA'), /quota/);
  assert.equal(s.values.get(CHAT_BACKGROUND_KEY), 'original');
});
test('normal visual viewport', () => assert.deepEqual(topicViewportMetrics({ height: 800, offsetTop: 0, scale: 1 }, 800), { height: '800px', center: '400px' }));
test('keyboard with viewport offset', () => assert.deepEqual(topicViewportMetrics({ height: 400, offsetTop: 24, scale: 1 }, 800), { height: '400px', center: '224px' }));
test('fallback without visualViewport', () => assert.deepEqual(topicViewportMetrics(null, 640), { height: '640px', center: '320px' }));
test('pinch zoom does not masquerade as keyboard', () => assert.equal(topicViewportMetrics({ height: 300, offsetTop: 0, scale: 2 }, 800), null));
test('negative viewport offset clamps to zero', () => assert.deepEqual(topicViewportMetrics({ height: 400, offsetTop: -10 }, 800), { height: '400px', center: '200px' }));
test('invalid viewport values remain finite', () => assert.deepEqual(topicViewportMetrics({ height: NaN, offsetTop: Infinity }, 700), { height: '700px', center: '350px' }));
test('invalid fallback cannot create NaN CSS', () => assert.deepEqual(topicViewportMetrics(null, NaN), { height: '1px', center: '0.5px' }));
test('subpixel scroll measurements coalesce', () => assert.deepEqual(topicViewportMetrics({ height: 600.01, offsetTop: 0.01 }, 800), topicViewportMetrics({ height: 600.02, offsetTop: 0.02 }, 800)));

// Source contracts: these validate identity and wiring, not Android runtime behavior.
test('version matches npm and Android manifest', () => {
  const pkg = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url)));
  const manifest = readFileSync(new URL('../../mobile/android/AndroidManifest.xml', import.meta.url), 'utf8');
  assert.equal(pkg.version, RELEASE_INFO.version);
  assert.ok(manifest.includes(`android:versionName='${RELEASE_INFO.version}'`));
  assert.ok(manifest.includes(`android:versionCode='${RELEASE_INFO.androidVersionCode}'`));
  assert.ok(manifest.includes("android:label='四时与你'"));
});
test('copyable information includes version and precise branch URL', () => {
  assert.ok(releaseSummary().includes(RELEASE_INFO.version)); assert.ok(releaseSummary().includes(RELEASE_SOURCE_URL));
  assert.ok(!/localStorage|Bearer|api[_ -]?key/i.test(releaseSummary()));
});
test('custom background stylesheet is wired with explicit selector', () => {
  const css = readFileSync(new URL('../../components/appearance-polish.module.css', import.meta.url), 'utf8');
  assert.ok(css.includes("data-chat-background='custom'")); assert.ok(css.includes('var(--chat-user-background)!important'));
});
test('startup restores the background before readiness bridge effect', () => {
  const source = readFileSync(new URL('../../components/startup.tsx', import.meta.url), 'utf8');
  assert.ok(source.indexOf('restoreBackground();') < source.indexOf('pageReady?.()'));
  assert.ok(source.includes("removeEventListener('storage', onStorage)"));
});
