'use client';
import { cloneElement, isValidElement, useCallback, useLayoutEffect, useRef, useState, type ReactElement, type ReactNode, type Ref } from 'react';
import { MOTION, prefersReducedMotion, settleAnimation } from '@/lib/motion';

type ChildProps = { ref?: Ref<HTMLElement>; 'data-motion-presence'?: string; inert?: boolean; 'aria-hidden'?: boolean };
/** Retain the actual element through its exit, with no extra layout or portal wrapper. */
export function MotionPresence({ present, children, kind = 'local' }: { present: boolean; children: ReactNode; kind?: 'local' | 'sheet' | 'fade' }) {
  const last = useRef<ReactElement<ChildProps> | null>(null);
  const [retained, setRetained] = useState(present);
  const element = useRef<HTMLElement | null>(null);
  const active = useRef<Animation | null>(null);
  const interrupted = useRef<{ opacity: string; translate: string } | null>(null);
  const originalRef = useRef<Ref<HTMLElement> | undefined>(undefined);
  if (present && isValidElement<ChildProps>(children)) { last.current = children; originalRef.current = children.props.ref; }
  const attach = useCallback((node: HTMLElement | null) => {
    element.current = node;
    if (!node) { active.current?.cancel(); active.current = null; interrupted.current = null; }
    const ref = originalRef.current;
    if (typeof ref === 'function') ref(node);
    else if (ref) ref.current = node;
  }, []);
  useLayoutEffect(() => {
    const node = element.current;
    if (!node) return;
    const previous = active.current;
    const current = interrupted.current ?? (previous ? getComputedStyle(node) : null);
    interrupted.current = null;
    const opacity = current?.opacity ?? (present ? '0' : '1');
    const translate = current?.translate ?? (present && kind !== 'fade' ? `0 ${kind === 'sheet' ? 14 : 6}px` : '0 0');
    previous?.cancel();
    const controller = new AbortController();
    if (present) setRetained(true);
    if (prefersReducedMotion() || document.hidden || !node.animate) {
      if (!present) { setRetained(false); last.current = null; }
      return;
    }
    const animation = node.animate([
      { opacity, translate },
      { opacity: present ? 1 : 0, translate: !present && kind !== 'fade' ? `0 ${kind === 'sheet' ? 10 : 4}px` : '0 0' },
    ], { duration: present ? MOTION.enter : MOTION.exit, easing: MOTION.easing, fill: 'both' });
    active.current = animation;
    void settleAnimation(animation, controller.signal).then(done => {
      if (!done || controller.signal.aborted || active.current !== animation) return;
      if (!present) {
        // Keep the transparent final frame until React removes the node.
        setRetained(false); last.current = null;
      } else { animation.cancel(); active.current = null; }
    });
    // React runs cleanup before the new effect: capture the live frame before cancelling.
    return () => {
      if (active.current === animation && element.current === node && node.isConnected) {
        const style = getComputedStyle(node);
        interrupted.current = { opacity: style.opacity, translate: style.translate };
      }
      controller.abort();
    };
  }, [present, kind]);
  useLayoutEffect(() => () => { active.current?.cancel(); }, []);
  if ((!present && !retained) || !last.current) return null;
  return cloneElement(last.current, { ref: attach, 'data-motion-presence': present ? 'present' : 'exiting', inert: !present, 'aria-hidden': !present || undefined });
}
