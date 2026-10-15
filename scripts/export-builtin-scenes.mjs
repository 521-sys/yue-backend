import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';

export function mergeCatalogs(web,mini){
 const all=new Map();
 for(const d of [...web,...mini]){
  const canonical={id:d.id,title:d.title,place:d.place,emoji:d.emoji,roles:d.roles,lines:d.lines};
  if(all.has(d.id)&&JSON.stringify(all.get(d.id))!==JSON.stringify(canonical))throw new Error('两端内置场景内容冲突，请先核对：'+d.id);
  all.set(d.id,canonical);
 }
 return [...all.values()];
}
const quote=s=>s==null?'NULL':`CONVERT(0x${Buffer.from(String(s),'utf8').toString('hex')} USING utf8mb4)`;
export function generateSeed(dialogues,manifest){
 const out=['-- Generated from reviewed builtin catalog. Explicit import only; existing scenes are never overwritten.','SET NAMES utf8mb4;','START TRANSACTION;'];
 dialogues.forEach((d,order)=>{
  out.push(`INSERT INTO scenes (code,title,place,emoji,rolea,roleb,builtin,owner_user_id,created_at,updated_at,category,difficulty,sort_order,status,version) SELECT ${[d.id,d.title,d.place,d.emoji,d.roles[0],d.roles[1]].map(quote).join(',')},1,NULL,NOW(6),NOW(6),${quote('日常生活')},${quote('初级')},${order},'PUBLISHED',0 WHERE NOT EXISTS (SELECT 1 FROM scenes WHERE code=${quote(d.id)});`);
  out.push('SET @scene_id=IF(ROW_COUNT()=1,LAST_INSERT_ID(),NULL);');
  d.lines.forEach((l,i)=>{
   const audio=manifest[l.yue]?.split('/').pop();
   out.push(`INSERT INTO scene_lines (scene_id,sequence_no,speaker,cantonese_text,mandarin_text,jyutping,audio_key) SELECT @scene_id,${i+1},${[l.speaker,l.yue,l.man,l.jyutping??null,audio??null].map(quote).join(',')} WHERE @scene_id IS NOT NULL;`);
  });
 });
 out.push('COMMIT;');return out.join('\n')+'\n';
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
 const ts=createRequire(path.join(root,'yue-frontend/package.json'))('typescript');
 function load(file){const module={exports:{}};vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(root,file),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{module,exports:module.exports});return module.exports;}
 const catalog=mergeCatalogs(load('yue-frontend/src/data/dialogues.ts').DIALOGUES,load('yue-miniprogram/src/data/dialogues.ts').DIALOGUES);
 const manifest=load('yue-frontend/src/data/audio-manifest.ts').AUDIO_MANIFEST;
 const out=path.resolve(process.argv[2]||path.join(root,'yue-backend/scripts/admin-v1-seed.sql'));
 fs.writeFileSync(out,generateSeed(catalog,manifest),'utf8');console.log(`导出 ${catalog.length} 个场景到 ${out}；尚未写入任何数据库。`);
}
