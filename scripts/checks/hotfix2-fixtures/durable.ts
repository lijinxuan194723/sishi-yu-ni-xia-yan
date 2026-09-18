import type {Data} from '../../../lib/companion';
export type LoadedData={data:Data;revision:number;payload:string;backend:'indexeddb'|'legacy'};
declare global {interface Window {__restoreFixture:{failLoad:boolean;failRestore:boolean;hold:boolean;release?:()=>void;writes:number;restores:number}}}
const loaded=(data:Data,revision=1):LoadedData=>({data,revision,payload:JSON.stringify(data),backend:'indexeddb'});
export async function loadDurable(initial:Data){if(window.__restoreFixture.failLoad)throw Error('合成的读取失败');return loaded(initial);}
export async function saveDurable(data:Data,state:LoadedData){window.__restoreFixture.writes++;return loaded(data,state.revision+1);}
export async function replaceDurable(data:Data){window.__restoreFixture.restores++;if(window.__restoreFixture.hold)await new Promise<void>(resolve=>window.__restoreFixture.release=resolve);if(window.__restoreFixture.failRestore)throw Error('合成的恢复失败');return loaded(data,9);}
export async function requestDurability(){}
