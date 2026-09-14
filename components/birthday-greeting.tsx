'use client';
import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { Cake, Heart, X } from 'lucide-react';
import { Dialog, DialogContent, DialogTitle, DialogDescription } from '@/components/ui/dialog';

/** Settings and the greeting never own focus/scroll locks at the same time. */
export function useBirthdayGreeting({ ready, birthdayToday, day, settings, setSettings, blocked }: {
  ready: boolean; birthdayToday: boolean; day: string; settings: boolean;
  setSettings: (open: boolean) => void; blocked: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [preview, setPreview] = useState(false);
  const [epoch, setEpoch] = useState(0);
  const [nativeReady, setNativeReady] = useState(false);
  useEffect(() => {
    const root = document.documentElement;
    const sync = () => { if (root.dataset.nativeLaunching !== 'true') setNativeReady(true); };
    const observer = new MutationObserver(sync);
    observer.observe(root, { attributes: true, attributeFilter: ['data-native-launching'] });
    const wake = () => { if (!document.hidden) setEpoch(value => value + 1); };
    sync(); document.addEventListener('visibilitychange', wake);
    return () => { observer.disconnect(); document.removeEventListener('visibilitychange', wake); };
  }, []);
  const shown = useRef(new Set<string>());
  const settingsLayer = useRef(false);
  const pendingPreview = useRef(false);
  const returnToSettings = useRef(false);
  const greetingLayer = useRef(false);
  const greetingDay = useRef(day);
  if (settings) settingsLayer.current = true;
  const show = (isPreview: boolean) => {
    greetingLayer.current = true; greetingDay.current = day;
    returnToSettings.current = isPreview;
    setPreview(isPreview); setOpen(true);
  };
  useEffect(() => {
    if (!ready || !nativeReady || document.hidden || !birthdayToday || settings || settingsLayer.current || blocked || greetingLayer.current || shown.current.has(day)) return;
    try { if (localStorage.getItem('luke-birthday-seen') === day) return; } catch { /* In-memory guard still prevents repeated interruptions. */ }
    shown.current.add(day); show(false);
    // Day/session guards, not an animation timer, determine automatic display.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, nativeReady, birthdayToday, day, settings, blocked, epoch]);
  function beginPreview() {
    if (greetingLayer.current || pendingPreview.current) return;
    pendingPreview.current = true;
    if (settingsLayer.current) setSettings(false);
    else { pendingPreview.current = false; show(true); }
  }
  function settingsComplete(next: boolean) {
    settingsLayer.current = next;
    if (!next && pendingPreview.current) { pendingPreview.current = false; show(true); }
    else if (!next) setEpoch(value => value + 1);
  }
  function dismiss() {
    if (!open) return;
    if (!preview) {
      shown.current.add(greetingDay.current);
      try { localStorage.setItem('luke-birthday-seen', greetingDay.current); } catch { /* Never trap the user when persistence fails. */ }
    }
    setOpen(false);
  }
  function complete(next: boolean) {
    if (next || open) return;
    greetingLayer.current = false;
    if (returnToSettings.current) { returnToSettings.current = false; setSettings(true); }
  }
  return { open, preview, beginPreview, dismiss, complete, settingsComplete };
}

export function BirthdayGreeting({ name, open, preview, onClose, onComplete }: {
  name: string; open: boolean; preview: boolean; onClose: () => void; onComplete: (open: boolean) => void;
}) {
  return <Dialog open={open} onOpenChange={next => { if (!next) onClose(); }} onOpenChangeComplete={onComplete}>
    <DialogContent fullScreen showCloseButton={false} className="birthday-screen birthday-refined">
      <button type="button" className="birthday-close" data-slot="dialog-close" aria-label="关闭" onClick={onClose}><X size={20} aria-hidden="true" /></button>
      <div className="birthday-confetti" aria-hidden="true">{Array.from({ length: 12 }, (_, index) =>
        <i key={index} style={{ '--piece': index, left: `${(index * 37 + 7) % 100}%` } as CSSProperties} />)}</div>
      <div className="birthday-scroll"><div className="birthday-card">
        <p className="birthday-eyebrow">{preview ? '一份生日祝福 · 预览' : '今天，为你留一份温柔'}</p>
        <div className="birthday-emblem" aria-hidden="true"><Cake className="birthday-cake" size={58} strokeWidth={1.5} /></div>
        <DialogTitle>{name === '我' ? '亲爱的你' : name}，生日快乐</DialogTitle>
        <DialogDescription>新的一岁，也想陪你收藏每一个开心的瞬间。<br />今天的愿望，慢慢许，我在这里。<span>—— 夏彦</span></DialogDescription>
        <div className="birthday-actions">
          <button type="button" className="primary" data-haptic="confirm" onClick={onClose}>收下这份祝福 <Heart size={18} aria-hidden="true" /></button>
          <button type="button" className="birthday-later" onClick={onClose}>{preview ? '返回生日设置' : '先回到身边'}</button>
        </div>
        <small className="birthday-hint">无需等动画结束，随时可以关闭。</small>
      </div></div>
    </DialogContent>
  </Dialog>;
}
