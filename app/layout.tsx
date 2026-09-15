import type { Metadata } from 'next';
import './globals.css';
import './theme.css';
import './ambience.css';
import './notes-polish.css';
import './motion.css';
import './interaction-polish.css';
import './companion-polish.css';
import './detail-six.css';
import './settings-calendar.css';
import './refinement205.css';
import {ErrorBoundary} from '@/components/error-boundary';
export const metadata:Metadata={title:'四时与你 · 夏彦与你',description:'属于夏彦和你的日常：悄悄话、日历与时光手记。',manifest:'/manifest.webmanifest',appleWebApp:{capable:true,title:'四时与你',statusBarStyle:'default'}};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="zh-CN"><body><ErrorBoundary>{children}</ErrorBoundary></body></html>}
