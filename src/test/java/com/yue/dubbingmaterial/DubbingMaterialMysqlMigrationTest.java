package com.yue.dubbingmaterial;

import com.yue.YueApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit opt-in, disposable loopback database only. */
@EnabledIfSystemProperty(named = "admin.mysql", matches = "true")
class DubbingMaterialMysqlMigrationTest {
    private static final String URL = "jdbc:mysql://127.0.0.1:33379/admin_v1_check?allowPublicKeyRetrieval=true&useSSL=false&characterEncoding=utf8";

    @Test
    void migrationIsRepeatableCaseInsensitiveAndMatchesHibernate() throws Exception {
        try (var connection = DriverManager.getConnection(URL, "root", ""); var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            statement.execute("DROP TABLE IF EXISTS dubbing_cues");
            statement.execute("DROP TABLE IF EXISTS dubbing_parts");
            statement.execute("DROP TABLE IF EXISTS dubbing_materials");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
        try (var initial = start("create")) {}
        try (var connection = DriverManager.getConnection(URL, "root", ""); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE dubbing_cues");
            statement.execute("DROP TABLE dubbing_parts");
            statement.execute("DROP TABLE dubbing_materials");
        }

        runSql("scripts/dubbing-material-migration.sql");
        runSql("scripts/dubbing-material-migration.sql");
        runSql("scripts/dubbing-cover-migration.sql");
        runSql("scripts/dubbing-cover-migration.sql");
        runSql("scripts/dubbing-material-seed.sql");
        runSql("scripts/dubbing-material-seed.sql");
        runSql("scripts/dubbing-cover-seed.sql");
        runSql("scripts/dubbing-cover-seed.sql");

        try (var connection = DriverManager.getConnection(URL, "root", ""); var statement = connection.createStatement()) {
            assertScalar(statement, "SELECT COUNT(*) FROM dubbing_materials", "2");
            assertScalar(statement,"SELECT COUNT(*) FROM dubbing_materials WHERE cover_file_name IS NOT NULL AND cover_version=1","2");
            assertScalar(statement, "SELECT COUNT(*) FROM dubbing_parts", "6");
            assertScalar(statement, "SELECT COUNT(*) FROM dubbing_cues", "25");
            assertScalar(statement, "SELECT COUNT(*) FROM dubbing_materials WHERE status='PUBLISHED'", "2");
            assertScalar(statement, "SELECT GROUP_CONCAT(code ORDER BY sort_order,id SEPARATOR ',') FROM dubbing_materials",
                    "pendatang-arrival,sfdph-wash-hands-cantonese");
            assertScalar(statement, "SELECT COUNT(*) FROM dubbing_parts WHERE file_name LIKE 'seed-%.mp4' AND video_codec='h264' AND audio_codec='aac'", "6");

            statement.execute("UPDATE dubbing_materials SET title='运营标题',status='OFFLINE',sort_order=99 WHERE code='pendatang-arrival'");
            statement.execute("UPDATE dubbing_parts SET title='运营 Part' WHERE code='pendatang-arrival-01'");
            statement.execute("UPDATE dubbing_cues SET text='运营台词' WHERE code='pendatang-01-01'");
            runSql("scripts/dubbing-material-seed.sql");
            assertScalar(statement, "SELECT CONCAT(title,'|',status,'|',sort_order) FROM dubbing_materials WHERE code='pendatang-arrival'", "运营标题|OFFLINE|99");
            assertScalar(statement, "SELECT title FROM dubbing_parts WHERE code='pendatang-arrival-01'", "运营 Part");
            assertScalar(statement, "SELECT text FROM dubbing_cues WHERE code='pendatang-01-01'", "运营台词");
            assertScalar(statement, "SELECT COUNT(*) FROM dubbing_materials", "2");
            assertScalar(statement, "SELECT COUNT(*) FROM dubbing_parts", "6");
            assertScalar(statement, "SELECT COUNT(*) FROM dubbing_cues", "25");

            statement.execute("INSERT INTO dubbing_materials(code,title,difficulty,tags_json,source_label,source_url,license_label,license_url,status,sort_order,version,created_at,updated_at) VALUES('Movie-One','测试','B1','[]','来源','https://example.com/source','CC BY','https://example.com/license','DRAFT',0,0,NOW(6),NOW(6))");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO dubbing_materials(code,title,difficulty,tags_json,source_label,source_url,license_label,license_url,status,sort_order,version,created_at,updated_at) VALUES('movie-one','重复','B1','[]','来源','https://example.com/source','CC BY','https://example.com/license','DRAFT',0,0,NOW(6),NOW(6))"));
            statement.execute("INSERT INTO dubbing_parts(material_id,code,label,title,sort_order,video_version,version,created_at,updated_at) SELECT id,'part-01','Part 01','片段',0,0,0,NOW(6),NOW(6) FROM dubbing_materials WHERE code='Movie-One'");
            statement.execute("INSERT INTO dubbing_cues(part_id,code,sequence_no,start_ms,end_ms,text) SELECT id,'cue-01',1,0,1000,'你好' FROM dubbing_parts WHERE code='part-01'");
            statement.execute("DELETE FROM dubbing_materials WHERE code='Movie-One'");
            try (var result = statement.executeQuery("SELECT (SELECT COUNT(*) FROM dubbing_parts) + (SELECT COUNT(*) FROM dubbing_cues)")) {
                assertTrue(result.next());
                assertEquals(31, result.getInt(1));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('dubbing_materials','dubbing_parts','dubbing_cues')")) {
                assertTrue(result.next());
                assertEquals(3, result.getInt(1));
            }
        }

        try (var validated = start("validate")) {
            assertTrue(validated.isActive());
        }
    }

    private void assertScalar(java.sql.Statement statement, String query, String expected) throws Exception {
        try (var result = statement.executeQuery(query)) {
            assertTrue(result.next());
            assertEquals(expected, result.getString(1));
        }
    }

    private org.springframework.context.ConfigurableApplicationContext start(String ddl) {
        return SpringApplication.run(YueApplication.class,
                "--server.address=127.0.0.1", "--server.port=0", "--spring.profiles.active=test",
                "--spring.datasource.url=" + URL, "--spring.datasource.username=root", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver", "--spring.jpa.hibernate.ddl-auto=" + ddl,
                "--app.jwt.secret=local-only-migration-check-012345678901234567890123456789",
                "--app.dubbing-asr.enabled=false");
    }

    private void runSql(String file) throws Exception {
        var process = new ProcessBuilder(
                System.getProperty("admin.mysql.client", "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"),
                "--no-defaults", "--host=127.0.0.1", "--port=33379", "--user=root",
                "--default-character-set=utf8mb4", "admin_v1_check")
                .redirectErrorStream(true).start();
        try (var output = process.getOutputStream()) {
            output.write(Files.readAllBytes(Path.of(file)));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }
}
