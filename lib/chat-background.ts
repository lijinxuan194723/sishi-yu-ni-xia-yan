/** Backgrounds remain local; validate and resize before touching the saved image. */
export const CHAT_BACKGROUND_KEY = 'luke-chat-background-v1';
export const MAX_BACKGROUND_BYTES = 8 * 1024 * 1024;
export const MAX_BACKGROUND_EDGE = 1440;
export const MAX_BACKGROUND_PIXELS = 36_000_000;
export const MAX_BACKGROUND_DATA_LENGTH = 1_500_000;
const allowedTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);

export function validateBackgroundFile(file: { size: number; type: string }): void {
  if (!allowedTypes.has(file.type.toLowerCase())) throw new Error('请选择 JPG、PNG 或 WebP 图片。');
  if (!Number.isFinite(file.size) || file.size <= 0) throw new Error('图片是空文件，请重新选择。');
  if (file.size > MAX_BACKGROUND_BYTES) throw new Error('图片不能超过 8 MB，请先缩小后再选择。');
}

export function backgroundDimensions(width: number, height: number) {
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1) {
    throw new Error('无法读取图片尺寸，请换一张图片。');
  }
  if (width * height > MAX_BACKGROUND_PIXELS) throw new Error('图片分辨率过大，请先缩小后再选择。');
  const scale = Math.min(1, MAX_BACKGROUND_EDGE / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

// Existing GIF backgrounds remain readable; new uploads use static supported formats.
export function isStoredBackground(value: string): boolean {
  return value.length <= 12 * 1024 * 1024 &&
    /^data:image\/(?:png|jpeg|webp|gif);base64,[a-z\d+/=]+$/i.test(value);
}

export function saveChatBackground(storage: Pick<Storage, 'setItem' | 'removeItem'>, value: string | null) {
  if (value === null) storage.removeItem(CHAT_BACKGROUND_KEY);
  else {
    if (!isStoredBackground(value)) throw new Error('图片格式无效，原背景已保留。');
    storage.setItem(CHAT_BACKGROUND_KEY, value);
  }
}

export function prepareChatBackground(file: File, signal?: AbortSignal): Promise<string> {
  try { validateBackgroundFile(file); } catch (error) { return Promise.reject(error); }
  if (signal?.aborted) return Promise.reject(new DOMException('已取消选择', 'AbortError'));
  return new Promise((resolve, reject) => {
    const image = new Image();
    const url = URL.createObjectURL(file);
    let settled = false;
    let timeout: ReturnType<typeof setTimeout> | undefined;
    const cleanup = () => {
      if (timeout !== undefined) clearTimeout(timeout);
      image.onload = null; image.onerror = null;
      signal?.removeEventListener('abort', abort);
      URL.revokeObjectURL(url);
      image.src = '';
    };
    const fail = (error: unknown) => {
      if (settled) return;
      settled = true; cleanup(); reject(error);
    };
    const abort = () => fail(new DOMException('已取消选择', 'AbortError'));
    signal?.addEventListener('abort', abort, { once: true });
    timeout = setTimeout(() => fail(new Error('图片处理超时，请换一张更小的图片。')), 15000);
    image.onerror = () => fail(new Error('这张图片无法解码，请换一张 JPG、PNG 或 WebP 图片。'));
    image.onload = () => {
      if (settled || signal?.aborted) { abort(); return; }
      let canvas: HTMLCanvasElement | undefined;
      try {
        const size = backgroundDimensions(image.naturalWidth, image.naturalHeight);
        canvas = document.createElement('canvas'); canvas.width = size.width; canvas.height = size.height;
        const context = canvas.getContext('2d');
        if (!context) throw new Error('当前设备暂时无法处理图片。');
        context.drawImage(image, 0, 0, size.width, size.height);
        let result = '';
        for (const quality of [0.84, 0.72, 0.6]) {
          result = canvas.toDataURL('image/webp', quality);
          if (!result.startsWith('data:image/webp;')) {
            // Older engines fall back to JPEG, with an explicit light alpha backdrop.
            context.globalCompositeOperation = 'destination-over';
            context.fillStyle = '#fffaf2'; context.fillRect(0, 0, size.width, size.height);
            context.globalCompositeOperation = 'source-over';
            result = canvas.toDataURL('image/jpeg', quality);
          }
          if (result.length <= MAX_BACKGROUND_DATA_LENGTH) break;
        }
        if (!isStoredBackground(result) || result.length > MAX_BACKGROUND_DATA_LENGTH) {
          throw new Error('压缩后的图片仍然较大，请选择尺寸更小的图片。');
        }
        settled = true; cleanup(); resolve(result);
      } catch (error) { fail(error); }
      finally { if (canvas) { canvas.width = 1; canvas.height = 1; } }
    };
    image.src = url;
  });
}

/** The explicit flag makes custom images win over the seasonal stylesheet. */
export function applyChatBackground(value: string | null): void {
  const root = document.documentElement;
  if (value && isStoredBackground(value)) {
    root.style.setProperty('--chat-user-background', `url("${value}")`);
    root.dataset.chatBackground = 'custom';
  } else {
    root.style.removeProperty('--chat-user-background');
    delete root.dataset.chatBackground;
  }
}
