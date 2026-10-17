package com.yue.sms;

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
                "spring.datasource.url=jdbc:h2:mem:sms_schema;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=sms-schema-test-secret-012345678901234567890123456789",
                "app.dubbing-asr.enabled=false"
        })
class SmsLoginCodeSchemaTest {
    @Autowired
    private DataSource dataSource;

    @Test
    void jpaSchemaContainsEverySecurityFieldAndActivePhoneIsUnique() throws Exception {
        Set<String> columns = new HashSet<>();
        boolean activePhoneUnique = false;
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet result = metadata.getColumns(null, null, "SMS_LOGIN_CODES", null)) {
                while (result.next()) columns.add(result.getString("COLUMN_NAME"));
            }
            try (ResultSet result = metadata.getIndexInfo(null, null, "SMS_LOGIN_CODES", true, false)) {
                while (result.next()) {
                    if ("ACTIVE_PHONE".equals(result.getString("COLUMN_NAME"))) activePhoneUnique = true;
                }
            }
        }

        assertEquals(Set.of(
                "ID", "PHONE", "CODE_HASH", "REQUEST_IP_HASH", "CREATED_AT", "EXPIRES_AT",
                "CONSUMED_AT", "FAILED_ATTEMPTS", "STATUS", "PROVIDER_REQUEST_ID", "ACTIVE_PHONE"),
                columns);
        assertTrue(activePhoneUnique, "active_phone must have a database uniqueness guard");
    }
}
