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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SmsAuthService {
    private static final Logger log = LoggerFactory.getLogger(SmsAuthService.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<SmsCodeStatus> ACCEPTED = List.of(SmsCodeStatus.SENT, SmsCodeStatus.CONSUMED);

    private final SmsLoginCodeRepository repository;
    private final SmsSender sender;
    private final SmsProperties properties;
    private final SmsCodeHasher hasher;
    private final SecureRandom random = new SecureRandom();

    public SmsAuthService(SmsLoginCodeRepository repository, SmsSender sender,
                          SmsProperties properties, SmsCodeHasher hasher) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.hasher = hasher;
    }

    @Transactional(noRollbackFor = SmsDeliveryException.class)
    public int sendCode(String phone, String clientIp, Instant now) {
        if (!properties.isReady()) throw new SmsUnavailableException();
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$") || clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("Invalid SMS login request");
        }

        releaseExpiredReservation(phone, now);
        Instant dayStart = ZonedDateTime.ofInstant(now, BUSINESS_ZONE).toLocalDate()
                .atStartOfDay(BUSINESS_ZONE).toInstant();
        String ipHash = hasher.hashIp(clientIp);
        if (repository.countByPhoneAndStatusInAndCreatedAtAfter(phone, ACCEPTED, dayStart)
                >= properties.getPhoneDailyLimit()
                || repository.countByRequestIpHashAndStatusInAndCreatedAtAfter(ipHash, ACCEPTED, dayStart)
                >= properties.getIpDailyLimit()) {
            throw new SmsRateLimitException(secondsUntilNextDay(now));
        }

        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        SmsLoginCode row = SmsLoginCode.builder()
                .phone(phone)
                .codeHash(hasher.hashCode(phone, code))
                .requestIpHash(ipHash)
                .createdAt(now)
                .expiresAt(now.plusSeconds(properties.getExpirySeconds()))
                .failedAttempts(0)
                .status(SmsCodeStatus.PENDING)
                .activePhone(phone)
                .build();
        try {
            repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException e) {
            throw new SmsRateLimitException(properties.getCooldownSeconds());
        }

        try {
            SmsSendReceipt receipt = sender.sendLoginCode(
                    phone, code, Math.max(1, (properties.getExpirySeconds() + 59) / 60));
            row.setStatus(SmsCodeStatus.SENT);
            row.setProviderRequestId(receipt.providerRequestId());
            repository.saveAndFlush(row);
            log.info("sms_send accepted phoneHash={} ipHash={}", shortHash(row.getCodeHash()), shortHash(ipHash));
            return properties.getCooldownSeconds();
        } catch (SmsDeliveryException e) {
            if (!e.unknownOutcome()) {
                row.setStatus(SmsCodeStatus.FAILED);
                row.setActivePhone(null);
                repository.saveAndFlush(row);
            }
            log.warn("sms_send failed unknownOutcome={} phoneHash={} ipHash={}",
                    e.unknownOutcome(), shortHash(row.getCodeHash()), shortHash(ipHash));
            throw e;
        }
    }

    private void releaseExpiredReservation(String phone, Instant now) {
        repository.findFirstByActivePhoneOrderByCreatedAtDesc(phone).ifPresent(active -> {
            Instant cooldownEnd = active.getCreatedAt().plusSeconds(properties.getCooldownSeconds());
            if (now.isBefore(cooldownEnd)) {
                throw new SmsRateLimitException(ceilSeconds(Duration.between(now, cooldownEnd)));
            }
            active.setActivePhone(null);
            if (active.getStatus() == SmsCodeStatus.PENDING) active.setStatus(SmsCodeStatus.FAILED);
            repository.saveAndFlush(active);
        });
    }

    private int secondsUntilNextDay(Instant now) {
        Instant next = ZonedDateTime.ofInstant(now, BUSINESS_ZONE).toLocalDate().plusDays(1)
                .atStartOfDay(BUSINESS_ZONE).toInstant();
        return ceilSeconds(Duration.between(now, next));
    }

    private int ceilSeconds(Duration duration) {
        long millis = duration.toMillis();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (millis + 999) / 1000));
    }

    private String shortHash(String hash) {
        return hash.substring(0, Math.min(12, hash.length()));
    }
}
