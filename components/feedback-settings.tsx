'use client';
import { useEffect, useState } from 'react';
import { FEEDBACK_KEY, feedbackMode, type FeedbackMode } from '@/lib/interaction-feedback';

export function FeedbackSettings() {
  const [mode, setMode] = useState<FeedbackMode>('gentle');
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    try { setMode(feedbackMode(localStorage.getItem(FEEDBACK_KEY))); setReady(true); }
    catch { setError('暂时无法读取触感设置。'); }
  }, []);
  function choose(next: FeedbackMode) {
    try {
      localStorage.setItem(FEEDBACK_KEY, next);
      setMode(next); setError('');
      window.dispatchEvent(new Event('luke-feedback-change'));
    } catch { setError('触感设置未能保存，原设置没有改变。'); }
  }
  return <fieldset className="appearance-options feedback-options">
    <legend>按钮触感</legend>
    <div>{(['gentle', 'off'] as const).map(value => <button type="button" key={value} disabled={!ready}
      aria-pressed={mode === value} data-haptic="off" onClick={() => choose(value)}>
      {value === 'gentle' ? '轻柔反馈' : '关闭触感'}</button>)}</div>
    <p className="feedback-help">切换选项、轻点和确认使用不同触感；遵循手机的触摸反馈设置，不连续震动。网页及不支持的设备静默降级。</p>
    <button type="button" className="soft-button" data-haptic="selection" disabled={!ready || mode === 'off'}>试试轻柔触感</button>
    {error && <p role="alert">{error}</p>}
  </fieldset>;
}
