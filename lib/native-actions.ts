export type ActionResult = { id: string; ok: boolean; message: string; route?: string };
type ActionsBridge = {
 openSong?: (id: string, title: string, artist: string, qq: string, netease: string) => void;
 clockAction?: (id: string, mode: string, hour: number, minute: number, seconds: number, label: string) => void;
 feedbackStyle?: (style: string) => void;
 feedbackStatus?: () => string;
};
export function actionsBridge(): ActionsBridge | undefined { return typeof window === 'undefined' ? undefined : (window as unknown as { LukeAndroid?: ActionsBridge }).LukeAndroid; }
export function nativeAction(send: (id: string) => void): Promise<ActionResult> {
 const id = crypto.randomUUID();
 return new Promise((resolve, reject) => {
  const done = (event: Event) => {
   const result = (event as CustomEvent<ActionResult>).detail;
   if (result?.id !== id) return;
   cleanup(); resolve(result);
  };
  const timer = setTimeout(() => { cleanup(); reject(Error('没有收到系统确认。请先查看目标应用，避免重复创建提醒。')); }, 15000);
  function cleanup() { clearTimeout(timer); window.removeEventListener('luke-action-result', done); }
  window.addEventListener('luke-action-result', done);
  try { send(id); } catch { cleanup(); reject(Error('当前安装版本不支持这个操作，请更新后重试。')); }
 });
}
