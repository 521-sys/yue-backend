package com.yue.email;

import com.yue.entity.EmailLoginCode;
import com.yue.repository.EmailLoginCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:email_cleanup;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=email-cleanup-test-secret-012345678901234567890123456789",
        "app.dubbing-asr.enabled=false",
        "app.email.enabled=false"
})
class EmailCodeCleanupJobTest {
    @Autowired EmailLoginCodeRepository repository;
    @Autowired EmailCodeCleanupJob job;

    @BeforeEach void clean() { repository.deleteAll(); }

    @Test
    void purgeDeletesOnlyRecordsOlderThanSevenDays() {
        Instant now = Instant.parse("2026-10-19T06:00:00Z");
        repository.save(row(now.minusSeconds(7 * 86400L + 1), "old@example.com"));
        repository.save(row(now.minusSeconds(7 * 86400L - 1), "new@example.com"));

        assertEquals(1, job.purge(now));
        assertEquals(1, repository.count());
        assertEquals("new@example.com", repository.findAll().getFirst().getEmail());
    }

    private EmailLoginCode row(Instant createdAt, String email) {
        return EmailLoginCode.builder()
                .email(email)
                .codeHash("a".repeat(64))
                .requestIpHash("b".repeat(64))
                .createdAt(createdAt)
                .expiresAt(createdAt.plusSeconds(600))
                .status(EmailCodeStatus.FAILED)
                .build();
    }
}
