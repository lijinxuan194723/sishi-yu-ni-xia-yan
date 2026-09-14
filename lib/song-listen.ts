/** Only user-confirmed official song links, never model-invented audio URLs. */
export type SongTarget = { title: string; artist: string };
export type SongLinks = { qq?: string; netease?: string };
export const SONG_LINKS_KEY = 'luke-song-links-v1';
export function songIdentity(song: SongTarget): string {
  return JSON.stringify([song.title.trim().normalize('NFKC'), song.artist.trim().normalize('NFKC')]);
}
export function canonicalSongLink(value: string, provider: keyof SongLinks): string {
  if (!value.trim()) return '';
  if (value.length > 2000) throw Error('歌曲链接太长，请复制平台的单曲链接。');
  let u: URL;
  try { u = new URL(value.trim()); } catch { throw Error('请粘贴完整的 https 单曲链接。'); }
  if (u.protocol !== 'https:' || u.username || u.password || (u.port && u.port !== '443')) throw Error('只支持官方 https 单曲链接。');
  if (provider === 'qq') {
    if (!['y.qq.com', 'c.y.qq.com', 'i.y.qq.com'].includes(u.hostname)) throw Error('QQ 音乐链接必须来自 y.qq.com 或 c.y.qq.com。');
    const path = u.pathname.match(/\/(?:songDetail\/|song\/)([A-Za-z0-9]+)(?:\.html)?$/);
    const id = path?.[1] || u.searchParams.get('songmid') || u.searchParams.get('mid') || u.searchParams.get('songid') || '';
    if (!/^(?:[A-Za-z0-9]{14}|[0-9]{1,20})$/.test(id)) throw Error('未识别到 QQ 单曲编号；请打开单曲详情后复制链接，不要使用短链接或歌单链接。');
    return `https://y.qq.com/n/ryqq/songDetail/${id}`;
  }
  if (!['music.163.com', 'y.music.163.com'].includes(u.hostname)) throw Error('网易云链接必须来自 music.163.com。');
  let path = u.pathname, query = u.searchParams;
  if (u.hash.startsWith('#/')) { const h = new URL(u.hash.slice(1), 'https://music.163.com'); path = h.pathname; query = h.searchParams; }
  const id = query.get('id') || '';
  if (!/(?:^|\/)song\/?$/.test(path) || !/^[0-9]{1,20}$/.test(id)) throw Error('未识别到网易云单曲编号；请复制单曲链接，不要使用歌单链接。');
  return `https://music.163.com/song?id=${id}`;
}
export function songWebSearch(song: SongTarget, provider: keyof SongLinks = 'qq'): string {
  const q = encodeURIComponent(`${song.title.trim()} ${song.artist.trim()}`);
  return provider === 'qq' ? `https://y.qq.com/n/ryqq/search?w=${q}&t=song` : `https://music.163.com/#/search/m/?s=${q}&type=1`;
}
export function readSongLinks(storage: Pick<Storage, 'getItem'>, song: SongTarget): SongLinks {
  const raw = storage.getItem(SONG_LINKS_KEY);
  if (!raw) return {};
  const entries: unknown = JSON.parse(raw);
  if (!Array.isArray(entries)) throw Error('歌曲链接存档格式异常，原数据没有改动。');
  const found = entries.find(e => e && e.identity === songIdentity(song));
  if (!found) return {};
  return { qq: canonicalSongLink(String(found.qq || ''), 'qq'), netease: canonicalSongLink(String(found.netease || ''), 'netease') };
}
export function writeSongLinks(storage: Pick<Storage, 'getItem' | 'setItem'>, song: SongTarget, links: SongLinks): SongLinks {
  const qq = canonicalSongLink(links.qq || '', 'qq'), netease = canonicalSongLink(links.netease || '', 'netease');
  const raw = storage.getItem(SONG_LINKS_KEY), entries: unknown = raw ? JSON.parse(raw) : [];
  if (!Array.isArray(entries)) throw Error('歌曲链接存档格式异常，原数据没有改动。');
  const key = songIdentity(song), next = entries.filter(e => e?.identity !== key);
  if (qq || netease) next.push({ identity: key, qq, netease });
  // Never silently delete somebody's earlier links to make room.
  if (next.length > 500) throw Error('已保存 500 首歌曲链接，请先移除不用的绑定。');
  storage.setItem(SONG_LINKS_KEY, JSON.stringify(next));
  return { qq, netease };
}
