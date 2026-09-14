'use client';
import { useEffect, useRef, useState } from 'react';
import { Headphones, Link2, Copy } from 'lucide-react';
import { actionsBridge, nativeAction } from '@/lib/native-actions';
import { readSongLinks, writeSongLinks, songWebSearch, songIdentity, type SongLinks, type SongTarget } from '@/lib/song-listen';

export function SongListen({ song }: { song: SongTarget }) { return <SongListenSession key={songIdentity(song)} song={song}/>; }
function SongListenSession({ song }: { song: SongTarget }) {
 const [links, setLinks] = useState<SongLinks>({}), [qq, setQQ] = useState(''), [netease, setNetease] = useState('');
 const [notice, setNotice] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false);
 const alive = useRef(true), opening = useRef(false);
 useEffect(() => { alive.current = true; try { const saved = readSongLinks(localStorage, song); setLinks(saved); setQQ(saved.qq || ''); setNetease(saved.netease || ''); } catch { setError('无法读取歌曲链接，原存档未改动。'); } return () => { alive.current = false; }; }, []);
 function saveLinks() { try { const saved = writeSongLinks(localStorage, song, { qq, netease }); setLinks(saved); setQQ(saved.qq || ''); setNetease(saved.netease || ''); setError(''); setNotice(saved.qq || saved.netease ? '已保存这首歌的单曲链接。请确认链接对应你想听的版本。' : '已移除本曲绑定，恢复按歌名查找。'); } catch (e) { setError(e instanceof Error ? e.message : '未能保存链接，原数据未改动。'); } }
 async function listen() {
  if (opening.current) return;
  const native = actionsBridge();
  if (!native?.openSong) { window.open(links.qq || songWebSearch(song), '_blank', 'noopener,noreferrer'); setNotice('已请求打开 QQ 音乐网页；网页不能检测手机安装了哪些音乐应用。'); return; }
  opening.current = true; setBusy(true); setError('');
  try { const result = await nativeAction(id => native.openSong!(id, song.title, song.artist, links.qq || '', links.netease || '')); if (alive.current) { setNotice(result.message); if (!result.ok) setError(result.message); } }
  catch (e) { if (alive.current) setError(e instanceof Error ? e.message : '暂时无法打开音乐应用。'); }
  finally { opening.current = false; if (alive.current) setBusy(false); }
 }
 async function copy() { try { await navigator.clipboard.writeText(`${song.title} ${song.artist}`); if (alive.current) setNotice('已复制歌名和歌手，可在音乐应用中粘贴查找。'); } catch { if (alive.current) setError('复制失败，请长按上方歌名复制。'); } }
 return <div className="music-listen">
  <div className="listen-actions"><button type="button" className="primary" data-haptic="confirm" disabled={busy} onClick={() => void listen()}><Headphones size={17}/>{busy ? '正在打开…' : '去听这首歌'}</button><button type="button" className="soft-button" onClick={() => void copy()}><Copy size={15}/>复制歌名</button></div>
  <p className="listen-note">优先 QQ 音乐，未安装或不支持时尝试网易云。已绑定链接则优先打开单曲；否则按歌名与歌手查找，不保证自动播放或匹配同一版本。</p>
  <details><summary><Link2 size={14}/> 绑定这首歌的直达链接</summary><div className="music-link-fields"><p className="listen-note">粘贴平台的单曲详情链接，核对歌名、歌手后保存。不使用模型猜测歌曲编号，也不绕过会员限制。</p><label>QQ 音乐单曲链接<input type="url" value={qq} maxLength={2000} onChange={e => setQQ(e.target.value)} placeholder="https://y.qq.com/…"/></label><label>网易云单曲链接<input type="url" value={netease} maxLength={2000} onChange={e => setNetease(e.target.value)} placeholder="https://music.163.com/song?id=…"/></label><button type="button" className="soft-button" onClick={saveLinks}>保存本曲链接</button></div></details>
  {notice && <p className="listen-note" role="status">{notice}</p>}{error && <p role="alert">{error}</p>}
 </div>;
}
