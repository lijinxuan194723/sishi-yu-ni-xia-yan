'use client';
import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ArrowDown, Copy, Search, Send, Square, X } from 'lucide-react';
import { Dialog, DialogContent, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { complete, type ModelConfig } from '@/lib/model';
import { topicContext, topicKey, TOPIC_STORAGE_KEY, type Topic } from '@/lib/topic-chat';
import {
  TopicSessionStore, TopicStorageConflict, TOPIC_DRAFT_DELAY, TOPIC_MESSAGE_LIMIT,
  TOPIC_PAGE_SIZE, prepareTopicMessages, searchTopicMessages, literalHighlights,
  type SavedTopicThread, type TopicMessage,
} from '@/lib/topic-chat-session';
import { useTopicViewport } from './use-topic-viewport';
import styles from './topic-chat.module.css';

type Props = { topic: Topic; config: ModelConfig; onClose: () => void };
type Request = { controller: AbortController; messages: TopicMessage[]; text: string; finished: boolean };

// A topic change creates a fresh session. Late callbacks can never write into another topic.
export function TopicChat(props: Props) {
  return <TopicChatSession key={topicKey(props.topic)} {...props} />;
}

function TopicChatSession({ topic, config, onClose }: Props) {
  const [sessionTopic] = useState(topic);
  const [open, setOpen] = useState(true);
  const [thread, setThread] = useState<SavedTopicThread>({ messages: [], draft: '' });
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState('');
  const [error, setError] = useState('');
  const [storageError, setStorageError] = useState('');
  const [conflict, setConflict] = useState(false);
  const [saveState, setSaveState] = useState<'saved' | 'pending' | 'error'>('saved');
  const [editing, setEditing] = useState<number | null>(null);
  const [query, setQuery] = useState('');
  const [historyOpen, setHistoryOpen] = useState(false);
  const [resultLimit, setResultLimit] = useState(TOPIC_PAGE_SIZE);
  const [visibleCount, setVisibleCount] = useState(TOPIC_PAGE_SIZE);
  const [atBottom, setAtBottom] = useState(true);
  const [notice, setNotice] = useState('');
  const current = useRef(thread);
  const store = useRef<TopicSessionStore | null>(null);
  const mounted = useRef(false);
  const dirty = useRef(false);
  const request = useRef<Request | null>(null);
  const draftTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const draftBeforeEdit = useRef<string | null>(null);
  const composing = useRef(false);
  const pane = useRef<HTMLDivElement>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);
  const following = useRef(true);
  const jumpTo = useRef<number | null>(null);
  const scrollAnchor = useRef<{ height: number; top: number } | null>(null);
  const helperId = useId();
  const viewportStyle = useTopicViewport();
  const matches = useMemo(() => searchTopicMessages(thread.messages, query), [thread.messages, query]);
  const startIndex = Math.max(0, thread.messages.length - visibleCount);

  function clearDraftTimer() {
    if (draftTimer.current !== null) clearTimeout(draftTimer.current);
    draftTimer.current = null;
  }

  function reportStorageError(cause: unknown) {
    if (!mounted.current) return;
    setConflict(cause instanceof TopicStorageConflict);
    setSaveState('error');
    setStorageError(cause instanceof TopicStorageConflict ? cause.message :
      '未能保存到本机。内容仍在当前窗口，请保留草稿、释放存储空间后重试。');
  }

  function flush() {
    clearDraftTimer();
    if (!dirty.current) return true;
    try {
      if (!store.current) throw new Error('会话尚未读取。');
      store.current.write(current.current);
      dirty.current = false;
      if (mounted.current) { setSaveState('saved'); setStorageError(''); setConflict(false); }
      return true;
    } catch (cause) { reportStorageError(cause); return false; }
  }

  function update(next: SavedTopicThread, immediate = false) {
    current.current = next;
    dirty.current = true;
    if (mounted.current) { setThread(next); setSaveState('pending'); }
    clearDraftTimer();
    if (immediate) return flush();
    draftTimer.current = setTimeout(flush, TOPIC_DRAFT_DELAY);
    return true;
  }

  // For a send, write first: a failed save must not erase the typed draft.
  function commitSend(next: SavedTopicThread) {
    clearDraftTimer();
    try {
      if (!store.current) throw new Error('会话尚未读取。');
      store.current.write(next);
      current.current = next;
      dirty.current = false;
      setThread(next); setSaveState('saved'); setStorageError(''); setConflict(false);
      return true;
    } catch (cause) { dirty.current = true; reportStorageError(cause); return false; }
  }

  function finish(active: Request, text: string) {
    if (active.finished || request.current !== active) return;
    active.finished = true;
    request.current = null;
    if (text.trim()) {
      update({ ...current.current, messages: [...active.messages, { role: 'assistant', content: text }] }, true);
    } else flush();
    if (mounted.current) { setBusy(false); setLive(''); }
  }

  function stopReply(announce = true) {
    const active = request.current;
    if (!active) return;
    active.controller.abort();
    finish(active, active.text);
    if (announce && mounted.current) {
      setNotice(active.text.trim() ? '已停止回复，收到的内容已保留。' : '已停止回复，可以重试。');
    }
  }

  function readSaved() {
    try {
      const source = new TopicSessionStore(localStorage, TOPIC_STORAGE_KEY, topicKey(sessionTopic));
      const saved = source.read();
      store.current = source; current.current = saved; dirty.current = false;
      setThread(saved); setError(''); setReady(true); setSaveState('saved'); setStorageError(''); setConflict(false);
      setEditing(null); draftBeforeEdit.current = null;
      following.current = true; setAtBottom(true);
    } catch (cause) {
      setReady(false); setSaveState('error');
      setStorageError(cause instanceof Error ? cause.message : '会话无法读取，原数据已保留。');
    }
  }

  useEffect(() => {
    mounted.current = true;
    readSaved();
    const onPageHide = () => { stopReply(false); flush(); };
    const onVisibility = () => { if (document.visibilityState === 'hidden') flush(); };
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      stopReply(false);
      if (!flush()) { event.preventDefault(); event.returnValue = ''; }
    };
    window.addEventListener('pagehide', onPageHide);
    window.addEventListener('beforeunload', onBeforeUnload);
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      mounted.current = false;
      stopReply(false); flush(); clearDraftTimer();
      window.removeEventListener('pagehide', onPageHide);
      window.removeEventListener('beforeunload', onBeforeUnload);
      document.removeEventListener('visibilitychange', onVisibility);
    };
    // This component is keyed by the immutable topic identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const element = textarea.current;
    if (!element) return;
    element.style.setProperty('--topic-input-height', '56px');
    element.style.setProperty('--topic-input-height', `${Math.min(136, Math.max(56, element.scrollHeight + 2))}px`);
  }, [thread.draft, ready, viewportStyle]);

  useEffect(() => {
    const element = pane.current;
    if (!element) return;
    if (jumpTo.current !== null) {
      const index = jumpTo.current;
      const target = element.querySelector<HTMLElement>(`[data-message-index="${index}"]`);
      if (target) {
        element.scrollTop += target.getBoundingClientRect().top - element.getBoundingClientRect().top - 12;
        target.focus({ preventScroll: true }); jumpTo.current = null;
      }
    } else if (scrollAnchor.current) {
      element.scrollTop = scrollAnchor.current.top + element.scrollHeight - scrollAnchor.current.height;
      scrollAnchor.current = null;
    } else if (following.current) element.scrollTop = element.scrollHeight;
  }, [thread.messages, live, visibleCount, historyOpen, viewportStyle, thread.draft]);

  async function send(retry = false) {
    if (!ready || request.current || composing.current) return;
    if (!config.baseUrl.trim() || !config.model.trim() || !config.key.trim()) {
      setError('请先在设置中配置聊天模型、API 地址和密钥。'); return;
    }
    const editIndex = retry ? null : editing;
    if (editIndex !== null && current.current.messages.length > editIndex + 1 &&
        !window.confirm('重新发送会替换这条消息及其后续对话。取消修改可保留原记录，确定继续吗？')) return;
    let messages: TopicMessage[];
    try { messages = prepareTopicMessages(current.current, editIndex, retry); }
    catch (cause) { setError(cause instanceof Error ? cause.message : '消息无法发送。'); return; }
    const nextDraft = retry ? current.current.draft : editIndex !== null ? draftBeforeEdit.current ?? '' : '';
    if (!commitSend({ messages, draft: nextDraft })) return;
    if (editIndex !== null) { setEditing(null); draftBeforeEdit.current = null; }
    setError(''); setNotice(''); setBusy(true); setLive('');
    following.current = true; setAtBottom(true);
    const active: Request = { controller: new AbortController(), messages, text: '', finished: false };
    request.current = active;
    try {
      const reply = await complete(config, topicContext(sessionTopic, messages), active.controller.signal, 1500, text => {
        if (active.finished || request.current !== active || !mounted.current) return;
        active.text = text; setLive(text);
      });
      if (active.finished || request.current !== active || !mounted.current) return;
      finish(active, reply);
      setNotice('夏彦的回复已收到。');
    } catch (cause) {
      if (active.finished || request.current !== active || !mounted.current) return;
      finish(active, active.text);
      if (active.controller.signal.aborted) setNotice('已停止回复，收到的内容已保留。');
      else setError(cause instanceof Error ? cause.message : '回复失败，请检查网络后重试。');
    }
  }

  function close() {
    stopReply(false);
    if (flush()) setOpen(false);
    else setNotice('尚有内容未保存。请先保留草稿或重试保存，再关闭窗口。');
  }

  function edit(index: number) {
    if (request.current) return;
    if (editing === null) draftBeforeEdit.current = current.current.draft;
    setEditing(index); setError('');
    update({ ...current.current, draft: current.current.messages[index].content });
    textarea.current?.focus();
  }

  function cancelEdit() {
    setEditing(null);
    update({ ...current.current, draft: draftBeforeEdit.current ?? '' }, true);
    draftBeforeEdit.current = null;
    setNotice('已取消修改，原来的草稿已恢复。');
  }

  async function copy(text: string) {
    try {
      if (!navigator.clipboard) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(text);
      if (mounted.current) setNotice('已复制。');
    } catch { if (mounted.current) setNotice('复制未成功，请长按或选中文字后复制。'); }
  }

  function showLatest() {
    following.current = true; setAtBottom(true);
    pane.current?.scrollTo({ top: pane.current.scrollHeight, behavior: 'auto' });
  }

  function locate(index: number) {
    following.current = false; setAtBottom(false); jumpTo.current = index;
    setVisibleCount(count => Math.max(count, current.current.messages.length - index));
    setHistoryOpen(false);
  }

  return (
    <Dialog open={open} onOpenChange={next => { if (!next) close(); }} onOpenChangeComplete={next => { if (!next) onClose(); }}>
      <DialogContent className={`topic-chat ${styles.panel}`} style={viewportStyle}>
        <header className={styles.header}>
          <DialogTitle title={sessionTopic.title}>{sessionTopic.title}</DialogTitle>
          <DialogDescription>慢慢聊，话题记录与悄悄话分开保存。</DialogDescription>
        </header>
        <div className={styles.contextArea}>
        <details className={`topic-background ${styles.background}`}>
          <summary>查看话题背景</summary><p>{sessionTopic.context}</p>
        </details>
        <details className={styles.history} open={historyOpen}
          onToggle={event => setHistoryOpen(event.currentTarget.open)}>
          <summary>会话记录 <span>{thread.messages.length} 条</span></summary>
          <div className={styles.searchBox}>
            <Search size={17} aria-hidden="true" />
            <input aria-label="搜索此话题的会话记录" type="search" maxLength={200} placeholder="搜索关键词…"
              value={query} onChange={event => { setQuery(event.target.value); setResultLimit(TOPIC_PAGE_SIZE); }} />
            {query && <button type="button" aria-label="清空搜索" onClick={() => setQuery('')}><X size={16} /></button>}
          </div>
          <p className={styles.resultCount} role="status">{query.trim() ? `找到 ${matches.length} 条相关记录` : '选择一条记录，回到当时的对话。'}</p>
          <div className={styles.results}>
            {!matches.length && <p className={styles.empty}>{query.trim() ? '没有找到这句话，换个关键词试试。' : '还没有记录，第一句话就从现在开始。'}</p>}
            {matches.slice(0, resultLimit).map(index => <button type="button" key={index}
              className={styles.result} onClick={() => locate(index)}>
              <small>{thread.messages[index].role === 'user' ? '你' : '夏彦'} · 第 {index + 1} 条</small>
              <span>{literalHighlights(thread.messages[index].content, query).map((part, i) => part.match ?
                <mark key={i}>{part.text}</mark> : <span key={i}>{part.text}</span>)}</span>
            </button>)}
            {matches.length > resultLimit && <button type="button" className={styles.quietButton}
              onClick={() => setResultLimit(count => count + TOPIC_PAGE_SIZE)}>继续查看记录</button>}
          </div>
        </details>
        </div>
        <div className={`topic-messages ${styles.messages}`} role="log" aria-label="此话题的聊天消息"
          aria-live="polite" aria-relevant="additions" ref={pane} onScroll={event => {
            const element = event.currentTarget;
            const bottom = element.scrollHeight - element.scrollTop - element.clientHeight < 70;
            following.current = bottom; setAtBottom(bottom);
          }}>
          {startIndex > 0 && <button type="button" className={styles.quietButton} onClick={() => {
            if (pane.current) scrollAnchor.current = { height: pane.current.scrollHeight, top: pane.current.scrollTop };
            following.current = false; setVisibleCount(count => count + TOPIC_PAGE_SIZE);
          }}>查看更早的 {Math.min(startIndex, TOPIC_PAGE_SIZE)} 条消息</button>}
          {!thread.messages.length && <div className={styles.empty}><p>{ready ? '想从哪一句开始？' : storageError ? '暂时无法读取记录' : '正在读取本地记录…'}</p><small>聊一段旋律、一页书，或此刻的心情。</small></div>}
          {thread.messages.slice(startIndex).map((message, offset) => {
            const index = startIndex + offset;
            return <article key={index} data-message-index={index} tabIndex={-1}
              className={`message ${message.role === 'user' ? 'mine' : ''} ${styles.message}`}>
              <div className={styles.messageMeta}>
                <small>{message.role === 'user' ? '你' : '夏彦'}</small>
                <div className={styles.messageTools}>
                  <button type="button" aria-label={`复制第 ${index + 1} 条消息`} onClick={() => void copy(message.content)}><Copy size={14} /></button>
                  {message.role === 'user' && <button type="button" disabled={busy || !ready} onClick={() => edit(index)}>修改</button>}
                </div>
              </div><p>{message.content}</p>
            </article>;
          })}
          {busy && <div className={`message ${styles.message}`} aria-live="off"><small>夏彦</small><p>{live || '正在想怎么回应你…'}</p></div>}
        </div>
        <div className={styles.feedback}>
        {!atBottom && <button type="button" className={styles.latest} onClick={showLatest}><ArrowDown size={15} />回到最新消息</button>}
        {editing !== null && <div className={styles.editStatus} role="status"><span>正在修改第 {editing + 1} 条消息。重新发送将替换后续对话。</span><button type="button" onClick={cancelEdit}>取消修改</button></div>}
        {storageError && <div className={styles.alert} role="alert"><p>{storageError}</p><div>
          {ready && !conflict && <button type="button" onClick={() => flush()}>重试保存</button>}
          {(!ready || conflict) && <button type="button" disabled={busy} onClick={() => {
            if (dirty.current && !window.confirm('重新读取会放弃当前未保存的内容。请先复制草稿，确定继续吗？')) return;
            clearDraftTimer(); readSaved();
          }}>重新读取</button>}
          {thread.draft && <button type="button" onClick={() => void copy(thread.draft)}>复制草稿</button>}
        </div></div>}
        {error && <div className={styles.alert} role="alert"><p>{error}</p><button type="button" onClick={() => setError('')}>收起提示</button></div>}
        {!busy && ready && editing === null && thread.messages.at(-1)?.role === 'user' && <button type="button"
          className={styles.quietButton} onClick={() => void send(true)}>重试上一条回复</button>}
        </div>
        <form className={`topic-composer ${styles.composer}`} onSubmit={event => { event.preventDefault(); void send(); }}>
          <textarea ref={textarea} aria-label="独立对话消息" aria-describedby={helperId} rows={2}
            maxLength={TOPIC_MESSAGE_LIMIT} disabled={!ready} placeholder={editing !== null ? '修改后发送…' : busy ? '也可以先写下想说的下一句…' : '想和你聊聊…'}
            value={thread.draft} onCompositionStart={() => { composing.current = true; }}
            onCompositionEnd={() => { composing.current = false; }}
            onKeyDown={event => {
              if (event.key === 'Enter' && (event.ctrlKey || event.metaKey) &&
                  !event.nativeEvent.isComposing && event.nativeEvent.keyCode !== 229 && !composing.current) {
                event.preventDefault(); void send();
              }
            }} onChange={event => { setNotice(''); update({ ...current.current, draft: event.target.value }); }} />
          {busy ? <button type="button" className="primary" aria-label="停止独立回复" title="停止回复" onClick={() => stopReply()}><Square size={19} /></button> :
            <button type="submit" className="primary" aria-label={editing !== null ? '发送修改后的消息' : '发送独立消息'} title="发送（Ctrl / ⌘ + Enter）"
              disabled={!ready || !thread.draft.trim() || thread.draft.length > TOPIC_MESSAGE_LIMIT}><Send size={20} /></button>}
        </form>
        <div className={styles.composerHint} id={helperId}>
          <span role="status">{saveState === 'pending' ? '正在保存…' : saveState === 'error' ? '尚未保存' : notice || '已保存在本机'}</span>
          <span><span className={styles.shortcut}>Ctrl / ⌘ + Enter 发送 · </span>{thread.draft.length} / {TOPIC_MESSAGE_LIMIT}</span>
        </div>
        <span role="status" className={styles.srOnly}>{busy ? '夏彦正在回复。' : ''}</span>
      </DialogContent>
    </Dialog>
  );
}
