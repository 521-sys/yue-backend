-- Idempotent seed for the six already-reviewed clips. Existing rows are never updated.
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_dubbing_material_seed;
CREATE TEMPORARY TABLE tmp_dubbing_material_seed (
  code VARCHAR(80) PRIMARY KEY, title VARCHAR(120), difficulty VARCHAR(20), tags_json TEXT,
  source_label VARCHAR(200), source_url VARCHAR(1000), license_label VARCHAR(80), license_url VARCHAR(1000), sort_order INT
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO tmp_dubbing_material_seed VALUES
('pendatang-arrival','PENDATANG：搬入新家','B2','["粤语电影","家庭","搬家"]','Kuman Pictures（已裁剪）','https://commons.wikimedia.org/wiki/File:PENDATANG_(2023)_full_movie.webm','CC BY 3.0','https://creativecommons.org/licenses/by/3.0/',10),
('sfdph-wash-hands-cantonese','正确洗手（粤语）','B1','["生活粤语","公共服务","健康"]','San Francisco Government Television（已裁剪）','https://commons.wikimedia.org/wiki/File:20200303_-_SFGovTV_-_SFDPH_WASH_HANDS_CANTONESE.webm','CC BY 3.0','https://creativecommons.org/licenses/by/3.0/',20);

INSERT INTO dubbing_materials(code,title,difficulty,tags_json,source_label,source_url,license_label,license_url,status,sort_order,version,created_at,updated_at)
SELECT seed.code,seed.title,seed.difficulty,seed.tags_json,seed.source_label,seed.source_url,seed.license_label,seed.license_url,'PUBLISHED',seed.sort_order,0,NOW(6),NOW(6)
FROM tmp_dubbing_material_seed seed
LEFT JOIN dubbing_materials existing ON existing.code=seed.code
WHERE existing.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_dubbing_part_seed;
CREATE TEMPORARY TABLE tmp_dubbing_part_seed (
  material_code VARCHAR(80), code VARCHAR(80), label VARCHAR(40), title VARCHAR(120), sort_order INT,
  video_key VARCHAR(80), original_name VARCHAR(200), file_name VARCHAR(80), size_bytes BIGINT, duration_ms BIGINT,
  PRIMARY KEY(material_code,code)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO tmp_dubbing_part_seed VALUES
('pendatang-arrival','pendatang-arrival-01','Part 01','搬入新家',10,'713c519feedf6f692d6d5ea7d08c436a','pendatang-arrival-01.mp4','seed-pendatang-arrival-01-713c519feedf.mp4',307820,9000),
('pendatang-arrival','pendatang-arrival-02','Part 02','留在外面',20,'6ff8729ea3b25f7275fe94f272bbddd5','pendatang-arrival-02.mp4','seed-pendatang-arrival-02-6ff8729ea3b2.mp4',516479,12000),
('pendatang-arrival','pendatang-arrival-03','Part 03','这间屋不能住',30,'7d3c75601d8596774c81eee8b8a842b','pendatang-arrival-03.mp4','seed-pendatang-arrival-03-7d3c75601d85.mp4',913352,24250),
('sfdph-wash-hands-cantonese','wash-hands-01','Part 01','三个简单步骤',10,'016ad0ebec3ca0b5c9bb423a59b9e3e','wash-hands-01.mp4','seed-wash-hands-01-016ad0ebec3c.mp4',1077566,17518),
('sfdph-wash-hands-cantonese','wash-hands-02','Part 02','仔细搓洗',20,'3a185ec48b72dd764681e56f1ea80032','wash-hands-02.mp4','seed-wash-hands-02-3a185ec48b72.mp4',1274882,17751),
('sfdph-wash-hands-cantonese','wash-hands-03','Part 03','擦干与搓手液',30,'1c94dad9f60c99cddcf9f98894195e4d','wash-hands-03.mp4','seed-wash-hands-03-1c94dad9f60c.mp4',1217291,22789);

INSERT INTO dubbing_parts(material_id,code,label,title,sort_order,video_key,original_name,file_name,content_type,size_bytes,duration_ms,video_codec,audio_codec,video_version,version,created_at,updated_at)
SELECT material.id,seed.code,seed.label,seed.title,seed.sort_order,seed.video_key,seed.original_name,seed.file_name,'video/mp4',seed.size_bytes,seed.duration_ms,'h264','aac',1,0,NOW(6),NOW(6)
FROM tmp_dubbing_part_seed seed
JOIN dubbing_materials material ON material.code=seed.material_code
LEFT JOIN dubbing_parts existing ON existing.material_id=material.id AND existing.code=seed.code
WHERE existing.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_dubbing_cue_seed;
CREATE TEMPORARY TABLE tmp_dubbing_cue_seed (
  material_code VARCHAR(80), part_code VARCHAR(80), code VARCHAR(80), sequence_no INT, start_ms BIGINT, end_ms BIGINT, text TEXT,
  PRIMARY KEY(material_code,part_code,code)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO tmp_dubbing_cue_seed VALUES
('pendatang-arrival','pendatang-arrival-01','pendatang-01-01',1,875,2125,'入嚟啦。'),
('pendatang-arrival','pendatang-arrival-01','pendatang-01-02',2,5416,6708,'有冇搞错啊？'),
('pendatang-arrival','pendatang-arrival-01','pendatang-01-03',3,7750,8416,'等我一阵。'),
('pendatang-arrival','pendatang-arrival-02','pendatang-02-01',1,1000,2291,'你同阿姐喺度等啊。'),
('pendatang-arrival','pendatang-arrival-02','pendatang-02-02',2,2291,3500,'入面应该有好多尘。'),
('pendatang-arrival','pendatang-arrival-02','pendatang-02-03',3,4333,5666,'我都要去。'),
('pendatang-arrival','pendatang-arrival-02','pendatang-02-04',4,6000,7750,'爸叫你等啊。'),
('pendatang-arrival','pendatang-arrival-02','pendatang-02-05',5,8500,9333,'企喺度唔好郁。'),
('pendatang-arrival','pendatang-arrival-02','pendatang-02-06',6,10250,11125,'姐！'),
('pendatang-arrival','pendatang-arrival-03','pendatang-03-01',1,875,2041,'未嚟过马来屋啊？'),
('pendatang-arrival','pendatang-arrival-03','pendatang-03-02',2,4916,9000,'文森，我哋唔可以住喺呢度㗎喎。'),
('pendatang-arrival','pendatang-arrival-03','pendatang-03-03',3,9500,12125,'附近市区应该仲有其他屋嘅，系唔系？'),
('pendatang-arrival','pendatang-arrival-03','pendatang-03-04',4,12750,14166,'有系有嘅。'),
('pendatang-arrival','pendatang-arrival-03','pendatang-03-05',5,14166,15958,'不过全部都分配咗㗎喇。'),
('pendatang-arrival','pendatang-arrival-03','pendatang-03-06',6,16791,19666,'黄先生，签喺你自己抽嘅。'),
('pendatang-arrival','pendatang-arrival-03','pendatang-03-07',7,19666,23416,'每个人都要遵守规则，如果唔系会好乱水㗎。'),
('sfdph-wash-hands-cantonese','wash-hands-01','wash-01-01',1,0,3750,'欢迎观赏以下如何正确洗手嘅片段。'),
('sfdph-wash-hands-cantonese','wash-hands-01','wash-01-02',2,4000,9750,'最能够预防你同你嘅家人生病嘅方法，就系以正确嘅方式洗手。'),
('sfdph-wash-hands-cantonese','wash-hands-01','wash-01-03',3,9875,12750,'请参照以下三个简单嘅步骤。'),
('sfdph-wash-hands-cantonese','wash-hands-01','wash-01-04',4,13000,17500,'第一步，喺流动嘅水下，用番枧洗手。'),
('sfdph-wash-hands-cantonese','wash-hands-02','wash-02-01',1,0,11750,'洗手时，最少要搓手二十秒钟，同时要将手背、手指之间、指尖、指缝、手腕同大拇指都洗干净。'),
('sfdph-wash-hands-cantonese','wash-hands-02','wash-02-02',2,12125,17750,'第三步，将番枧洗干净后，用擦手纸或烘干机将手擦干。'),
('sfdph-wash-hands-cantonese','wash-hands-03','wash-03-01',1,0,6750,'如果你特别关注周围环境嘅卫生，可以用擦手纸按住门柄进出。'),
('sfdph-wash-hands-cantonese','wash-hands-03','wash-03-02',2,7250,12000,'如果你喺屋企，可以用毛巾将手擦干净，系安全嘅。'),
('sfdph-wash-hands-cantonese','wash-hands-03','wash-03-03',3,12125,22750,'如果你冇番枧同水可以洗手，亦都可以使用酒精成分超过百分之六十嘅搓手液，亦都可以达到清洁杀菌嘅效果。');

INSERT INTO dubbing_cues(part_id,code,sequence_no,start_ms,end_ms,text)
SELECT part.id,seed.code,seed.sequence_no,seed.start_ms,seed.end_ms,seed.text
FROM tmp_dubbing_cue_seed seed
JOIN dubbing_materials material ON material.code=seed.material_code
JOIN dubbing_parts part ON part.material_id=material.id AND part.code=seed.part_code
LEFT JOIN dubbing_cues existing ON existing.part_id=part.id AND existing.code=seed.code
WHERE existing.id IS NULL;

DROP TEMPORARY TABLE tmp_dubbing_cue_seed;
DROP TEMPORARY TABLE tmp_dubbing_part_seed;
DROP TEMPORARY TABLE tmp_dubbing_material_seed;
