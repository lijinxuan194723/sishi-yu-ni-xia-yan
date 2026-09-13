'use client';
import { useEffect, useState, type CSSProperties } from 'react';

/** Local to the popup: do not override Android's existing keyboard bridge. */
export function useTopicViewport(): CSSProperties | undefined {
  const [style, setStyle] = useState<CSSProperties>();
  useEffect(() => {
    const viewport = window.visualViewport;
    let frame = 0;
    const update = () => {
      frame = 0;
      const height = Math.max(1, viewport?.height ?? window.innerHeight);
      const offset = Math.max(0, viewport?.offsetTop ?? 0);
      setStyle({
        '--topic-viewport-height': `${height}px`,
        '--topic-viewport-center': `${offset + height / 2}px`,
      } as CSSProperties);
    };
    const schedule = () => {
      if (!frame) frame = window.requestAnimationFrame(update);
    };
    update();
    viewport?.addEventListener('resize', schedule);
    viewport?.addEventListener('scroll', schedule);
    window.addEventListener('resize', schedule);
    window.addEventListener('orientationchange', schedule);
    return () => {
      if (frame) window.cancelAnimationFrame(frame);
      viewport?.removeEventListener('resize', schedule);
      viewport?.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule);
      window.removeEventListener('orientationchange', schedule);
    };
  }, []);
  return style;
}
