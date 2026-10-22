-- Run explicitly against a backed-up MySQL 8 database. Repeat execution is safe.
CREATE TABLE IF NOT EXISTS dubbing_materials (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
 title varchar(120) NOT NULL,
 difficulty varchar(20) NOT NULL,
 tags_json text NOT NULL,
 source_label varchar(200) NOT NULL,
 source_url varchar(1000) NOT NULL,
 license_label varchar(80) NOT NULL,
 license_url varchar(1000) NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'DRAFT',
 sort_order int NOT NULL DEFAULT 0,
 version bigint NOT NULL DEFAULT 0,
 created_at datetime(6) NOT NULL,
 updated_at datetime(6) NOT NULL,
 UNIQUE KEY uk_dubbing_material_code (code),
 KEY idx_dubbing_material_status_sort (status,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dubbing_parts (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 material_id bigint NOT NULL,
 code varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
 label varchar(40) NOT NULL,
 title varchar(120) NOT NULL,
 sort_order int NOT NULL DEFAULT 0,
 video_key varchar(80) NULL,
 original_name varchar(200) NULL,
 file_name varchar(80) NULL,
 content_type varchar(40) NULL,
 size_bytes bigint NULL,
 duration_ms bigint NULL,
 video_codec varchar(20) NULL,
 audio_codec varchar(20) NULL,
 video_version bigint NOT NULL DEFAULT 0,
 version bigint NOT NULL DEFAULT 0,
 created_at datetime(6) NOT NULL,
 updated_at datetime(6) NOT NULL,
 UNIQUE KEY uk_dubbing_part_material_code (material_id,code),
 UNIQUE KEY uk_dubbing_part_video_key (video_key),
 KEY idx_dubbing_part_material_sort (material_id,sort_order,id),
 CONSTRAINT fk_dubbing_part_material FOREIGN KEY (material_id) REFERENCES dubbing_materials(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dubbing_cues (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 part_id bigint NOT NULL,
 code varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
 sequence_no int NOT NULL,
 start_ms bigint NOT NULL,
 end_ms bigint NOT NULL,
 text text NOT NULL,
 UNIQUE KEY uk_dubbing_cue_part_code (part_id,code),
 KEY idx_dubbing_cue_part_sequence (part_id,sequence_no,id),
 CONSTRAINT fk_dubbing_cue_part FOREIGN KEY (part_id) REFERENCES dubbing_parts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
