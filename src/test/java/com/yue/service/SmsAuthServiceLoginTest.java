package com.yue.service;

import com.yue.dto.AuthResponse;
import com.yue.entity.SmsLoginCode;
import com.yue.repository.SmsLoginCodeRepository;
import com.yue.sms.SmsCodeHasher;
import com.yue.sms.SmsCodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:sms_login;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=sms-login-test-secret-012345678901234567890123456789",
        "app.dubbing-asr.enabled=false",
        "app.sms.enabled=true",
        "app.sms.code-pepper=test-pepper-0123456789abcdef0123456789abcdef",
        "app.sms.tencent.secret-id=id", "app.sms.tencent.secret-key=key",
        "app.sms.tencent.sdk-app-id=app", "app.sms.tencent.sign-name=sign",
        "app.sms.tencent.template-id=template"
})
class SmsAuthServiceLoginTest {
    @Autowired SmsAuthService service;
    @Autowired SmsLoginCodeRepository repository;
    @Autowired SmsCodeHasher hasher;
    @MockBean AuthService authService;
    final Instant now = Instant.parse("2026-10-17T08:00:00Z");

    @BeforeEach void resetState() { repository.deleteAll(); org.mockito.Mockito.reset(authService); }

    @Test void correctCodeConsumesAndCannotBeReused() {
        save("13812345678", "123456", now.plusSeconds(60));
        when(authService.loginOrRegisterSmsUser("13812345678"))
                .thenReturn(new AuthResponse("token", "user5678", 1L));
        assertEquals("token", service.login("13812345678", "123456", now).token());
        SmsLoginCode row = repository.findAll().getFirst();
        assertEquals(SmsCodeStatus.CONSUMED, row.getStatus());
        assertEquals(now, row.getConsumedAt());
        assertNull(row.getActivePhone());
        assertInvalid(() -> service.login("13812345678", "123456", now));
        verify(authService, times(1)).loginOrRegisterSmsUser(anyString());
    }

    @Test void wrongCodeIncrementsFailuresAndSixthAttemptIsRejected() {
        save("13812345678", "123456", now.plusSeconds(60));
        for (int i = 0; i < 6; i++) assertInvalid(() -> service.login("13812345678", "654321", now));
        assertEquals(5, repository.findAll().getFirst().getFailedAttempts());
        verifyNoInteractions(authService);
    }

    @Test void expiredAndMalformedCodesHaveGenericError() {
        save("13812345678", "123456", now.minusSeconds(1));
        assertInvalid(() -> service.login("13812345678", "123456", now));
        repository.deleteAll();
        assertInvalid(() -> service.login("13812345678", "１２３４５６", now));
        assertEquals(0, repository.count());
    }

    @Test void concurrentConsumersYieldOneSuccess() throws Exception {
        save("13812345678", "123456", now.plusSeconds(60));
        when(authService.loginOrRegisterSmsUser(anyString()))
                .thenReturn(new AuthResponse("token", "user5678", 1L));
        int workers = 2;
        CyclicBarrier barrier = new CyclicBarrier(workers);
        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < workers; i++) calls.add(() -> {
            barrier.await();
            try { return service.login("13812345678", "123456", now); }
            catch (IllegalArgumentException e) { return e; }
        });
        try (var executor = Executors.newFixedThreadPool(workers)) {
            var results = executor.invokeAll(calls).stream().map(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).toList();
            assertEquals(1, results.stream().filter(AuthResponse.class::isInstance).count());
            assertEquals(1, results.stream().filter(IllegalArgumentException.class::isInstance).count());
        }
        verify(authService, times(1)).loginOrRegisterSmsUser(anyString());
    }

    private void save(String phone, String code, Instant expiresAt) {
        repository.saveAndFlush(SmsLoginCode.builder().phone(phone).codeHash(hasher.hashCode(phone, code))
                .requestIpHash(hasher.hashIp("203.0.113.8")).createdAt(now.minusSeconds(30))
                .expiresAt(expiresAt).status(SmsCodeStatus.SENT).activePhone(phone).build());
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, action);
        assertEquals("验证码无效或已过期", e.getMessage());
    }
}
