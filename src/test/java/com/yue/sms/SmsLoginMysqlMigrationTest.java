package com.yue.sms;

import com.yue.YueApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit opt-in; runs only against a disposable loopback MySQL database. */
@EnabledIfSystemProperty(named = "sms.mysql", matches = "true")
class SmsLoginMysqlMigrationTest {
    private static final String URL =
            "jdbc:mysql://127.0.0.1:33379/sms_login_check?allowPublicKeyRetrieval=true&useSSL=false&characterEncoding=utf8";

    @Test
    void migrationIsRepeatableAndMatchesHibernateValidate() throws Exception {
        try (var initial = start("create")) {
            assertTrue(initial.isActive());
        }
        try (var connection = DriverManager.getConnection(URL, "root", "");
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE sms_login_codes");
        }

        runSql("scripts/sms-login-migration.sql");
        runSql("scripts/sms-login-migration.sql");

        try (var validated = start("validate")) {
            assertTrue(validated.isActive());
        }
    }

    private org.springframework.context.ConfigurableApplicationContext start(String ddl) {
        return SpringApplication.run(
                YueApplication.class,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--spring.profiles.active=test",
                "--spring.datasource.url=" + URL,
                "--spring.datasource.username=root",
                "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.jpa.hibernate.ddl-auto=" + ddl,
                "--app.jwt.secret=sms-migration-test-secret-012345678901234567890123456789",
                "--app.dubbing-asr.enabled=false",
                "--app.sms.enabled=false");
    }

    private void runSql(String file) throws Exception {
        var process = new ProcessBuilder(
                System.getProperty("sms.mysql.client", "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"),
                "--no-defaults",
                "--host=127.0.0.1",
                "--port=33379",
                "--user=root",
                "--default-character-set=utf8mb4",
                "sms_login_check")
                .redirectErrorStream(true)
                .start();
        try (var output = process.getOutputStream()) {
            output.write(Files.readAllBytes(Path.of(file)));
        }
        String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), result);
    }
}
