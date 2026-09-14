/** Unsaved settings live in this process only; never persist credentials as drafts. */
export function createTransientDrafts(ttl = 30 * 60 * 1000, now = () => Date.now()) {
  const entries = new Map<string, { base: string; value: string; touched: number }>();
  function read<T>(key: string, baseline: T): T {
    const entry = entries.get(key), base = JSON.stringify(baseline);
    if (!entry || entry.base !== base || now() - entry.touched > ttl) {
      entries.delete(key);
      return baseline;
    }
    entry.touched = now();
    return JSON.parse(entry.value) as T;
  }
  function write<T>(key: string, baseline: T, value: T) {
    const base = JSON.stringify(baseline), content = JSON.stringify(value);
    if (base === content) entries.delete(key);
    else entries.set(key, { base, value: content, touched: now() });
  }
  return { read, write, clear: (key: string) => { entries.delete(key); } };
}
