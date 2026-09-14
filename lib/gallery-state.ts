/** Keep image framing independent of the root palette while an album is replaced. */
export type GallerySeason = 'spring' | 'summer' | 'autumn' | 'winter';
export function albumIndex(index: number, count: number): number {
  if (!Number.isInteger(count) || count < 1) return 0;
  return Math.max(0, Math.min(count - 1, Number.isFinite(index) ? Math.floor(index) : 0));
}
export function photoPosition(season: GallerySeason): string {
  return `var(--hero-photo-x, 75%) ${season === 'spring' || season === 'winter' ? '40%' : '32%'}`;
}
