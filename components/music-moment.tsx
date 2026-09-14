'use client';
import {SongListen} from './song-listen';
import {Music2,RefreshCw} from 'lucide-react';
import {dateKey} from '@/lib/companion';
import type {DailyState} from '@/components/daily-picks';
import {RecommendationComment} from '@/components/recommendation-comment';

export function MusicMoment({daily,onChat}:{daily?:DailyState;onChat?:(text:string)=>void}){
 const track=daily?.picks?.songs[0];
 return <section className="music-moment" aria-label="夏彦的随身歌单">
  <div className="section-label"><Music2 size={18}/> 夏彦的随身歌单 <small>{daily?.picks?(daily.picks.date===dateKey(new Date())?'今日分享':`${daily.picks.date} 的分享`):'等待推荐'}</small></div>
  {track&&<div className="song-detail" aria-live="polite"><strong>{track.title}</strong><small>{track.artist}</small></div>}
  <RecommendationComment item={track?{kind:'song',title:track.title,creator:track.artist,thought:track.thought,date:daily!.picks!.date}:undefined}/>
  <p role="status">{daily?.loading?'夏彦正在挑选今天想分享的歌…':daily?.error||(!track?'联网后，让夏彦为你挑一首歌。':'更新于 '+new Date(daily!.picks!.updatedAt).toLocaleString('zh-CN'))}</p>
  {track&&<SongListen song={track}/>}
  <div className="moment-footer"><button className="soft-button" disabled={!daily||daily.loading} onClick={daily?.refresh}><RefreshCw size={16}/> {track?'再推荐一次':'获取今日推荐'}</button>{track&&<button className="primary" onClick={()=>onChat?.(`想和你聊聊这首歌《${track.title}》，歌手是${track.artist}。你刚才给它的评价是：${track.thought}`)}>聊聊这首歌</button>}</div>
 </section>;
}
