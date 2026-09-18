#!/usr/bin/env node
/** Reproducible Android WebView packaging. No automatic key generation or bundled model credentials. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const version=JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8')).version;
const help=`四时与你 WebView ${version} · 本机 APK 构建

node mobile/build-local.mjs --sdk <Android SDK> --unsigned
node mobile/build-local.mjs --sdk <SDK> --keystore <existing.p12> --alias <alias> --store-pass-file <file>

可选：--skip-web（仅在本次已重新构建网页时使用） --app-id <独立包名> --label <桌面名称>
      --output <目录> --key-pass-file <file> --keep-stage
默认仅生成未签名 APK。签名必须显式提供原有密钥；不会创建密钥、清除数据或混入 personal-model.json。
覆盖旧版需要相同包名和同一签名证书。不同包名是独立应用，不自动共享旧记录。
`;
function fail(s){throw new Error(s);}
function options(argv){const flags=new Set(['unsigned','skip-web','keep-stage','help']),fields=new Set(['sdk','keystore','alias','store-pass-file','key-pass-file','app-id','label','output']);const out={};for(let i=0;i<argv.length;i++){const a=argv[i];if(!a.startsWith('--'))fail('未知参数：'+a);const k=a.slice(2);if(k in out)fail('参数重复：'+a);if(flags.has(k))out[k]=true;else if(fields.has(k)){if(!argv[i+1]||argv[i+1].startsWith('--'))fail(a+' 缺少值');out[k]=argv[++i];}else fail('未知参数：'+a);}return out;}
function run(command,args,cwd=root){console.log('>',path.basename(command),args.map(s=>/pass/i.test(s)?'[password-file option]':s).join(' '));const r=spawnSync(command,args,{cwd,stdio:'inherit',shell:false});if(r.error)fail(`${path.basename(command)} 无法启动：${r.error.message}`);if(r.status!==0)fail(`${path.basename(command)} 失败（${r.status}）`);}
function list(dir,match){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(x=>x.isDirectory()?list(path.join(dir,x.name),match):match(x.name)?[path.join(dir,x.name)]:[]);}
function artifactDigest(file){return createHash('sha256').update(fs.readFileSync(file)).digest('hex');}
function executable(folder,name){const win=process.platform==='win32';const file=path.join(folder,name+(win?(name==='d8'||name==='apksigner'?'.bat':'.exe'):''));if(!fs.existsSync(file))fail('SDK 工具缺失：'+file);return file;}
function nativeCommand(file,args,cwd){if(process.platform==='win32'&&file.endsWith('d8.bat')){run('java',['-cp',path.join(path.dirname(file),'lib','d8.jar'),'com.android.tools.r8.D8',...args],cwd);}else if(process.platform==='win32'&&file.endsWith('apksigner.bat')){run('java',['-cp',path.join(path.dirname(file),'lib','apksigner.jar'),'com.android.apksigner.ApkSignerTool',...args],cwd);}else if(process.platform==='win32'&&file.endsWith('.bat')){const escaped=args.map(s=>'"'+s.replaceAll('"','""')+'"');run(process.env.ComSpec||'cmd.exe',['/d','/c',file+' '+escaped.join(' ')],cwd);}else run(file,args,cwd);}
let stage;
try{
 const o=options(process.argv.slice(2));if(o.help){console.log(help);process.exit(0);}
 const sdk=path.resolve(o.sdk||process.env.ANDROID_SDK_ROOT||process.env.ANDROID_HOME||'work/android-sdk');
 if(!fs.existsSync(path.join(sdk,'platforms')))fail('请通过 --sdk 指定已安装的 Android SDK（含 platform 35 和 build-tools 35）。');
 const platforms=fs.readdirSync(path.join(sdk,'platforms')).filter(s=>/^android-\d+$/.test(s)).sort((a,b)=>Number(b.split('-')[1])-Number(a.split('-')[1]));
 const androidJar=path.join(sdk,'platforms',platforms[0]||'','android.jar');if(!fs.existsSync(androidJar))fail('SDK 没有 android.jar');
 const tools=fs.readdirSync(path.join(sdk,'build-tools')).filter(s=>/^\d+(\.\d+)*$/.test(s)).sort((a,b)=>b.localeCompare(a,undefined,{numeric:true}));const folder=path.join(sdk,'build-tools',tools[0]||'');
 const aapt=executable(folder,'aapt'),d8=executable(folder,'d8'),align=executable(folder,'zipalign'),signer=executable(folder,'apksigner');
 const signing=!!o.keystore;
 if(o.unsigned&&signing)fail('--unsigned 与 --keystore 不能同时使用');
 if(signing&&!['alias','store-pass-file'].every(k=>o[k]))fail('签名必须提供 alias 与 store-pass-file');
 if(!signing&&['alias','store-pass-file','key-pass-file'].some(k=>o[k]))fail('提供了签名参数但未提供 keystore');
 for(const k of ['keystore','store-pass-file','key-pass-file'])if(o[k]&&!fs.statSync(path.resolve(o[k])).isFile())fail(k+' 必须是已有文件');
 const appId=o['app-id']||'com.luke.summer';if(!/^[A-Za-z][\w]*(?:\.[A-Za-z][\w]*){2,}$/.test(appId))fail('包名不正确');
 const label=o.label||'四时与你';if(!label.trim()||label.length>40||/[<>&"'\x00-\x1f]/.test(label))fail('应用名称含不支持的字符');
 if(!o['skip-web'])run(process.execPath,[path.join(root,'node_modules/vite/bin/vite.js'),'build','--config','mobile/vite.config.mts']);
 const web=path.join(root,'work/mobile-web');if(!fs.existsSync(path.join(web,'index.html')))fail('请先重新构建本版网页');
 const forbidden=list(web,n=>/(?:personal-model|signing-password|\.env)|\.(?:p12|jks|keystore|pem|key|ttf|otf|woff2?)$/i.test(n));if(forbidden.length)fail('网页产物含私密文件或字体二进制，已停止打包');
 stage=fs.mkdtempSync(path.join(os.tmpdir(),'luke-webview210-'));for(const p of ['assets','classes','dex'])fs.mkdirSync(path.join(stage,p));fs.cpSync(web,path.join(stage,'assets/web'),{recursive:true});fs.cpSync(path.join(root,'mobile/android'),path.join(stage,'native'),{recursive:true});
 const manifest=path.join(stage,'native/AndroidManifest.xml');let xml=fs.readFileSync(manifest,'utf8');if(!xml.includes(`android:versionName='${version}'`))fail('Android 与 package.json 版本不一致');
 xml=xml.replace('package="com.luke.summer"',`package="${appId}"`).replace("android:label='四时与你'",`android:label='${label}'`);fs.writeFileSync(manifest,xml);
 const java=path.join(stage,'native/MainActivity.java');let source=fs.readFileSync(java,'utf8');source=source.replace('package com.luke.summer;',`package ${appId};`);fs.writeFileSync(java,source);
 const output=path.resolve(o.output||path.join(root,'outputs/android'));fs.mkdirSync(output,{recursive:true});
 run('javac',['-encoding','UTF-8','--release','8','-classpath',androidJar,'-d',path.join(stage,'classes'),java]);
 nativeCommand(d8,['--lib',androidJar,'--min-api','26','--output',path.join(stage,'dex'),...list(path.join(stage,'classes'),s=>s.endsWith('.class'))]);
 nativeCommand(aapt,['package','-f','-M',manifest,'-S',path.join(stage,'native/res'),'-A',path.join(stage,'assets'),'-I',androidJar,'-F',path.join(stage,'unsigned.apk')]);
 nativeCommand(aapt,['add',path.join(stage,'unsigned.apk'),'classes.dex'],path.join(stage,'dex'));
 nativeCommand(align,['-f','-p','4',path.join(stage,'unsigned.apk'),path.join(stage,'aligned.apk')]);
 const destination=path.join(output,`Four-Seasons-Luke-WebView-${version}${appId==='com.luke.summer'?'':'-independent'}${signing?'':'-unsigned'}.apk`);
 if(signing){const args=['sign','--ks',path.resolve(o.keystore),'--ks-key-alias',o.alias,'--ks-pass','file:'+path.resolve(o['store-pass-file'])];if(o['key-pass-file'])args.push('--key-pass','file:'+path.resolve(o['key-pass-file']));args.push('--out',destination,path.join(stage,'aligned.apk'));nativeCommand(signer,args);nativeCommand(signer,['verify','--verbose','--print-certs',destination]);}else fs.copyFileSync(path.join(stage,'aligned.apk'),destination);
 nativeCommand(align,['-c','-v','4',destination]);nativeCommand(aapt,['dump','badging',destination]);
 const sha=artifactDigest(destination);fs.writeFileSync(destination+'.sha256',sha+'  '+path.basename(destination)+'\n');
 const summary={version,applicationId:appId,label,signed:signing,independent:appId!=='com.luke.summer',apk:destination,sha256:sha,bytes:fs.statSync(destination).size,sourceChanges:'Only temporary native package/label overrides. Functional source and built web resources unchanged.',signedUpgradeCompatibility:'Requires the same original package name and certificate; not inferred by this build.'};fs.writeFileSync(destination+'.json',JSON.stringify(summary,null,2)+'\n');console.log(JSON.stringify(summary,null,2));
 if(!o['keep-stage']){fs.rmSync(stage,{recursive:true,force:true});stage=undefined;}
}catch(e){console.error('构建停止：',e.message);if(stage)console.error('故障检查目录：',stage);process.exitCode=1;}
