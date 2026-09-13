/** v2.0.1: pure, independently testable helpers. The v1 storage schema is unchanged. */
export type TopicMessage = { role: 'user' | 'assistant'; content: string };
export type SavedTopicThread = { messages: TopicMessage[]; draft: string };
export type TopicStorage = Pick<Storage, 'getItem' | 'setItem'>;
export const TOPIC_DRAFT_DELAY = 280;
export const TOPIC_MESSAGE_LIMIT = 2000;
export const TOPIC_PAGE_SIZE = 60;

export class TopicStorageConflict extends Error {
  constructor() {
    super('这个话题已在另一个窗口更新。你的内容仍在当前窗口，请先保留草稿，再重新读取。');
    this.name = 'TopicStorageConflict';
  }
}

export function parseTopicArchive(raw: string | null): Record<string, SavedTopicThread> {
  let value: unknown;
  try { value = JSON.parse(raw ?? '{}'); }
  catch { throw new Error('独立对话存档无法读取，原数据已保留，请勿清除应用数据。'); }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('独立对话存档格式异常，原数据已保留。');
  }
  for (const item of Object.values(value)) {
    if (!item || typeof item !== 'object' || typeof item.draft !== 'string' ||
        !Array.isArray(item.messages) || !item.messages.every((message: unknown) => {
          if (!message || typeof message !== 'object') return false;
          const m = message as Record<string, unknown>;
          return (m.role === 'user' || m.role === 'assistant') && typeof m.content === 'string';
        })) {
      throw new Error('独立对话存档格式异常，原数据已保留。');
    }
  }
  // A null prototype makes arbitrary archive keys safe to round-trip.
  return Object.assign(Object.create(null), value) as Record<string, SavedTopicThread>;
}

/** Optimistic conflict detection; localStorage does not provide atomic multi-tab transactions. */
export class TopicSessionStore {
  private expected: string | undefined;
  private loaded = false;
  private storage: TopicStorage;
  private storageKey: string;
  private key: string;
  constructor(storage: TopicStorage, storageKey: string, key: string) {
    this.storage = storage; this.storageKey = storageKey; this.key = key;
  }

  read(): SavedTopicThread {
    const archive = parseTopicArchive(this.storage.getItem(this.storageKey));
    const thread = archive[this.key];
    this.expected = thread === undefined ? undefined : JSON.stringify(thread);
    this.loaded = true;
    // Never expose a mutable reference to the last-read archive.
    return thread ? JSON.parse(JSON.stringify(thread)) as SavedTopicThread : { messages: [], draft: '' };
  }

  write(next: SavedTopicThread): void {
    if (!this.loaded) throw new Error('请先读取会话记录，再保存。');
    const archive = parseTopicArchive(this.storage.getItem(this.storageKey));
    const actual = archive[this.key] === undefined ? undefined : JSON.stringify(archive[this.key]);
    if (actual !== this.expected) throw new TopicStorageConflict();
    const serialized = JSON.stringify(next);
    if (serialized === this.expected) return;
    archive[this.key] = next;
    this.storage.setItem(this.storageKey, JSON.stringify(archive));
    // Advance only after setItem succeeds; quota failures remain retryable.
    this.expected = serialized;
  }
}

export function prepareTopicMessages(
  thread: SavedTopicThread, editIndex: number | null, retry = false,
): TopicMessage[] {
  if (retry) {
    if (thread.messages.at(-1)?.role !== 'user') throw new Error('没有需要重试的消息。');
    return thread.messages.slice();
  }
  const content = thread.draft.trim();
  if (!content) throw new Error('先写一点想说的话吧。');
  if (thread.draft.length > TOPIC_MESSAGE_LIMIT) throw new Error('消息最多 2000 个字符，请分段发送。');
  if (editIndex !== null && (!Number.isInteger(editIndex) || editIndex < 0 ||
      thread.messages[editIndex]?.role !== 'user')) throw new Error('待修改的消息已变化，请重新选择。');
  const previous = editIndex === null ? thread.messages : thread.messages.slice(0, editIndex);
  return [...previous, { role: 'user', content }];
}

function literalPattern(query: string, global = false): RegExp | null {
  const value = query.trim();
  if (!value) return null;
  return new RegExp(value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), global ? 'giu' : 'iu');
}

export function searchTopicMessages(messages: TopicMessage[], query: string): number[] {
  const pattern = literalPattern(query);
  return messages.reduce<number[]>((indices, message, index) => {
    if (!pattern || pattern.test(message.content)) indices.push(index);
    return indices;
  }, []);
}

export function literalHighlights(text: string, query: string): { text: string; match: boolean }[] {
  const pattern = literalPattern(query, true);
  if (!pattern) return [{ text, match: false }];
  const result: { text: string; match: boolean }[] = [];
  let position = 0;
  for (const found of text.matchAll(pattern)) {
    const index = found.index!;
    if (index > position) result.push({ text: text.slice(position, index), match: false });
    result.push({ text: found[0], match: true });
    position = index + found[0].length;
  }
  if (position < text.length) result.push({ text: text.slice(position), match: false });
  return result;
}
