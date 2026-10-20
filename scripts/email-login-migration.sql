-- Run explicitly against a backed-up MySQL 8 database. Repeat execution is safe.
DELIMITER $$
DROP PROCEDURE IF EXISTS email_login_migrate$$
CREATE PROCEDURE email_login_migrate()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='users' AND column_name='email'
  ) THEN
    ALTER TABLE users ADD COLUMN email varchar(254) NULL;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='users' AND index_name='uk_users_email'
  ) THEN
    CREATE UNIQUE INDEX uk_users_email ON users(email);
  END IF;
END$$
CALL email_login_migrate()$$
DROP PROCEDURE email_login_migrate$$
DELIMITER ;

CREATE TABLE IF NOT EXISTS email_login_codes (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 email varchar(254) NOT NULL,
 code_hash varchar(64) NOT NULL,
 request_ip_hash varchar(64) NOT NULL,
 created_at datetime(6) NOT NULL,
 expires_at datetime(6) NOT NULL,
 consumed_at datetime(6) NULL,
 failed_attempts int NOT NULL DEFAULT 0,
 status enum('PENDING','SENT','CONSUMED','FAILED') NOT NULL,
 provider_request_id varchar(128) NULL,
 active_email varchar(254) NULL,
 CONSTRAINT uk_email_login_codes_active_email UNIQUE (active_email),
 INDEX idx_email_login_codes_email_created (email, created_at),
 INDEX idx_email_login_codes_ip_created (request_ip_hash, created_at)
);

DELIMITER $$
DROP PROCEDURE IF EXISTS email_login_status_migrate$$
CREATE PROCEDURE email_login_status_migrate()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='email_login_codes'
      AND column_name='status' AND data_type <> 'enum'
  ) THEN
    ALTER TABLE email_login_codes
      MODIFY COLUMN status enum('PENDING','SENT','CONSUMED','FAILED') NOT NULL;
  END IF;
END$$
CALL email_login_status_migrate()$$
DROP PROCEDURE email_login_status_migrate$$
DELIMITER ;
