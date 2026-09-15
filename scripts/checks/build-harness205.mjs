import {build} from 'vite';
import path from 'node:path';
await build({configFile:false,define:{'process.env.NODE_ENV':JSON.stringify('production')},resolve:{alias:{'@':process.cwd()}},build:{outDir:'work/harness205',emptyOutDir:true,target:'es2022',lib:{entry:path.resolve('scripts/checks/harness205.ts'),formats:['es'],fileName:()=> 'harness205.js'}}});
