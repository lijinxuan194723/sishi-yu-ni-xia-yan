/** Half-pixel measurements avoid resizing the composer on every subpixel scroll. */
export type TopicViewport = { height: number; offsetTop?: number; scale?: number };
export function topicViewportMetrics(viewport: TopicViewport | null, fallbackHeight: number) {
  const scale = viewport?.scale ?? 1;
  // Pinch zoom is magnification, not an on-screen keyboard. Keep the last layout.
  if (Number.isFinite(scale) && Math.abs(scale - 1) > 0.02) return null;
  const rawHeight = viewport?.height ?? fallbackHeight;
  const height = Math.max(1, Math.round((Number.isFinite(rawHeight) && rawHeight > 0 ? rawHeight : Math.max(1, Number.isFinite(fallbackHeight) ? fallbackHeight : 1)) * 2) / 2);
  const rawOffset = viewport?.offsetTop ?? 0;
  const offset = Math.max(0, Math.round((Number.isFinite(rawOffset) ? rawOffset : 0) * 2) / 2);
  return { height: `${height}px`, center: `${offset + height / 2}px` };
}
