'use client';
import {motion} from 'motion/react';
import {Expand} from 'lucide-react';
import {PhotoDetail} from './photo-detail';
import { useCallback, useEffect, useLayoutEffect, useState, useRef, type ReactNode } from 'react';
import { flushSync } from 'react-dom';
import useEmblaCarousel from 'embla-carousel-react';
import { useReducedMotion } from '@/hooks/use-reduced-motion';
import { seasonAlbums } from '@/lib/season-albums';
import { albumIndex, photoPosition, type GallerySeason } from '@/lib/gallery-state';
import { MOTION, prefersReducedMotion, settleAnimation } from '@/lib/motion';

async function decoded(src: string) { const image = new Image(); image.src = src; await image.decode(); }

export function HeroGallery({ season, children }: { season: GallerySeason; children: ReactNode }) {
  const reduced = useReducedMotion();
  const [displayed, setDisplayed] = useState(season);
  const [cover, setCover] = useState<{ src: string; position: string } | null>(null);
  const [selected, setSelected] = useState(0);
  const [error, setError] = useState('');
  const [detail,setDetail]=useState<{src:string;id:string;alt:string}|null>(null);
  const shown = useRef(season), selectedRef = useRef(0), swapping = useRef(false);
  const pendingIndex = useRef<number | null>(null);
  const coverNode = useRef<HTMLImageElement>(null), animation = useRef<Animation | null>(null);
  const alive = useRef(true), navigation = useRef(0), queue = useRef(Promise.resolve());
  const watchDrag = useCallback((_api: unknown, event: MouseEvent | TouchEvent) =>
    !swapping.current && !(event.target as HTMLElement)?.closest('button,input,a'), []);
  // Changes to slide count are handled exactly once, below, behind the decoded cover.
  const [viewport, carousel] = useEmblaCarousel({ loop: true, container: '.hero-track', slides: '.hero-slide',
    duration: reduced ? 0 : 30, watchDrag, watchSlides: false });
  const api = useRef(carousel); api.current = carousel;
  useEffect(() => { alive.current = true; return () => { alive.current = false; animation.current?.cancel(); }; }, []);
  useEffect(() => {
    if (!carousel) return;
    const update = () => { selectedRef.current = carousel.selectedScrollSnap(); setSelected(selectedRef.current); };
    update(); carousel.on('select', update); carousel.on('reInit', update);
    return () => { carousel.off('select', update); carousel.off('reInit', update); };
  }, [carousel]);
  useLayoutEffect(() => {
    if (!carousel) return;
    const index = albumIndex(pendingIndex.current ?? selectedRef.current, seasonAlbums[displayed].length);
    pendingIndex.current = null;
    carousel.reInit({ startIndex: index });
  }, [carousel, displayed]);
  useEffect(() => {
    let cancelled = false;
    queue.current = queue.current.catch(() => {}).then(async () => {
      if (cancelled || !alive.current || shown.current === season) return;
      const previous = shown.current;
      const oldIndex = albumIndex(api.current?.selectedScrollSnap() ?? 0, seasonAlbums[previous].length);
      const index = albumIndex(oldIndex, seasonAlbums[season].length);
      const oldSrc = seasonAlbums[previous][oldIndex];
      const oldImage = api.current?.slideNodes()[oldIndex]?.querySelector('img');
      const oldPosition = oldImage ? getComputedStyle(oldImage).objectPosition : photoPosition(previous);
      try { await Promise.all([decoded(seasonAlbums[season][index]), decoded(oldSrc)]); }
      catch { if (alive.current && !cancelled) setError('照片暂时无法加载，已保留当前相册。'); return; }
      if (cancelled || !alive.current) return;
      swapping.current = true; navigation.current++;
      try {
        // Decode the actual DOM cover BEFORE touching slides, not an unattached lookalike.
        flushSync(() => setCover({ src: oldSrc, position: oldPosition }));
        await coverNode.current?.decode();
        if (!alive.current) return;
        pendingIndex.current = index;
        flushSync(() => { setDisplayed(season); setError(''); });
        await api.current?.slideNodes()[index]?.querySelector('img')?.decode();
        if (!alive.current) return;
        shown.current = season;
        const node = coverNode.current;
        if (node && !prefersReducedMotion() && !document.hidden) {
          const fade = node.animate([{ opacity: 1 }, { opacity: 0 }], { duration: MOTION.photo, easing: MOTION.easing, fill: 'both' });
          animation.current = fade; await settleAnimation(fade);
        }
      } catch {
        if (alive.current) {
          pendingIndex.current = oldIndex;
          flushSync(() => { setDisplayed(previous); setError('照片暂时无法加载，已保留当前相册。'); });
          shown.current = previous;
        }
      } finally {
        if (alive.current) flushSync(() => setCover(null));
        animation.current?.cancel(); animation.current = null; swapping.current = false;
      }
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
  return <><section ref={viewport} className="hero hero-gallery" data-album-season={displayed} data-photo-index={selected} aria-roledescription="可左右滑动的相册" aria-label="四季相册" tabIndex={0} onKeyDown={event => {
    if (event.target !== event.currentTarget) return;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); void go(selectedRef.current + (event.key === 'ArrowLeft' ? -1 : 1)); }
  }}><div className="hero-track">{seasonAlbums[displayed].map((src, i) => {
    const distance = Math.abs(i - selected), near = distance <= 1 || distance === seasonAlbums[displayed].length - 1;
    return <div className="hero-slide season-photo" key={i}><motion.img layoutId={i===selected?`gallery-${displayed}-${i}`:undefined} src={src} alt={`${{ spring: '春', summer: '夏', autumn: '秋', winter: '冬' }[displayed]}日夏彦 ${i + 1}`} style={{ objectPosition: photoPosition(displayed) }} loading={near ? 'eager' : 'lazy'} decoding="async" draggable={false} /></div>;
  })}</div>{cover && <img ref={coverNode} className="hero-season-cover" src={cover.src} alt="" aria-hidden="true" style={{ objectPosition: cover.position }} />}
    {children}<button type="button" className="round gallery-expand210" aria-label="放大查看相册照片" onClick={()=>setDetail({src:seasonAlbums[displayed][selected],id:`gallery-${displayed}-${selected}`,alt:`四季相册第 ${selected+1} 张`})}><Expand size={17}/></button><span className="sr-only" role="status">{error || `第 ${selected + 1} 张，共 ${seasonAlbums[displayed].length} 张；左右滑动切换照片。`}</span></section><PhotoDetail photo={detail} onClose={()=>setDetail(null)}/></>;
}
