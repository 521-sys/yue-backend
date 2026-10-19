package com.yue.service;

import com.yue.dto.AuthResponse;
import com.yue.email.EmailCodeHasher;
import com.yue.email.EmailCodeStatus;
import com.yue.entity.EmailLoginCode;
import com.yue.repository.EmailLoginCodeRepository;
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
        "spring.datasource.url=jdbc:h2:mem:email_login;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=email-login-test-secret-012345678901234567890123456789",
        "app.dubbing-asr.enabled=false",
        "app.email.enabled=true",
        "app.email.code-pepper=test-pepper-0123456789abcdef0123456789abcdef",
        "app.email.smtp.username=verify@mail.gnep.online",
        "app.email.smtp.password=test-password",
        "app.email.smtp.from=verify@mail.gnep.online"
})
class EmailAuthServiceLoginTest {
    @Autowired EmailAuthService service;
    @Autowired EmailLoginCodeRepository repository;
    @Autowired EmailCodeHasher hasher;
    @MockBean AuthService authService;
    final Instant now = Instant.parse("2026-10-19T08:00:00Z");

    @BeforeEach void resetState() { repository.deleteAll(); reset(authService); }

    @Test
    void correctCodeNormalizesEmailConsumesAndCannotBeReused() {
        save("user@example.com", "123456", now.plusSeconds(60));
        when(authService.loginOrRegisterEmailUser("user@example.com"))
                .thenReturn(new AuthResponse("token", "mailuser", 1L));

        assertEquals("token", service.login(" User@Example.COM ", "123456", now).token());
        EmailLoginCode row = repository.findAll().getFirst();
        assertEquals(EmailCodeStatus.CONSUMED, row.getStatus());
        assertEquals(now, row.getConsumedAt());
        assertNull(row.getActiveEmail());
        assertInvalid(() -> service.login("user@example.com", "123456", now));
        verify(authService, times(1)).loginOrRegisterEmailUser("user@example.com");
    }

    @Test
    void wrongCodeStopsAtFiveFailures() {
        save("user@example.com", "123456", now.plusSeconds(60));
        for (int i = 0; i < 6; i++) assertInvalid(
                () -> service.login("user@example.com", "654321", now));
        assertEquals(5, repository.findAll().getFirst().getFailedAttempts());
        verifyNoInteractions(authService);
    }

    @Test
    void expiredMalformedAndUnknownCodesUseGenericError() {
        save("user@example.com", "123456", now.minusSeconds(1));
        assertInvalid(() -> service.login("user@example.com", "123456", now));
        repository.deleteAll();
        assertInvalid(() -> service.login("not-email", "１２３４５６", now));
        assertEquals(0, repository.count());
    }

    @Test
    void concurrentConsumersYieldOneSuccess() throws Exception {
        save("user@example.com", "123456", now.plusSeconds(60));
        when(authService.loginOrRegisterEmailUser(anyString()))
                .thenReturn(new AuthResponse("token", "mailuser", 1L));
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) calls.add(() -> {
            barrier.await();
            try { return service.login("user@example.com", "123456", now); }
            catch (IllegalArgumentException e) { return e; }
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(calls).stream().map(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).toList();
            assertEquals(1, results.stream().filter(AuthResponse.class::isInstance).count());
            assertEquals(1, results.stream().filter(IllegalArgumentException.class::isInstance).count());
        }
        verify(authService, times(1)).loginOrRegisterEmailUser("user@example.com");
    }

    private void save(String email, String code, Instant expiresAt) {
        repository.saveAndFlush(EmailLoginCode.builder()
                .email(email).codeHash(hasher.hashCode(email, code))
                .requestIpHash(hasher.hashIp("203.0.113.8"))
                .createdAt(now.minusSeconds(30)).expiresAt(expiresAt)
                .status(EmailCodeStatus.SENT).activeEmail(email).build());
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, action);
        assertEquals("验证码无效或已过期", error.getMessage());
    }
}
