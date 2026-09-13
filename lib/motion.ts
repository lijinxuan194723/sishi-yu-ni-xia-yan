export const MOTION = Object.freeze({ enter: 320, exit: 240, photo: 520, easing: 'cubic-bezier(.4,0,.2,1)' });
export function prefersReducedMotion() {
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}
/** Finish rather than strand a presence layer when the document becomes hidden. */
export function settleAnimation(animation: Animation, signal?: AbortSignal): Promise<boolean> {
  return new Promise(resolve => {
    let settled = false;
    const media = typeof matchMedia === 'function' ? matchMedia('(prefers-reduced-motion: reduce)') : null;
    const finish = () => { try { animation.finish(); } catch { complete(true); } };
    const visibility = () => { if (document.hidden) finish(); };
    const preference = () => { if (media?.matches) finish(); };
    const abort = () => { animation.cancel(); complete(false); };
    function complete(ok: boolean) {
      if (settled) return; settled = true;
      signal?.removeEventListener('abort', abort);
      if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', visibility);
      media?.removeEventListener('change', preference);
      resolve(ok);
    }
    animation.finished.then(() => complete(true), () => complete(false));
    signal?.addEventListener('abort', abort, { once: true });
    if (typeof document !== 'undefined') document.addEventListener('visibilitychange', visibility);
    media?.addEventListener('change', preference);
    if (signal?.aborted) abort();
    else if (media?.matches || (typeof document !== 'undefined' && document.hidden)) finish();
  });
}
