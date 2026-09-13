'use client';
import { useEffect, useRef, useState } from 'react';
import { Copy, ExternalLink } from 'lucide-react';
import { RELEASE_INFO, RELEASE_SOURCE_URL, RELEASE_BUILDS_URL, releaseSummary } from '@/lib/release-info';
import styles from './appearance-polish.module.css';

export function BuildInfo() {
  const [notice, setNotice] = useState('');
  const [manualCopy, setManualCopy] = useState(false);
  const [copying, setCopying] = useState(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  async function copy() {
    setCopying(true);
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(releaseSummary());
      if (mounted.current) { setNotice('版本与位置信息已复制，不包含聊天或密钥。'); setManualCopy(false); }
    } catch {
      if (mounted.current) { setNotice('当前环境不支持直接复制，可长按下方文字复制。'); setManualCopy(true); }
    } finally { if (mounted.current) setCopying(false); }
  }
  return <details className={styles.release}>
    <summary><span>版本与更新位置</span><span className={styles.version}>{RELEASE_INFO.version}</span></summary>
    <div className={styles.releaseBody}>
      <dl><div><dt>版本</dt><dd>{RELEASE_INFO.version} · {RELEASE_INFO.channel}</dd></div>
        <div><dt>构建编号</dt><dd>{RELEASE_INFO.androidVersionCode}</dd></div>
        <div><dt>代码分支</dt><dd><code>{RELEASE_INFO.branch}</code></dd></div></dl>
      <p className={styles.help}>预览版与正式版并存。请保留原应用，两个版本的本地记录不会自动互通。</p>
      <div className={styles.actions}>
        <button type="button" disabled={copying} onClick={() => void copy()}><Copy size={16} aria-hidden="true" />{copying ? '正在复制…' : '复制版本信息'}</button>
        <a href={RELEASE_SOURCE_URL} target="_blank" rel="noreferrer">源码位置<ExternalLink size={15} aria-hidden="true" /></a>
        <a href={RELEASE_BUILDS_URL} target="_blank" rel="noreferrer">构建与下载位置<ExternalLink size={15} aria-hidden="true" /></a>
      </div>
      {notice && <p className={styles.help} role="status">{notice}</p>}
      {manualCopy && <textarea className={styles.copyText} readOnly aria-label="可手动复制的版本与位置" value={releaseSummary()} onFocus={event => event.currentTarget.select()} />}
    </div>
  </details>;
}
