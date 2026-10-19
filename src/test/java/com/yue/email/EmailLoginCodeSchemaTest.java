package com.yue.email;

import com.yue.YueApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = YueApplication.class,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:email_schema;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=email-schema-test-secret-012345678901234567890123456789",
                "app.dubbing-asr.enabled=false"
        })
class EmailLoginCodeSchemaTest {
    @Autowired DataSource dataSource;

    @Test
    void jpaSchemaContainsSecurityFieldsAndActiveEmailIsUnique() throws Exception {
        Set<String> columns = new HashSet<>();
        boolean activeEmailUnique = false;
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet result = metadata.getColumns(null, null, "EMAIL_LOGIN_CODES", null)) {
                while (result.next()) columns.add(result.getString("COLUMN_NAME"));
            }
            try (ResultSet result = metadata.getIndexInfo(null, null, "EMAIL_LOGIN_CODES", true, false)) {
                while (result.next()) {
                    if ("ACTIVE_EMAIL".equals(result.getString("COLUMN_NAME"))) activeEmailUnique = true;
                }
            }
        }

        assertEquals(Set.of(
                "ID", "EMAIL", "CODE_HASH", "REQUEST_IP_HASH", "CREATED_AT", "EXPIRES_AT",
                "CONSUMED_AT", "FAILED_ATTEMPTS", "STATUS", "PROVIDER_REQUEST_ID", "ACTIVE_EMAIL"),
                columns);
        assertTrue(activeEmailUnique, "active_email must have a database uniqueness guard");
    }
}
