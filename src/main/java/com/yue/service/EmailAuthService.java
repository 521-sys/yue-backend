package com.yue.service;

import com.yue.dto.AuthResponse;
import com.yue.email.*;
import com.yue.entity.EmailLoginCode;
import com.yue.repository.EmailLoginCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class EmailAuthService {
    private static final Logger log = LoggerFactory.getLogger(EmailAuthService.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<EmailCodeStatus> ACCEPTED =
            List.of(EmailCodeStatus.SENT, EmailCodeStatus.CONSUMED);

    private final EmailLoginCodeRepository repository;
    private final EmailSender sender;
    private final EmailProperties properties;
    private final EmailCodeHasher hasher;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();

    public EmailAuthService(EmailLoginCodeRepository repository, EmailSender sender,
                            EmailProperties properties, EmailCodeHasher hasher,
                            AuthService authService) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.hasher = hasher;
        this.authService = authService;
    }

    @Transactional(noRollbackFor = EmailInvalidCodeException.class)
    public AuthResponse login(String email, String code, Instant now) {
        String normalized = normalizeOrNull(email);
        if (normalized == null || code == null || !code.matches("^\\d{6}$")) throw invalidCode();
        EmailLoginCode row = repository.findFirstByActiveEmailAndStatusOrderByCreatedAtDesc(
                        normalized, EmailCodeStatus.SENT)
                .orElseThrow(this::invalidCode);
        if (!now.isBefore(row.getExpiresAt()) || row.getFailedAttempts() >= properties.getMaxFailures()) {
            row.setStatus(EmailCodeStatus.FAILED);
            row.setActiveEmail(null);
            repository.saveAndFlush(row);
            throw invalidCode();
        }
        byte[] expected = row.getCodeHash().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hasher.hashCode(normalized, code).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            row.setFailedAttempts(row.getFailedAttempts() + 1);
            if (row.getFailedAttempts() >= properties.getMaxFailures()) {
                row.setStatus(EmailCodeStatus.FAILED);
                row.setActiveEmail(null);
            }
            repository.saveAndFlush(row);
            throw invalidCode();
        }
        row.setStatus(EmailCodeStatus.CONSUMED);
        row.setConsumedAt(now);
        row.setActiveEmail(null);
        repository.saveAndFlush(row);
        return authService.loginOrRegisterEmailUser(normalized);
    }

    @Transactional(noRollbackFor = EmailDeliveryException.class)
    public int sendCode(String email, String clientIp, Instant now) {
        if (!properties.isReady()) throw new EmailUnavailableException();
        String normalized = normalizeOrNull(email);
        if (normalized == null || clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("Invalid email login request");
        }

        releaseExpiredReservation(normalized, now);
        Instant dayStart = ZonedDateTime.ofInstant(now, BUSINESS_ZONE).toLocalDate()
                .atStartOfDay(BUSINESS_ZONE).toInstant();
        String ipHash = hasher.hashIp(clientIp);
        if (repository.countByEmailAndStatusInAndCreatedAtAfter(normalized, ACCEPTED, dayStart)
                >= properties.getEmailDailyLimit()
                || repository.countByRequestIpHashAndStatusInAndCreatedAtAfter(ipHash, ACCEPTED, dayStart)
                >= properties.getIpDailyLimit()) {
            throw new EmailRateLimitException(secondsUntilNextDay(now));
        }

        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        EmailLoginCode row = EmailLoginCode.builder()
                .email(normalized)
                .codeHash(hasher.hashCode(normalized, code))
                .requestIpHash(ipHash)
                .createdAt(now)
                .expiresAt(now.plusSeconds(properties.getExpirySeconds()))
                .status(EmailCodeStatus.PENDING)
                .activeEmail(normalized)
                .build();
        try {
            repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException collision) {
            throw new EmailRateLimitException(properties.getCooldownSeconds());
        }

        try {
            EmailSendReceipt receipt = sender.sendLoginCode(
                    normalized, code, Math.max(1, (properties.getExpirySeconds() + 59) / 60));
            row.setStatus(EmailCodeStatus.SENT);
            row.setProviderRequestId(receipt.providerRequestId());
            repository.saveAndFlush(row);
            log.info("email_send accepted emailHash={} ipHash={}",
                    shortHash(hasher.hashCode(normalized, "address")), shortHash(ipHash));
            return properties.getCooldownSeconds();
        } catch (EmailDeliveryException error) {
            if (!error.unknownOutcome()) {
                row.setStatus(EmailCodeStatus.FAILED);
                row.setActiveEmail(null);
                repository.saveAndFlush(row);
            }
            log.warn("email_send failed unknownOutcome={} emailHash={} ipHash={}",
                    error.unknownOutcome(), shortHash(hasher.hashCode(normalized, "address")), shortHash(ipHash));
            throw error;
        }
    }

    private void releaseExpiredReservation(String email, Instant now) {
        repository.findFirstByActiveEmailOrderByCreatedAtDesc(email).ifPresent(active -> {
            Instant cooldownEnd = active.getCreatedAt().plusSeconds(properties.getCooldownSeconds());
            if (now.isBefore(cooldownEnd)) {
                throw new EmailRateLimitException(ceilSeconds(Duration.between(now, cooldownEnd)));
            }
            active.setActiveEmail(null);
            if (active.getStatus() == EmailCodeStatus.PENDING) active.setStatus(EmailCodeStatus.FAILED);
            repository.saveAndFlush(active);
        });
    }

    private String normalizeOrNull(String email) {
        if (email == null) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 254
                || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) return null;
        return normalized;
    }

    private EmailInvalidCodeException invalidCode() { return new EmailInvalidCodeException(); }

    private int secondsUntilNextDay(Instant now) {
        Instant next = ZonedDateTime.ofInstant(now, BUSINESS_ZONE).toLocalDate().plusDays(1)
                .atStartOfDay(BUSINESS_ZONE).toInstant();
        return ceilSeconds(Duration.between(now, next));
    }

    private int ceilSeconds(Duration duration) {
        long millis = duration.toMillis();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (millis + 999) / 1000));
    }

    private String shortHash(String hash) { return hash.substring(0, Math.min(12, hash.length())); }
}
