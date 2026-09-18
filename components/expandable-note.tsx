'use client';
import {useId,useRef,useState,type ReactNode} from 'react';
import {motion} from 'motion/react';
import {ChevronDown} from 'lucide-react';
export function ExpandableNote({title,label,children}:{title:ReactNode;label:string;children:ReactNode}){const id=useId(),[open,setOpen]=useState(false),button=useRef<HTMLButtonElement>(null),body=useRef<HTMLDivElement>(null);return <div className="expandable210" data-open={open}><button ref={button} type="button" aria-expanded={open} aria-controls={id} aria-label={label} onClick={()=>{if(open&&body.current?.contains(document.activeElement))button.current?.focus({preventScroll:true});setOpen(v=>!v);}}><span>{title}</span><ChevronDown size={16}/></button><motion.div ref={body} id={id} initial={false} animate={{height:open?'auto':0,opacity:open?1:0}} transition={{duration:.2,ease:[.23,1,.32,1]}} inert={!open} aria-hidden={!open} className="expandable-body210"><div>{children}</div></motion.div></div>;}
