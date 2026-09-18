'use client';
import { useEffect, useState } from 'react';
export function useReducedMotion() {
  const [reduced, setReduced] = useState(() => typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches);
  useEffect(() => {
    const query = matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => setReduced(query.matches||document.documentElement.dataset.effects==='off');
    const attributes=new MutationObserver(update);attributes.observe(document.documentElement,{attributes:true,attributeFilter:['data-effects']});
    update(); query.addEventListener('change', update);
    return () => {query.removeEventListener('change', update);attributes.disconnect();};
  }, []);
  return reduced;
}
