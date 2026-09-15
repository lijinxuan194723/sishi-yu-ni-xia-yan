'use client';
import { useEffect, useId, useRef, useState } from 'react';
import { ImagePlus, RotateCcw } from 'lucide-react';
import { Ambience, SeasonPhoto, useAmbience } from './ambience';
import { seasons, periods } from '@/lib/ambience';
import { CHAT_BACKGROUND_KEY, isStoredBackground, prepareChatBackground, saveChatBackground, applyChatBackground } from '@/lib/chat-background';
import { FeedbackSettings } from './feedback-settings';
import {FontSettings} from './chat-appearance';
import styles from './appearance-polish.module.css';

export function AppearanceSettings() {
  const { scene, options, setOptions, appearanceError } = useAmbience(false, true);
  const [background, setBackground] = useState('');
  const [busy, setBusy] = useState(false);
  const [backgroundError, setBackgroundError] = useState('');
  const [notice, setNotice] = useState('');
  const selection = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const helpId = useId();
  useEffect(() => {
    try {
      const saved = localStorage.getItem(CHAT_BACKGROUND_KEY);
      if (saved && isStoredBackground(saved)) { applyChatBackground(saved); setBackground(saved); }
      else if (saved) setBackgroundError('原背景暂时无法显示，原记录已保留。可重新选择或恢复季节背景。');
    } catch { setBackgroundError('暂时无法读取本地背景，请检查浏览器存储权限。'); }
    return () => { selection.current?.abort(); selection.current = null; };
  }, []);
  async function choose(file: File) {
    selection.current?.abort();
    const active = new AbortController(); selection.current = active;
    setBusy(true); setBackgroundError(''); setNotice('');
    try {
      const value = await prepareChatBackground(file, active.signal);
      if (active.signal.aborted || selection.current !== active) return;
      try { saveChatBackground(localStorage, value); }
      catch { throw new Error('本地空间不足或存储不可用，原背景没有改变。请导出备份、整理空间后重试。'); }
      applyChatBackground(value); setBackground(value); setNotice('新背景已保存，仅存储在本机。');
    } catch (error) {
      if (!active.signal.aborted && selection.current === active) setBackgroundError(error instanceof Error ? error.message : '图片处理失败，原背景已保留。');
    } finally {
      if (selection.current === active) { selection.current = null; setBusy(false); }
    }
  }
  function reset() {
    selection.current?.abort(); selection.current = null; setBusy(false);
    try {
      saveChatBackground(localStorage, null); applyChatBackground(null);
      setBackground(''); setBackgroundError(''); setNotice('已恢复随季节切换的默认聊天背景。');
      if (input.current) input.current.value = '';
    } catch { setBackgroundError('暂时无法恢复背景，原背景已保留。请检查存储权限后重试。'); }
  }
  return <div className="settings-stack">
    <div className="appearance-preview" aria-busy={!scene}>{scene ? <><SeasonPhoto season={scene.season} /><Ambience scene={scene} /></> : <span role="status">正在准备外观…</span>}</div>
    <fieldset className="appearance-options"><legend>季节主题</legend><div>{(['auto', ...seasons] as const).map((value, index) => <button type="button" key={value} disabled={!scene} aria-pressed={options.season === value} onClick={() => setOptions({ ...options, season: value })}>{['随日期', '春', '夏', '秋', '冬'][index]}</button>)}</div></fieldset>
    <fieldset className="appearance-options"><legend>昼夜光照</legend><div>{(['auto', ...periods] as const).map(value => <button type="button" key={value} disabled={!scene} aria-pressed={options.period === value} onClick={() => setOptions({ ...options, period: value })}>{value === 'auto' ? '随时间' : value}</button>)}</div></fieldset>
    <label className="inline-label"><input type="checkbox" role="switch" aria-checked={options.effects !== false} checked={options.effects !== false} onChange={event => setOptions({ ...options, effects: event.target.checked })} /> 季节动画</label>
    <p className="feedback-help">开屏圆章与这里的季节一致；“随日期”会在每次打开时重新判断季节。</p>
    <FontSettings/><FeedbackSettings />
    <section className={styles.background} aria-label="悄悄话背景设置" aria-busy={busy}>
      <h3>悄悄话背景</h3><p className={styles.help} id={helpId}>支持 JPG、PNG、WebP，最大 8 MB。自动优化图片尺寸，节省本地空间，不上传图片。</p>
      {background && <img className={styles.preview} src={background} alt="当前自定义聊天背景" onError={() => setBackgroundError('已保存的背景无法显示，原记录已保留。可重新选择或恢复季节背景。')} />}
      <div className={styles.actions}>
        <label className={styles.upload}><ImagePlus size={17} aria-hidden="true" /><span>{busy ? '正在优化图片…' : background ? '更换背景图片' : '选择背景图片'}</span>
          <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" aria-label="选择悄悄话背景图片" aria-describedby={helpId} onChange={event => { const file = event.currentTarget.files?.[0]; event.currentTarget.value = ''; if (file) void choose(file); }} />
        </label>
        <button type="button" disabled={!background && !busy && !backgroundError} onClick={reset}><RotateCcw size={16} aria-hidden="true" />恢复季节背景</button>
      </div>
      {busy && <p className={styles.help} role="status">正在读取并压缩图片，原背景暂时保持不变。</p>}
      {backgroundError && <p className={styles.error} role="alert">{backgroundError}</p>}
      {notice && <p className={styles.help} role="status">{notice}</p>}
    </section>
    {appearanceError && <p role="alert">{appearanceError}</p>}
  </div>;
}
