import {build} from 'esbuild';
import path from 'node:path';
await build({entryPoints:['scripts/checks/hotfix2-fixtures/restore-harness.tsx'],outfile:'work/detail-hotfix2/restore-harness.js',bundle:true,platform:'browser',format:'iife',target:'es2022',jsx:'automatic',define:{'process.env.NODE_ENV':'"production"'},plugins:[{name:'fixture-only-storage',setup(b){b.onResolve({filter:/^\.\/durable-store$/},args=>args.importer.endsWith('/lib/use-local-data.ts')?{path:path.resolve('scripts/checks/hotfix2-fixtures/durable.ts')}:null);}}]});
