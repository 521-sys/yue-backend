package com.yue.service;

import com.yue.entity.SmsLoginCode;
import com.yue.repository.SmsLoginCodeRepository;
import com.yue.sms.SmsCodeHasher;
import com.yue.sms.SmsCodeStatus;
import com.yue.sms.SmsDeliveryException;
import com.yue.sms.SmsProperties;
import com.yue.sms.SmsRateLimitException;
import com.yue.sms.SmsSendReceipt;
import com.yue.sms.SmsSender;
import com.yue.sms.SmsUnavailableException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:sms_send;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=sms-send-test-secret-012345678901234567890123456789",
        "app.dubbing-asr.enabled=false",
        "app.sms.enabled=true",
        "app.sms.code-pepper=test-pepper-0123456789abcdef0123456789abcdef",
        "app.sms.tencent.secret-id=test-id",
        "app.sms.tencent.secret-key=test-key",
        "app.sms.tencent.sdk-app-id=1400999999",
        "app.sms.tencent.sign-name=test-sign",
        "app.sms.tencent.template-id=246810"
})
class SmsAuthServiceSendTest {
    @Autowired private SmsAuthService service;
    @Autowired private SmsLoginCodeRepository repository;
    @Autowired private SmsCodeHasher hasher;
    @Autowired private SmsProperties properties;
    @Autowired private RecordingSmsSender sender;

    private final Instant base = Instant.parse("2026-10-17T06:00:00Z");

    @BeforeEach
    void reset() {
        repository.deleteAll();
        sender.reset();
        properties.setEnabled(true);
        properties.setPhoneDailyLimit(10);
        properties.setIpDailyLimit(30);
    }

    @Test
    void acceptedSendPersistsOnlyHashAndReturnsCooldown() {
        int retry = service.sendCode("13812345678", "203.0.113.8", base);

        assertEquals(60, retry);
        assertEquals(1, sender.calls.get());
        assertTrue(sender.lastCode.matches("\\d{6}"));
        SmsLoginCode row = repository.findAll().getFirst();
        assertEquals(SmsCodeStatus.SENT, row.getStatus());
        assertEquals("13812345678", row.getActivePhone());
        assertEquals(base.plusSeconds(300), row.getExpiresAt());
        assertEquals("request-1", row.getProviderRequestId());
        assertEquals(hasher.hashCode("13812345678", sender.lastCode), row.getCodeHash());
        assertNotEquals(sender.lastCode, row.getCodeHash());
    }

    @Test
    void disabledServiceDoesNotReserveOrCallProvider() {
        properties.setEnabled(false);

        assertThrows(SmsUnavailableException.class,
                () -> service.sendCode("13812345678", "203.0.113.8", base));
        assertEquals(0, repository.count());
        assertEquals(0, sender.calls.get());
    }

    @Test
    void phoneCooldownReturnsExactRemainingSeconds() {
        service.sendCode("13812345678", "203.0.113.8", base);

        SmsRateLimitException error = assertThrows(SmsRateLimitException.class,
                () -> service.sendCode("13812345678", "203.0.113.8", base.plusSeconds(59)));

        assertEquals(1, error.retryAfterSeconds());
        assertEquals(1, sender.calls.get());
    }

    @Test
    void phoneDailyLimitCountsPreviouslyAcceptedSupersededCodes() {
        for (int i = 0; i < 10; i++) {
            service.sendCode("13812345678", "203.0.113.8", base.plusSeconds(i * 61L));
        }

        SmsRateLimitException error = assertThrows(SmsRateLimitException.class,
                () -> service.sendCode("13812345678", "203.0.113.8", base.plusSeconds(10 * 61L)));

        assertTrue(error.retryAfterSeconds() > 60);
        assertEquals(10, sender.calls.get());
    }

    @Test
    void ipDailyLimitAppliesAcrossDifferentPhones() {
        properties.setIpDailyLimit(2);
        service.sendCode("13812345678", "203.0.113.8", base);
        service.sendCode("13912345678", "203.0.113.8", base.plusSeconds(61));

        assertThrows(SmsRateLimitException.class,
                () -> service.sendCode("13712345678", "203.0.113.8", base.plusSeconds(122)));
        assertEquals(2, sender.calls.get());
    }

    @Test
    void knownProviderRejectionReleasesReservationForImmediateRetry() {
        sender.mode = RecordingSmsSender.Mode.REJECT;
        assertThrows(SmsDeliveryException.class,
                () -> service.sendCode("13812345678", "203.0.113.8", base));

        SmsLoginCode failed = repository.findAll().getFirst();
        assertEquals(SmsCodeStatus.FAILED, failed.getStatus());
        assertEquals(null, failed.getActivePhone());

        sender.mode = RecordingSmsSender.Mode.ACCEPT;
        service.sendCode("13812345678", "203.0.113.8", base.plusSeconds(1));
        assertEquals(2, sender.calls.get());
    }

    @Test
    void unknownProviderOutcomeKeepsShortReservation() {
        sender.mode = RecordingSmsSender.Mode.UNKNOWN;
        SmsDeliveryException error = assertThrows(SmsDeliveryException.class,
                () -> service.sendCode("13812345678", "203.0.113.8", base));
        assertTrue(error.unknownOutcome());

        SmsLoginCode pending = repository.findAll().getFirst();
        assertEquals(SmsCodeStatus.PENDING, pending.getStatus());
        assertEquals("13812345678", pending.getActivePhone());
        assertThrows(SmsRateLimitException.class,
                () -> service.sendCode("13812345678", "203.0.113.8", base.plusSeconds(1)));
        assertEquals(1, sender.calls.get());
    }

    @Test
    void concurrentRequestsProduceOneProviderSend() throws Exception {
        int workers = 6;
        CyclicBarrier barrier = new CyclicBarrier(workers);
        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            calls.add(() -> {
                barrier.await();
                try {
                    return service.sendCode("13812345678", "203.0.113.8", base);
                } catch (SmsRateLimitException e) {
                    return e;
                }
            });
        }

        try (var executor = Executors.newFixedThreadPool(workers)) {
            var results = executor.invokeAll(calls).stream().map(future -> {
                try { return future.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).toList();
            assertEquals(1, results.stream().filter(Integer.class::isInstance).count());
            assertEquals(workers - 1,
                    results.stream().filter(SmsRateLimitException.class::isInstance).count());
        }
        assertEquals(1, sender.calls.get());
    }

    @TestConfiguration
    static class FakeSenderConfiguration {
        @Bean
        @Primary
        RecordingSmsSender recordingSmsSender() {
            return new RecordingSmsSender();
        }
    }

    static class RecordingSmsSender implements SmsSender {
        enum Mode { ACCEPT, REJECT, UNKNOWN }
        final AtomicInteger calls = new AtomicInteger();
        volatile String lastCode;
        volatile Mode mode = Mode.ACCEPT;

        @Override
        public SmsSendReceipt sendLoginCode(String phone, String code, int validMinutes) {
            int call = calls.incrementAndGet();
            lastCode = code;
            if (mode == Mode.REJECT) throw new SmsDeliveryException("rejected", false);
            if (mode == Mode.UNKNOWN) throw new SmsDeliveryException("unknown", true);
            return new SmsSendReceipt("request-" + call);
        }

        void reset() {
            calls.set(0);
            lastCode = null;
            mode = Mode.ACCEPT;
        }
    }
}
