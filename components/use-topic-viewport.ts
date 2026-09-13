'use client';
import { useEffect, useState, type CSSProperties } from 'react';
import { topicViewportMetrics } from '@/lib/topic-viewport';
type ViewportStyle = CSSProperties & { '--topic-viewport-height': string; '--topic-viewport-center': string };
/** Local to the popup: never override Android's existing keyboard bridge. */
export function useTopicViewport(): CSSProperties | undefined {
  const [style, setStyle] = useState<ViewportStyle>();
  useEffect(() => {
    const viewport = window.visualViewport;
    let frame = 0;
    const update = () => {
      frame = 0;
      const metrics = topicViewportMetrics(viewport, window.innerHeight);
      if (!metrics) return;
      setStyle(previous => previous?.['--topic-viewport-height'] === metrics.height &&
        previous?.['--topic-viewport-center'] === metrics.center ? previous : {
          '--topic-viewport-height': metrics.height, '--topic-viewport-center': metrics.center,
        });
    };
    const schedule = () => { if (!frame) frame = window.requestAnimationFrame(update); };
    const resume = () => { if (document.visibilityState === 'visible') schedule(); };
    update();
    viewport?.addEventListener('resize', schedule); viewport?.addEventListener('scroll', schedule);
    window.addEventListener('resize', schedule); window.addEventListener('orientationchange', schedule);
    document.addEventListener('visibilitychange', resume);
    return () => {
      if (frame) window.cancelAnimationFrame(frame);
      viewport?.removeEventListener('resize', schedule); viewport?.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule); window.removeEventListener('orientationchange', schedule);
      document.removeEventListener('visibilitychange', resume);
    };
  }, []);
  return style;
}
