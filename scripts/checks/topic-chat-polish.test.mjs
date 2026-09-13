import test from 'node:test';
import assert from 'node:assert/strict';
import {
  TopicSessionStore, TopicStorageConflict, parseTopicArchive,
  prepareTopicMessages, searchTopicMessages, literalHighlights,
} from '../../lib/topic-chat-session.ts';

const key = 'luke-topic-chats-v1';
const user = content => ({ role: 'user', content });
const assistant = content => ({ role: 'assistant', content });
function memoryStorage(initial = null) {
  let raw = initial;
  return { getItem: () => raw, setItem: (_key, next) => { raw = next; }, raw: () => raw };
}
const thread = (draft = '') => ({ messages: [user('原问题'), assistant('原回复')], draft });

test('empty and v2.0.0 archives remain readable', () => {
  assert.equal(Object.keys(parseTopicArchive(null)).length, 0);
  assert.deepEqual(parseTopicArchive(JSON.stringify({ song: thread('草稿') })).song, thread('草稿'));
});
for (const [name, raw] of [
  ['malformed JSON', '{bad'], ['null root', 'null'], ['array root', '[]'],
  ['missing draft', '{"song":{"messages":[]}}'],
  ['system message', '{"song":{"draft":"","messages":[{"role":"system","content":"bad"}]}}'],
  ['null message', '{"song":{"draft":"","messages":[null]}}'],
  ['numeric content', '{"song":{"draft":"","messages":[{"role":"user","content":1}]}}'],
]) test(`${name}: refuse without modifying bytes`, () => {
  const storage = memoryStorage(raw);
  assert.throws(() => new TopicSessionStore(storage, key, 'song').read());
  assert.equal(storage.raw(), raw);
});

test('cannot write before reading', () => {
  const storage = memoryStorage();
  assert.throws(() => new TopicSessionStore(storage, key, 'song').write(thread()));
  assert.equal(storage.raw(), null);
});

test('saving one topic leaves every other topic untouched', () => {
  const storage = memoryStorage(JSON.stringify({ song: thread(), book: thread('读到第三章') }));
  const song = new TopicSessionStore(storage, key, 'song'); song.read(); song.write(thread('新草稿'));
  assert.deepEqual(JSON.parse(storage.raw()).book, thread('读到第三章'));
  assert.equal(JSON.parse(storage.raw()).song.draft, '新草稿');
});

test('other-topic changes can be merged safely', () => {
  const storage = memoryStorage();
  const song = new TopicSessionStore(storage, key, 'song'); song.read();
  const book = new TopicSessionStore(storage, key, 'book'); book.read(); book.write(thread('书'));
  song.write(thread('歌'));
  assert.deepEqual(Object.keys(JSON.parse(storage.raw())).sort(), ['book', 'song']);
});

test('same-topic stale session cannot overwrite newer content', () => {
  const storage = memoryStorage();
  const first = new TopicSessionStore(storage, key, 'song'); first.read();
  const second = new TopicSessionStore(storage, key, 'song'); second.read();
  first.write(thread('新内容'));
  assert.throws(() => second.write(thread('旧内容')), TopicStorageConflict);
  assert.equal(JSON.parse(storage.raw()).song.draft, '新内容');
});

test('corruption after loading cannot be overwritten by draft autosave', () => {
  const storage = memoryStorage();
  const session = new TopicSessionStore(storage, key, 'song'); session.read();
  storage.setItem(key, '{bad');
  assert.throws(() => session.write(thread('不可覆盖')));
  assert.equal(storage.raw(), '{bad');
});

test('quota errors do not advance revision; saving can be retried', () => {
  const storage = memoryStorage();
  let fail = true;
  const session = new TopicSessionStore({ getItem: storage.getItem, setItem: (k, v) => {
    if (fail) throw new Error('QuotaExceededError'); storage.setItem(k, v);
  } }, key, 'song');
  session.read();
  assert.throws(() => session.write(thread('不能丢')));
  assert.equal(storage.raw(), null);
  fail = false; session.write(thread('不能丢'));
  assert.equal(JSON.parse(storage.raw()).song.draft, '不能丢');
});

test('unchanged saves are not rewritten', () => {
  const storage = memoryStorage(); let writes = 0;
  const session = new TopicSessionStore({getItem: storage.getItem, setItem: (k, v) => {
    writes++; storage.setItem(k, v);
  }}, key, 'song');
  session.read(); session.write(thread()); session.write(thread());
  assert.equal(writes, 1);
});

test('prototype-like archive keys are preserved as data', () => {
  const storage = memoryStorage('{"__proto__":{"messages":[],"draft":"保留"}}');
  const session = new TopicSessionStore(storage, key, 'song'); session.read(); session.write(thread());
  assert.equal(JSON.parse(storage.raw()).__proto__.draft, '保留');
  assert.equal({}.draft, undefined);
});

test('sending trims the new message without mutating history or draft', () => {
  const original = thread('  下一句  '); const before = JSON.stringify(original);
  assert.deepEqual(prepareTopicMessages(original, null), [...original.messages, user('下一句')]);
  assert.equal(JSON.stringify(original), before);
});

test('retry never duplicates the last user message', () => {
  const original = { messages: [user('仅发送一次')], draft: '下一句' };
  assert.deepEqual(prepareTopicMessages(original, null, true), original.messages);
  assert.throws(() => prepareTopicMessages(thread(), null, true));
});

test('editing branches at the selected user message only', () => {
  const original = thread('修订问题');
  assert.deepEqual(prepareTopicMessages(original, 0), [user('修订问题')]);
  assert.equal(original.messages.length, 2);
});

test('invalid edits and empty/overlong drafts are rejected', () => {
  for (const index of [-1, 1, 99, 0.5]) assert.throws(() => prepareTopicMessages(thread('x'), index));
  for (const draft of ['', '  \n ', 'x'.repeat(2001)]) assert.throws(() => prepareTopicMessages(thread(draft), null));
  assert.equal(prepareTopicMessages(thread('x'.repeat(2000)), null).at(-1).content.length, 2000);
});

test('search ignores surrounding whitespace and Latin case', () => {
  const messages = [user('Hello 夏彦'), assistant('HELLO'), user('书')];
  assert.deepEqual(searchTopicMessages(messages, ' hello '), [0, 1]);
  assert.deepEqual(searchTopicMessages(messages, '夏彦'), [0]);
  assert.deepEqual(searchTopicMessages(messages, '  '), [0, 1, 2]);
  assert.deepEqual(searchTopicMessages(messages, '未找到'), []);
});

test('search and highlight treat regex punctuation literally', () => {
  for (const query of ['[书]', 'a+b', '(x)', '.*', '$', '\\', '?', '{2}', '^', '|']) {
    const text = `前 ${query} 后 ${query}`;
    assert.deepEqual(searchTopicMessages([user(text), user('无匹配')], query), [0]);
    const parts = literalHighlights(text, query);
    assert.equal(parts.map(p => p.text).join(''), text);
    assert.equal(parts.filter(p => p.match).length, 2);
  }
});

test('highlights preserve Unicode, HTML text and all original characters', () => {
  for (const [text, query] of [['夏彦🌻夏彦', '夏彦'], ['İstanbul I', 'I'], ['<script>x</script>', '<script>'], ['', 'x'], ['hello', '']]) {
    assert.equal(literalHighlights(text, query).map(p => p.text).join(''), text);
  }
});

test('a delayed old-topic save can never write to the new topic key', () => {
  const storage = memoryStorage();
  const oldSession = new TopicSessionStore(storage, key, 'song'); oldSession.read();
  const newSession = new TopicSessionStore(storage, key, 'book'); newSession.read();
  newSession.write(thread('新窗口草稿')); oldSession.write(thread('旧窗口结束回复'));
  assert.equal(JSON.parse(storage.raw()).book.draft, '新窗口草稿');
  assert.equal(JSON.parse(storage.raw()).song.draft, '旧窗口结束回复');
});
