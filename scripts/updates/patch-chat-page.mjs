/** Small, hash-guarded integration patch. Refuses to overwrite a different app/page.tsx. */
import fs from 'node:fs';
import crypto from 'node:crypto';
const file='app/page.tsx';
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
const text=fs.readFileSync(file,'utf8');
const pairs=[
 ["import {ChatAvatar,ChatAppearance}","import {ChatAvatar,ChatBubble,ChatAppearance}"],
 ["const avatar=<img className=\"avatar\" src={'/images/companions/'+display.lukeAvatar+'.webp'} alt=\"夏彦\"/>;","const avatar=<ChatAvatar mine={false} preferences={display} name={data.name}/>;"],
 ['<div className="bubble-frame" data-skin={display.bubble}><p>{m.text}</p></div>','<ChatBubble mine={m.who===\'me\'} preferences={display}><p>{m.text}</p></ChatBubble>'],
 ['<div className="bubble-frame" data-skin={display.bubble}>{liveReply?','<ChatBubble preferences={display}>{liveReply?'],
 ['aria-label="夏彦正在输入中"><i/><i/><i/></p>}</div></div>','aria-label="夏彦正在输入中"><i/><i/><i/></p>}</ChatBubble></div>'],
 ['className="composer"><ChatAttachmentPicker','className="composer composer-compat210"><ChatAttachmentPicker'],
];
if(pairs.every(([,after])=>text.includes(after))){console.log('Chat integration already applied.');process.exit(0);}
const expected='9540afaf6b5a8b7644ab1ac6cd934f639cbb3431597115014b15fe98b3f68938';
if(hash(text)!==expected)throw Error('app/page.tsx changed since inspection. Stopped without writing; inspect the new base first.');
let next=text;
for(const [before,after]of pairs){if(next.split(before).length!==2)throw Error('Expected exactly one integration anchor: '+before);next=next.replace(before,after);}
fs.writeFileSync(file+'.tmp',next);fs.renameSync(file+'.tmp',file);console.log('Integrated independent chat avatars/bubbles without changing data model or version.');
