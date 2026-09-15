'use client';
import {useEffect,useState} from 'react';
import {applyDisplay,readDisplay,saveDisplay,DEFAULT_DISPLAY,type DisplayPreferences} from '@/lib/display-preferences';
/** One listener per mounted preferences owner, not one per message. */
export function useDisplayPreferences(){
 const [preferences,setPreferences]=useState<DisplayPreferences>(DEFAULT_DISPLAY),[error,setError]=useState('');
 useEffect(()=>{const sync=()=>{const value=readDisplay();setPreferences(value);applyDisplay(value);};sync();const storage=(e:StorageEvent)=>{if(e.key===null||e.key==='luke-display-v205')sync();};window.addEventListener('luke-display-change',sync);window.addEventListener('storage',storage);return()=>{window.removeEventListener('luke-display-change',sync);window.removeEventListener('storage',storage);};},[]);
 function update(patch:Partial<DisplayPreferences>){try{const value=saveDisplay({...readDisplay(),...patch});setPreferences(value);setError('');return true;}catch{setError('设置未保存，本地空间可能不足。原设置已保留。');return false;}}
 return {preferences,update,error};
}
