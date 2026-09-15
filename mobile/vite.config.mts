import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
// Reuse sharp 0.34.5 already present in the committed build dependency graph.
import sharpModule from 'sharp';
const sharp=sharpModule as unknown as (input:Buffer)=>any;
import tailwind from '@tailwindcss/postcss';
import {fileURLToPath} from 'node:url';
import {readdir,readFile,unlink,writeFile} from 'node:fs/promises';
import {join} from 'node:path';
async function compactAssets(dir:string,root=dir){
 const files=await readdir(dir,{withFileTypes:true});
 for(const file of files){
  const path=join(dir,file.name);
  if(file.isDirectory()){await compactAssets(path,root);continue;}
  if(!/\.(?:js|css|html)$/.test(file.name))continue;
  let text=await readFile(path,'utf8');
  const refs=[...text.matchAll(/\/images\/[^"'`()\s,]+\.(?:png|jpe?g)/gi)].map(m=>m[0]);
  for(const ref of new Set(refs)){
   const webp=ref.replace(/\.(?:png|jpe?g)$/i,'.webp');
   try{await readFile(join(root,webp.replace(/^\//,'')));text=text.split(ref).join(webp);}catch{}
  }
  await writeFile(path,text);
 }
 for(const file of await readdir(dir,{withFileTypes:true})){
  const path=join(dir,file.name);
  if(file.isDirectory()){await compactAssets(path,root);continue;}
  if(/\.(?:png|jpe?g)$/i.test(file.name)){const webp=path.replace(/\.(?:png|jpe?g)$/i,'.webp');try{await readFile(webp);await unlink(path);}catch{}}
 }
}
async function resizeMobilePhotos(dir:string){
 for(const file of await readdir(dir,{withFileTypes:true})){
  const path=join(dir,file.name);
  if(file.isDirectory()){await resizeMobilePhotos(path);continue;}
  if(!/\.webp$/i.test(file.name))continue;
  const input=await readFile(path),meta=await sharp(input).metadata();
  const width=meta.width??0,height=meta.height??0;
  if(!width||!height||meta.pages&&meta.pages>1)continue;
  const ratio=Math.min(1,1920/Math.max(width,height),Math.sqrt(2500000/(width*height)));
  if(ratio>=1)continue;
  const output=await sharp(input).resize(Math.max(1,Math.round(width*ratio)),Math.max(1,Math.round(height*ratio)),{fit:'inside',withoutEnlargement:true}).webp({quality:88,effort:5}).toBuffer();
  await writeFile(path,output);
 }
}
export default defineConfig({root:fileURLToPath(new URL('.',import.meta.url)),publicDir:'../public',plugins:[react(),{name:'compact-mobile-images',async closeBundle(){const output=fileURLToPath(new URL('../work/mobile-web',import.meta.url));await compactAssets(output);await resizeMobilePhotos(join(output,'images'));}}],css:{postcss:{plugins:[tailwind()]}},resolve:{alias:{'@':fileURLToPath(new URL('..',import.meta.url))}},build:{outDir:'../work/mobile-web',emptyOutDir:true,target:'es2022'}});
