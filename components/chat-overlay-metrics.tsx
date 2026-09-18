'use client';
import {useLayoutEffect,useRef} from 'react';
export function ChatOverlayMetrics(){const marker=useRef<HTMLSpanElement>(null);useLayoutEffect(()=>{const chat=marker.current?.closest<HTMLElement>('.chat'),bottom=chat?.querySelector<HTMLElement>('.chat-bottom210');if(!chat||!bottom)return;const measure=()=>{const h=bottom.getBoundingClientRect().height;if(h>0)chat.style.setProperty('--chat-bottom210',`${h+12}px`);};const observer=new ResizeObserver(measure);observer.observe(bottom);measure();return()=>observer.disconnect();},[]);return <span hidden ref={marker}/>;}
