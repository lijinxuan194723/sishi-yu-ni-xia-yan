/** Batch streaming text to paint frames instead of scheduling 125 React renders/second. */
export function fastTyping(publish: (text: string) => void, signal?: AbortSignal) {
  let target = '', shown = '', frame: number | ReturnType<typeof setTimeout> | undefined;
  let last = 0, credit = 0, ended = false;
  const waiting: (() => void)[] = [];
  const raf = typeof requestAnimationFrame === 'function';
  function cancel() {
    if (frame === undefined) return;
    if (raf) cancelAnimationFrame(frame as number); else clearTimeout(frame);
    frame = undefined;
  }
  function resolve() { waiting.splice(0).forEach(done => done()); }
  function publishChanged() { if (shown !== target) { shown = target; publish(shown); } resolve(); }
  function flush() { cancel(); publishChanged(); }
  function immediate() {
    return signal?.aborted || (typeof document !== 'undefined' && document.hidden) ||
      (typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches);
  }
  function schedule() { if (frame === undefined) frame = raf ? requestAnimationFrame(tick) : setTimeout(() => tick(performance.now()), 16); }
  function tick(now: number) {
    frame = undefined;
    if (immediate()) { flush(); return; }
    const elapsed = last ? Math.max(0, Math.min(48, now - last)) : 16;
    last = now;
    // Catch up gently when a provider delivers a large buffered chunk, without a final jump.
    credit += elapsed * (.09 + (target.length - shown.length) / 360);
    let count = Math.min(256, Math.floor(credit)); credit -= count;
    const before = shown;
    while (count-- > 0 && shown.length < target.length) shown += String.fromCodePoint(target.codePointAt(shown.length)!);
    if (shown !== before) publish(shown);
    if (shown.length < target.length) schedule(); else { last = 0; credit = 0; resolve(); }
  }
  const hidden = () => { if (document.hidden) flush(); };
  signal?.addEventListener('abort', flush, { once: true });
  if (typeof document !== 'undefined') document.addEventListener('visibilitychange', hidden);
  return {
    update(text: string) {
      if (ended) return;
      target = text; if (!target.startsWith(shown)) shown = '';
      if (immediate()) flush(); else if (shown !== target) schedule();
    },
    async finish() {
      if (immediate()) flush();
      if (shown !== target) { schedule(); await new Promise<void>(done => waiting.push(done)); }
      ended = true; cancel(); signal?.removeEventListener('abort', flush);
      if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', hidden);
    },
  };
}
