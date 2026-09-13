'use client';
import { useEffect, useLayoutEffect, useState, useRef, type ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import useEmblaCarousel from 'embla-carousel-react';
import { useReducedMotion } from '@/hooks/use-reduced-motion';
import { seasonAlbums } from '@/lib/season-albums';
import { MOTION, prefersReducedMotion, settleAnimation } from '@/lib/motion';

type Season = keyof typeof seasonAlbums;
const position = (season: Season) => `75% ${{ spring: '40%', summer: '32%', autumn: '32%', winter: '40%' }[season]}`;
async function decoded(src: string) { const image = new Image(); image.src = src; await image.decode(); }

export function HeroGallery({ season, children }: { season: Season; children: ReactNode }) {
  const reduced = useReducedMotion();
  const [displayed, setDisplayed] = useState(season);
  const [cover, setCover] = useState<{ src: string; position: string } | null>(null);
  const [selected, setSelected] = useState(0);
  const [error, setError] = useState('');
  const shown = useRef(season), selectedRef = useRef(0), swapping = useRef(false);
  const coverNode = useRef<HTMLImageElement>(null), animation = useRef<Animation | null>(null);
  const alive = useRef(true), navigation = useRef(0), queue = useRef(Promise.resolve());
  const [viewport, carousel] = useEmblaCarousel({ loop: true, container: '.hero-track', slides: '.hero-slide',
    duration: reduced ? 0 : 30, watchDrag: (_api, event) => !swapping.current && !(event.target as HTMLElement)?.closest('button,input,a') });
  const api = useRef(carousel); api.current = carousel;
  useEffect(() => { alive.current = true; return () => { alive.current = false; animation.current?.cancel(); }; }, []);
  useEffect(() => {
    if (!carousel) return;
    const update = () => { selectedRef.current = carousel.selectedScrollSnap(); setSelected(selectedRef.current); };
    update(); carousel.on('select', update); carousel.on('reInit', update);
    return () => { carousel.off('select', update); carousel.off('reInit', update); };
  }, [carousel]);
  useLayoutEffect(() => {
    const index = Math.min(selectedRef.current, seasonAlbums[displayed].length - 1);
    carousel?.reInit(); carousel?.scrollTo(index, true);
  }, [carousel, displayed]);
  useEffect(() => {
    let cancelled = false;
    queue.current = queue.current.catch(() => {}).then(async () => {
      if (cancelled || !alive.current || shown.current === season) return;
      const previous = shown.current;
      const oldIndex = api.current?.selectedScrollSnap() ?? 0;
      const index = Math.min(oldIndex, seasonAlbums[season].length - 1);
      const oldSrc = seasonAlbums[previous][oldIndex];
      const oldImage = api.current?.slideNodes()[oldIndex]?.querySelector('img');
      const oldPosition = oldImage ? getComputedStyle(oldImage).objectPosition : position(previous);
      try { await Promise.all([decoded(seasonAlbums[season][index]), decoded(oldSrc)]); }
      catch { if (alive.current && !cancelled) setError('照片暂时无法加载，已保留当前相册。'); return; }
      if (cancelled || !alive.current) return;
      swapping.current = true; navigation.current++;
      shown.current = season;
      // The old frame stays above Embla while it reinitializes its new slide geometry.
      flushSync(() => { setCover({ src: oldSrc, position: oldPosition }); setDisplayed(season); setError(''); });
      const node = coverNode.current;
      if (node && !prefersReducedMotion() && !document.hidden) {
        const fade = node.animate([{ opacity: 1 }, { opacity: 0 }], { duration: MOTION.photo, easing: MOTION.easing, fill: 'both' });
        animation.current = fade; await settleAnimation(fade);
      }
      if (!alive.current) return;
      flushSync(() => setCover(null));
      animation.current?.cancel(); animation.current = null; swapping.current = false;
    });
    return () => { cancelled = true; };
  }, [season]);
  async function go(index: number) {
    if (!carousel || swapping.current) return;
    const token = ++navigation.current, currentSeason = shown.current;
    const count = seasonAlbums[currentSeason].length;
    index = (index + count) % count;
    try { await decoded(seasonAlbums[currentSeason][index]); }
    catch { if (alive.current && token === navigation.current) setError('照片暂时无法加载，请重试。'); return; }
    if (!alive.current || token !== navigation.current || swapping.current || shown.current !== currentSeason) return;
    setError(''); carousel.scrollTo(index, prefersReducedMotion());
  }
  return <section ref={viewport} className="hero hero-gallery" aria-label="四季相册" tabIndex={0} onKeyDown={event => {
    if (event.target !== event.currentTarget) return;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); void go(selectedRef.current + (event.key === 'ArrowLeft' ? -1 : 1)); }
  }}><div className="hero-track">{seasonAlbums[displayed].map((src, i) => {
    const distance = Math.abs(i - selected), near = distance <= 1 || distance === seasonAlbums[displayed].length - 1;
    return <div className="hero-slide season-photo" key={src}><img src={src} alt={`${{ spring: '春', summer: '夏', autumn: '秋', winter: '冬' }[displayed]}日夏彦 ${i + 1}`} loading={near ? 'eager' : 'lazy'} decoding="async" draggable={false} /></div>;
  })}</div>{cover && <img ref={coverNode} className="hero-season-cover" src={cover.src} alt="" aria-hidden="true" style={{ objectPosition: cover.position }} />}
    {children}<div className="photo-navigation" aria-label="相册翻页">
      <button type="button" aria-label="上一张照片" onClick={() => void go(selectedRef.current - 1)}><ChevronLeft size={16} /></button>
      <div className="photo-dots">{seasonAlbums[displayed].map((src, i) => <button type="button" key={src} aria-label={`查看第 ${i + 1} 张照片`} aria-current={i === selected ? 'true' : undefined} onClick={() => void go(i)}><span /></button>)}</div>
      <span className="sr-only" role="status">{error || `第 ${selected + 1} 张，共 ${seasonAlbums[displayed].length} 张`}</span>
      <button type="button" aria-label="下一张照片" onClick={() => void go(selectedRef.current + 1)}><ChevronRight size={16} /></button>
    </div></section>;
}
