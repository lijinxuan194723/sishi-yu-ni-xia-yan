// Test-only storage adapter; the real useLocalData hook is not rewritten or mocked.
import React from 'react';
import {createRoot} from 'react-dom/client';
import {useLocalData} from '../../../lib/use-local-data';
import type {Data} from '../../../lib/companion';
const initial:Data={name:'原称呼',since:'2023-07-08',messages:[],tasks:[],notes:[],checks:[]};
declare global {interface Window {__restoreHarness:ReturnType<typeof useLocalData>}}
function Harness(){const state=useLocalData(initial);window.__restoreHarness=state;return <><div role="status">{state.ready?'可编辑':'只读'} · {state.data.name}</div><input aria-label="草稿" disabled={!state.ready} value={state.data.draft??''} onChange={e=>state.save({draft:e.target.value})}/><p>{state.error}</p></>;}
createRoot(document.getElementById('root')!).render(<Harness/>);
