import { MOTION, prefersReducedMotion, settleAnimation } from './motion.ts';

export async function swapPhoto(front: HTMLImageElement, back: HTMLImageElement, src: string, position: string, cancelled: () => boolean) {
  back.style.opacity = '0'; back.style.zIndex = '1'; back.style.objectPosition = position;
  back.src = src;
  try { await back.decode(); } catch (error) { back.style.opacity = '0'; throw error; }
  if (cancelled()) return false;
  // The fully decoded previous frame remains opaque underneath throughout the fade.
  let animation: Animation | undefined;
  if (!prefersReducedMotion() && !document.hidden && typeof back.animate === 'function') {
    animation = back.animate([{ opacity: 0 }, { opacity: 1 }], { duration: MOTION.photo, easing: MOTION.easing, fill: 'both' });
    await settleAnimation(animation);
  }
  // Once visible, finish this crossfade before consuming the newest queued selection.
  // Cancelling at its end would flash backwards to the old photograph.
  back.style.opacity = '1'; back.style.zIndex = '0'; front.style.opacity = '0';
  animation?.cancel();
  return true;
}
