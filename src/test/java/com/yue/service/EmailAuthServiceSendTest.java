package com.yue.service;

import com.yue.email.*;
import com.yue.entity.EmailLoginCode;
import com.yue.repository.EmailLoginCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:email_send;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=email-send-test-secret-012345678901234567890123456789",
        "app.dubbing-asr.enabled=false",
        "app.email.enabled=true",
        "app.email.code-pepper=test-pepper-0123456789abcdef0123456789abcdef",
        "app.email.smtp.username=verify@mail.gnep.online",
        "app.email.smtp.password=test-password",
        "app.email.smtp.from=verify@mail.gnep.online"
})
class EmailAuthServiceSendTest {
    @Autowired EmailAuthService service;
    @Autowired EmailLoginCodeRepository repository;
    @Autowired EmailCodeHasher hasher;
    @Autowired EmailProperties properties;
    @Autowired RecordingEmailSender sender;

    private final Instant base = Instant.parse("2026-10-19T06:00:00Z");

    @BeforeEach
    void reset() {
        repository.deleteAll();
        sender.reset();
        properties.setEnabled(true);
        properties.setEmailDailyLimit(10);
        properties.setIpDailyLimit(30);
    }

    @Test
    void acceptedSendNormalizesEmailPersistsOnlyHashAndReturnsCooldown() {
        int retry = service.sendCode(" Learner@Example.COM ", "203.0.113.8", base);

        assertEquals(60, retry);
        assertEquals(1, sender.calls.get());
        assertEquals("learner@example.com", sender.lastEmail);
        assertTrue(sender.lastCode.matches("\\d{6}"));
        EmailLoginCode row = repository.findAll().getFirst();
        assertEquals(EmailCodeStatus.SENT, row.getStatus());
        assertEquals("learner@example.com", row.getActiveEmail());
        assertEquals(base.plusSeconds(600), row.getExpiresAt());
        assertEquals("message-1", row.getProviderRequestId());
        assertEquals(hasher.hashCode("learner@example.com", sender.lastCode), row.getCodeHash());
        assertNotEquals(sender.lastCode, row.getCodeHash());
    }

    @Test
    void disabledServiceDoesNotReserveOrCallProvider() {
        properties.setEnabled(false);
        assertThrows(EmailUnavailableException.class,
                () -> service.sendCode("user@example.com", "203.0.113.8", base));
        assertEquals(0, repository.count());
        assertEquals(0, sender.calls.get());
    }

    @Test
    void cooldownReturnsExactRemainingSeconds() {
        service.sendCode("user@example.com", "203.0.113.8", base);
        EmailRateLimitException error = assertThrows(EmailRateLimitException.class,
                () -> service.sendCode("USER@example.com", "203.0.113.8", base.plusSeconds(59)));
        assertEquals(1, error.retryAfterSeconds());
        assertEquals(1, sender.calls.get());
    }

    @Test
    void emailAndIpDailyLimitsCountAcceptedCodes() {
        properties.setEmailDailyLimit(2);
        service.sendCode("user@example.com", "203.0.113.8", base);
        service.sendCode("user@example.com", "203.0.113.8", base.plusSeconds(61));
        assertThrows(EmailRateLimitException.class,
                () -> service.sendCode("user@example.com", "203.0.113.8", base.plusSeconds(122)));

        repository.deleteAll(); sender.reset(); properties.setEmailDailyLimit(10); properties.setIpDailyLimit(2);
        service.sendCode("one@example.com", "203.0.113.8", base);
        service.sendCode("two@example.com", "203.0.113.8", base.plusSeconds(61));
        assertThrows(EmailRateLimitException.class,
                () -> service.sendCode("three@example.com", "203.0.113.8", base.plusSeconds(122)));
    }

    @Test
    void providerOutcomeControlsReservationRelease() {
        sender.mode = RecordingEmailSender.Mode.REJECT;
        assertThrows(EmailDeliveryException.class,
                () -> service.sendCode("user@example.com", "203.0.113.8", base));
        EmailLoginCode rejected = repository.findAll().getFirst();
        assertEquals(EmailCodeStatus.FAILED, rejected.getStatus());
        assertNull(rejected.getActiveEmail());

        repository.deleteAll(); sender.reset(); sender.mode = RecordingEmailSender.Mode.UNKNOWN;
        EmailDeliveryException error = assertThrows(EmailDeliveryException.class,
                () -> service.sendCode("user@example.com", "203.0.113.8", base));
        assertTrue(error.unknownOutcome());
        assertEquals(EmailCodeStatus.PENDING, repository.findAll().getFirst().getStatus());
        assertThrows(EmailRateLimitException.class,
                () -> service.sendCode("user@example.com", "203.0.113.8", base.plusSeconds(1)));
    }

    @Test
    void concurrentRequestsProduceOneProviderSend() throws Exception {
        int workers = 6;
        CyclicBarrier barrier = new CyclicBarrier(workers);
        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < workers; i++) calls.add(() -> {
            barrier.await();
            try { return service.sendCode("user@example.com", "203.0.113.8", base); }
            catch (EmailRateLimitException e) { return e; }
        });
        try (var executor = Executors.newFixedThreadPool(workers)) {
            var results = executor.invokeAll(calls).stream().map(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).toList();
            assertEquals(1, results.stream().filter(Integer.class::isInstance).count());
            assertEquals(workers - 1, results.stream().filter(EmailRateLimitException.class::isInstance).count());
        }
        assertEquals(1, sender.calls.get());
    }

    @TestConfiguration
    static class FakeSenderConfiguration {
        @Bean @Primary RecordingEmailSender recordingEmailSender() { return new RecordingEmailSender(); }
    }

    static class RecordingEmailSender implements EmailSender {
        enum Mode { ACCEPT, REJECT, UNKNOWN }
        final AtomicInteger calls = new AtomicInteger();
        volatile String lastEmail;
        volatile String lastCode;
        volatile Mode mode = Mode.ACCEPT;

        @Override
        public EmailSendReceipt sendLoginCode(String email, String code, int validMinutes) {
            int call = calls.incrementAndGet(); lastEmail = email; lastCode = code;
            if (mode == Mode.REJECT) throw new EmailDeliveryException("rejected", false);
            if (mode == Mode.UNKNOWN) throw new EmailDeliveryException("unknown", true);
            return new EmailSendReceipt("message-" + call);
        }

        void reset() { calls.set(0); lastEmail = null; lastCode = null; mode = Mode.ACCEPT; }
    }
}
