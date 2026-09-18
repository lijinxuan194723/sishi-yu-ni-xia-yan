'use client';
import {motion} from 'motion/react';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from './ui/dialog';
export function PhotoDetail({photo,onClose}:{photo:{src:string;id:string;alt:string}|null;onClose:()=>void}){return <Dialog open={!!photo} onOpenChange={v=>{if(!v)onClose();}}><DialogContent className="photo-detail210"><DialogTitle className="sr-only">四季相册</DialogTitle><DialogDescription className="sr-only">查看照片，关闭后返回原位置。</DialogDescription>{photo&&<motion.img layoutId={photo.id} src={photo.src} alt={photo.alt} transition={{type:'spring',stiffness:290,damping:31}}/>}</DialogContent></Dialog>;}
