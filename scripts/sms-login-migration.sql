-- Run explicitly against a backed-up MySQL 8 database. Repeat execution is safe.
CREATE TABLE IF NOT EXISTS sms_login_codes (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 phone varchar(20) NOT NULL,
 code_hash varchar(64) NOT NULL,
 request_ip_hash varchar(64) NOT NULL,
 created_at datetime(6) NOT NULL,
 expires_at datetime(6) NOT NULL,
 consumed_at datetime(6) NULL,
 failed_attempts int NOT NULL DEFAULT 0,
 status varchar(12) NOT NULL,
 provider_request_id varchar(128) NULL,
 active_phone varchar(20) NULL,
 CONSTRAINT uk_sms_login_codes_active_phone UNIQUE (active_phone),
 INDEX idx_sms_login_codes_phone_created (phone, created_at),
 INDEX idx_sms_login_codes_ip_created (request_ip_hash, created_at)
);
