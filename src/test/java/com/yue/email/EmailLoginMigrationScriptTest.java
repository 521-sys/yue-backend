package com.yue.email;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailLoginMigrationScriptTest {
    @Test
    void statusColumnUsesMysqlEnumExpectedByHibernateValidate() throws Exception {
        String sql = Files.readString(Path.of("scripts/email-login-migration.sql"));

        assertTrue(sql.contains("status enum('PENDING','SENT','CONSUMED','FAILED') NOT NULL"));
        assertTrue(sql.contains("data_type <> 'enum'"));
        assertTrue(sql.contains("MODIFY COLUMN status enum('PENDING','SENT','CONSUMED','FAILED') NOT NULL"));
    }
}
