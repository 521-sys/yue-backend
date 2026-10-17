package com.yue.sms;

import com.yue.entity.SmsLoginCode;
import com.yue.repository.SmsLoginCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:sms_cleanup;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=sms-cleanup-test-secret-012345678901234567890123456789",
        "app.dubbing-asr.enabled=false",
        "app.sms.enabled=false"
})
class SmsCodeCleanupJobTest {
    @Autowired private SmsLoginCodeRepository repository;
    @Autowired private SmsCodeCleanupJob job;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void purgeDeletesOnlyRecordsOlderThanSevenDays() {
        Instant now = Instant.parse("2026-10-17T06:00:00Z");
        repository.save(row(now.minusSeconds(7 * 86400L + 1), "13812345678"));
        repository.save(row(now.minusSeconds(7 * 86400L - 1), "13912345678"));

        assertEquals(1, job.purge(now));
        assertEquals(1, repository.count());
        assertEquals("13912345678", repository.findAll().getFirst().getPhone());
    }

    private SmsLoginCode row(Instant createdAt, String phone) {
        return SmsLoginCode.builder()
                .phone(phone)
                .codeHash("a".repeat(64))
                .requestIpHash("b".repeat(64))
                .createdAt(createdAt)
                .expiresAt(createdAt.plusSeconds(300))
                .failedAttempts(0)
                .status(SmsCodeStatus.FAILED)
                .build();
    }
}
