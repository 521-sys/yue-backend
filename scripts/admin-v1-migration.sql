-- Run explicitly against a backed-up MySQL 8 database. Never run from application startup.
-- Existing scenes/scene_lines tables must already exist. Repeat execution is safe.
DELIMITER $$
DROP PROCEDURE IF EXISTS admin_v1_migrate$$
CREATE PROCEDURE admin_v1_migrate()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scenes' AND column_name='status') THEN
    ALTER TABLE scenes ADD COLUMN status varchar(12) NOT NULL DEFAULT 'DRAFT';
    UPDATE scenes SET status='PUBLISHED' WHERE builtin=1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scenes' AND column_name='category') THEN
    ALTER TABLE scenes ADD COLUMN category varchar(40) NOT NULL DEFAULT '日常生活';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scenes' AND column_name='difficulty') THEN
    ALTER TABLE scenes ADD COLUMN difficulty varchar(10) NOT NULL DEFAULT '初级';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scenes' AND column_name='sort_order') THEN
    ALTER TABLE scenes ADD COLUMN sort_order int NOT NULL DEFAULT 0;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scenes' AND column_name='version') THEN
    ALTER TABLE scenes ADD COLUMN version bigint NOT NULL DEFAULT 0;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scenes' AND column_name='updated_at') THEN
    ALTER TABLE scenes ADD COLUMN updated_at datetime(6) NULL;
    UPDATE scenes SET updated_at=created_at;
    ALTER TABLE scenes MODIFY updated_at datetime(6) NOT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scene_lines' AND column_name='jyutping') THEN
    ALTER TABLE scene_lines ADD COLUMN jyutping varchar(2000);
  END IF;
END$$
CALL admin_v1_migrate()$$
DROP PROCEDURE admin_v1_migrate$$
DELIMITER ;
CREATE TABLE IF NOT EXISTS audio_resources (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 audio_key varchar(80) NOT NULL UNIQUE,
 name varchar(200) NOT NULL,
 file_name varchar(80) NOT NULL,
 content_type varchar(40) NOT NULL,
 size bigint NOT NULL,
 version bigint NOT NULL DEFAULT 0,
 created_at datetime(6) NOT NULL
);
CREATE TABLE IF NOT EXISTS admin_audit_logs (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor varchar(80) NOT NULL,
 action varchar(40) NOT NULL,
 target_type varchar(20) NOT NULL,
 target_id varchar(80) NOT NULL,
 summary varchar(500) NOT NULL,
 created_at datetime(6) NOT NULL
);
SELECT id,code,title FROM scenes WHERE builtin=0 AND owner_user_id IS NULL;
