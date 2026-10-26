-- MySQL 8; explicit migration, safe to repeat. Back up the database first.
SET @cover_columns = 'cover_key varchar(80) NULL, ADD COLUMN cover_original_name varchar(200) NULL, ADD COLUMN cover_file_name varchar(80) NULL, ADD COLUMN cover_content_type varchar(40) NULL, ADD COLUMN cover_size_bytes bigint NULL, ADD COLUMN cover_width int NULL, ADD COLUMN cover_height int NULL, ADD COLUMN cover_version bigint NOT NULL DEFAULT 0';
SET @cover_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='dubbing_materials' AND column_name='cover_key')=0, CONCAT('ALTER TABLE dubbing_materials ADD COLUMN ',@cover_columns), 'SELECT 1');
PREPARE cover_stmt FROM @cover_sql;
EXECUTE cover_stmt;
DEALLOCATE PREPARE cover_stmt;
