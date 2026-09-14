'use client';
import { useEffect } from 'react';
import { installFeedback, syncNativeAppearance } from '@/lib/interaction-feedback';
import { CHAT_BACKGROUND_KEY, applyChatBackground } from '@/lib/chat-background';

export function useStartup(ready: boolean) {
  useEffect(() => {
    // Restore the user's background before pageReady, not only after opening settings.
    const restoreBackground = () => {
      try { applyChatBackground(localStorage.getItem(CHAT_BACKGROUND_KEY)); } catch { /* Retain the current appearance if storage is unavailable. */ }
    };
    const onStorage = (event: StorageEvent) => {
      try {
        if (event.storageArea === localStorage && (event.key === CHAT_BACKGROUND_KEY || event.key === null)) restoreBackground();
      } catch { /* Ignore cross-document events when storage access is denied. */ }
    };
    restoreBackground();
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);
  useEffect(() => {
    const pointer = () => { delete document.documentElement.dataset.keyboardFocus; };
    const keyboard = (event: KeyboardEvent) => { if (event.key === 'Tab') document.documentElement.dataset.keyboardFocus = 'true'; };
    const removeFeedback = installFeedback();
    syncNativeAppearance();
    document.addEventListener('pointerdown', pointer, true);
    document.addEventListener('keydown', keyboard, true);
    return () => {
      removeFeedback();
      document.removeEventListener('pointerdown', pointer, true);
      document.removeEventListener('keydown', keyboard, true);
    };
  }, []);
  useEffect(() => { if (ready) window.LukeAndroid?.pageReady?.(); }, [ready]);
}
