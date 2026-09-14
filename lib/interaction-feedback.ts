/** Local, optional touch feedback. No timers, network requests or vibration loops. */
export const FEEDBACK_KEY = 'luke-touch-feedback-v1';
export type FeedbackMode = 'gentle' | 'clear' | 'off';
export type FeedbackKind = 'press' | 'selection' | 'confirm';
type NativeUI = { haptic?: () => void; hapticEvent?: (kind: FeedbackKind) => void; feedbackEnabled?: (enabled: boolean) => void; feedbackStyle?: (mode: string) => void; startupAppearance?: (season: string, period: string) => void };
const nativeUI = (): NativeUI | undefined => (window as unknown as { LukeAndroid?: NativeUI }).LukeAndroid;
export function feedbackMode(value: string | null): FeedbackMode { return value === 'off' ? 'off' : value === 'clear' ? 'clear' : 'gentle'; }
export function feedbackKind(element: Element): FeedbackKind | null {
  if (element.closest('[inert],[aria-disabled="true"],[data-haptic="off"],[data-ending-style],[data-motion-presence="exiting"]') || element.matches(':disabled')) return null;
  if (element.matches('[role="tab"][aria-selected="true"],[role="option"][aria-selected="true"]')) return null;
  const explicit = element.getAttribute('data-haptic');
  if (explicit === 'confirm') return 'confirm';
  if (explicit === 'selection' || element.matches('[role="tab"],[role="option"],[role="switch"],[aria-pressed],input[type="checkbox"],input[type="radio"],summary')) return 'selection';
  return 'press';
}
export function createFeedbackGate(cooldown = 90) {
  let last = -Infinity;
  return (time: number, trusted: boolean, visible: boolean, enabled: boolean) => {
    if (!trusted || !visible || !enabled || !Number.isFinite(time) || time - last < cooldown) return false;
    last = time; return true;
  };
}
export function syncNativeAppearance() {
  if (typeof window === 'undefined') return;
  try {
    const saved = JSON.parse(localStorage.getItem('luke-appearance-v1') || '{}');
    nativeUI()?.startupAppearance?.(typeof saved?.season === 'string' ? saved.season : 'auto', typeof saved?.period === 'string' ? saved.period : 'auto');
  } catch { /* A bad preference must never prevent launching the app. */ }
}
export function installFeedback() {
  let mode: FeedbackMode = 'gentle';
  const sync = () => {
    try { mode = feedbackMode(localStorage.getItem(FEEDBACK_KEY)); } catch { mode = 'off'; }
    try { nativeUI()?.feedbackEnabled?.(mode !== 'off'); nativeUI()?.feedbackStyle?.(mode); } catch { /* Older bridges stay usable. */ }
  };
  sync();
  const accept = createFeedbackGate();
  const tap = (event: MouseEvent) => {
    const origin = event.target;
    if (!(origin instanceof Element)) return;
    const control = origin.closest('button,a[href],[role="button"],[role="tab"],[role="option"],[role="switch"],input[type="checkbox"],input[type="radio"],summary');
    if (!control) return;
    const kind = feedbackKind(control);
    if (!kind || !accept(performance.now(), event.isTrusted, !document.hidden, mode !== 'off')) return;
    try {
      const bridge = nativeUI();
      if (bridge?.hapticEvent) bridge.hapticEvent(kind);
      else bridge?.haptic?.();
    } catch { /* Unsupported haptics must not consume the user's click. */ }
  };
  const storage = (event: StorageEvent) => { if (event.key === FEEDBACK_KEY || event.key === null) sync(); };
  document.addEventListener('click', tap, true);
  window.addEventListener('luke-feedback-change', sync);
  window.addEventListener('storage', storage);
  return () => {
    document.removeEventListener('click', tap, true);
    window.removeEventListener('luke-feedback-change', sync);
    window.removeEventListener('storage', storage);
  };
}
