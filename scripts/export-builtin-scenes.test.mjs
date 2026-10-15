import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mergeCatalogs,generateSeed} from './export-builtin-scenes.mjs';
const d={id:'tea',title:'点餐',place:'香港',emoji:'🍵',roles:['店员','顾客'],lines:[{speaker:'A',yue:'唔该',man:'谢谢'}]};
test('identical builtins merge once and conflicting content is rejected',()=>{
 assert.equal(mergeCatalogs([d],[structuredClone(d)]).length,1);
 assert.throws(()=>mergeCatalogs([d],[{...d,title:'其他'}]),/tea/);
});
test('seed preserves existing content and only inserts lines for a new scene',()=>{
 const sql=generateSeed([d],{'唔该':'/audio/a0001.mp3'});
 assert.match(sql,/WHERE NOT EXISTS/);assert.match(sql,/IF\(ROW_COUNT\(\)=1,LAST_INSERT_ID\(\),NULL\)/);
 assert.match(sql,/WHERE @scene_id IS NOT NULL/);assert.doesNotMatch(sql,/UPDATE scenes/);assert.match(sql,/61303030312e6d7033/);
});
