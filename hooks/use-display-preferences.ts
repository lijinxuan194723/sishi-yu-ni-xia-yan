'use client';
import {useEffect,useState} from 'react';
import {applyDisplay,readDisplay,saveDisplay,DEFAULT_DISPLAY,DISPLAY_KEY,LEGACY_DISPLAY_KEY,type DisplayPreferences} from '@/lib/display-preferences';
/** Each owner observes both generations; message rows do not add their own listeners. */
export function useDisplayPreferences(){
 const [preferences,setPreferences]=useState<DisplayPreferences>(DEFAULT_DISPLAY),[error,setError]=useState('');
 useEffect(()=>{const sync=()=>{try{const value=readDisplay();setPreferences(value);applyDisplay(value);setError('');}catch(e){setError(e instanceof Error?e.message:'设置读取失败，原值未覆盖。');}};sync();const storage=(e:StorageEvent)=>{if(e.key===null||[DISPLAY_KEY,LEGACY_DISPLAY_KEY].includes(e.key))sync();};window.addEventListener('luke-display-change',sync);window.addEventListener('storage',storage);return()=>{window.removeEventListener('luke-display-change',sync);window.removeEventListener('storage',storage);};},[]);
 function update(patch:Partial<DisplayPreferences>){try{const next={...patch};if(patch.bubble!==undefined){if(patch.bubbleMine===undefined)next.bubbleMine=patch.bubble;if(patch.bubbleLuke===undefined)next.bubbleLuke=patch.bubble;}if(patch.avatar!==undefined&&patch.avatarMine===undefined)next.avatarMine=patch.avatar;const value=saveDisplay({...readDisplay(),...next});setPreferences(value);setError('');return true;}catch(e){setError(e instanceof Error?e.message:'设置未保存，原设置已保留。');return false;}}
 return {preferences,update,error};
}
