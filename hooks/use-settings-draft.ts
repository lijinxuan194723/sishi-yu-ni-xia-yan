'use client';
import { useEffect, useRef, useState, type SetStateAction } from 'react';
import { createTransientDrafts } from '@/lib/transient-form';
const drafts = createTransientDrafts();

/** Keep an unfinished form through settings subpage/close navigation, in RAM only. */
export function useSettingsDraft<T>(key: string, baseline: T) {
  const base = useRef(baseline); base.current = baseline;
  const [value, setValue] = useState<T>(() => drafts.read(key, baseline));
  const current = useRef(value); current.current = value;
  const fingerprint = JSON.stringify(baseline);
  const previous = useRef(fingerprint);
  useEffect(() => {
    if (previous.current === fingerprint) return;
    previous.current = fingerprint;
    const next = drafts.read(key, base.current);
    current.current = next; setValue(next);
  }, [fingerprint, key]);
  const dirty = JSON.stringify(value) !== fingerprint;
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  function update(action: SetStateAction<T>) {
    const next = typeof action === 'function' ? (action as (before: T) => T)(current.current) : action;
    drafts.write(key, base.current, next);
    current.current = next; setValue(next);
  }
  function discard() { drafts.clear(key); current.current = base.current; setValue(base.current); }
  function committed() { drafts.clear(key); }
  return { value, update, dirty, discard, committed };
}
