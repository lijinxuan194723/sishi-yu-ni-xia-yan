import type {Data} from './companion.ts';
export const LEGACY_CHAT='legacy';
export type Conversation={id:string;title:string;createdAt:string;draft:string;archived?:boolean};
export type Conversations={version:1;active:string;items:Conversation[]};
const idOK=(s:unknown):s is string=>typeof s==='string'&&/^[a-zA-Z0-9_-]{1,100}$/.test(s);
export function parseConversations(value:unknown):Conversations{
 const v=value as Conversations;if(!v||v.version!==1||!idOK(v.active)||!Array.isArray(v.items)||!v.items.length||v.items.length>2000)throw Error('对话目录格式不正确，原始记录未覆盖。');
 const ids=new Set<string>();const items=v.items.map(s=>{if(!s||!idOK(s.id)||ids.has(s.id)||typeof s.title!=='string'||!s.title.trim()||s.title.length>80||typeof s.draft!=='string'||s.draft.length>2000||typeof s.createdAt!=='string'||s.createdAt.length>40||!Number.isFinite(Date.parse(s.createdAt))||(s.archived!==undefined&&typeof s.archived!=='boolean'))throw Error('对话目录含无效或重复记录。');ids.add(s.id);return {...s};});
 if(!ids.has(v.active)||!ids.has(LEGACY_CHAT)||items.find(s=>s.id===v.active)?.archived)throw Error('当前对话不存在或已归档。');return {version:1,active:v.active,items};
}
export const messageConversation=(m:Data['messages'][number])=>m.conversationId??LEGACY_CHAT;
export const activeConversation=(data:Data)=>data.conversations?.active??LEGACY_CHAT;
export function conversationsOf(data:Data):Conversations{return data.conversations??{version:1,active:LEGACY_CHAT,items:[{id:LEGACY_CHAT,title:'最初的悄悄话',createdAt:data.messages.find(m=>m.at&&Number.isFinite(Date.parse(m.at)))?.at??'1970-01-01T00:00:00.000Z',draft:data.draft??''}]};}
/** Indices always refer to the original ledger: stars, archive fingerprints and source links remain stable. */
export function conversationRows(data:Data,id=activeConversation(data)){return data.messages.map((m,i)=>({m,i})).filter(({m})=>messageConversation(m)===id);}
export function switchConversation(data:Data,id:string):Partial<Data>{const state=conversationsOf(data),target=state.items.find(c=>c.id===id);if(!target)throw Error('找不到这段对话。');if(id===state.active)return {};return {conversations:{...state,active:id,items:state.items.map(s=>s.id===state.active?{...s,draft:data.draft??''}:s.id===id?{...s,archived:false}:s)},draft:target.draft};}
export function createConversation(data:Data,id=crypto.randomUUID(),createdAt=new Date().toISOString()):Partial<Data>{const state=conversationsOf(data);if(!idOK(id)||state.items.some(s=>s.id===id)||state.items.length>=2000)throw Error('无法新建对话，请先整理已有对话。');return {conversations:parseConversations({...state,active:id,items:[...state.items.map(s=>s.id===state.active?{...s,draft:data.draft??''}:s),{id,title:'新的悄悄话',draft:'',createdAt}]}),draft:''};}
export function renameConversation(data:Data,id:string,title:string):Partial<Data>{const name=title.trim(),s=conversationsOf(data);if(!name||name.length>80||!s.items.some(s=>s.id===id))throw Error('名称须为 1～80 个字。');return {conversations:{...s,items:s.items.map(c=>c.id===id?{...c,title:name}:c)}};}
export function archiveConversation(data:Data,id:string,archived:boolean):Partial<Data>{const s=conversationsOf(data);if(!s.items.some(c=>c.id===id))throw Error('找不到对话。');if(archived&&id===s.active)throw Error('请先切换到另一段对话再归档。');return {conversations:{...s,items:s.items.map(c=>c.id===id?{...c,archived}:c)}};}
export function titledConversation(data:Data,text:string):Conversations{const s=conversationsOf(data),c=s.items.find(c=>c.id===s.active);return c?.title==='新的悄悄话'&&!conversationRows(data).length?{...s,items:s.items.map(c=>c.id===s.active?{...c,title:text.replace(/\s+/g,' ').trim().slice(0,28)||'新的悄悄话'}:c)}:s;}
export function conversationMarkdown(data:Data,id:string){const s=conversationsOf(data).items.find(c=>c.id===id);if(!s)throw Error('找不到对话。');return `# ${s.title}\n\n`+conversationRows(data,id).map(({m})=>`### ${m.who==='me'?data.name:'夏彦'}${m.at?' · '+m.at:''}\n\n${m.text}`).join('\n\n');}

/** One ledger pass instead of sessions × messages. No index rewrites or history copies. */
export function conversationSummaries(data:Data){
 const summaries=new Map(conversationsOf(data).items.map(s=>[s.id,{...s,count:0,last:(s.id===activeConversation(data)?data.draft:s.draft)??'',at:s.createdAt}]));
 for(const m of data.messages){const row=summaries.get(messageConversation(m));if(!row)continue;row.count++;row.last=m.text;if(m.at)row.at=m.at;}
 return [...summaries.values()];
}
