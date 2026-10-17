package com.yue.repository;

import com.yue.entity.SmsLoginCode;
import com.yue.sms.SmsCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface SmsLoginCodeRepository extends JpaRepository<SmsLoginCode, Long> {
    Optional<SmsLoginCode> findFirstByActivePhoneOrderByCreatedAtDesc(String activePhone);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<SmsLoginCode> findFirstByActivePhoneAndStatusOrderByCreatedAtDesc(
            String activePhone, SmsCodeStatus status);

    Optional<SmsLoginCode> findFirstByPhoneAndStatusOrderByCreatedAtDesc(
            String phone, SmsCodeStatus status);

    long countByPhoneAndStatusInAndCreatedAtAfter(
            String phone, Collection<SmsCodeStatus> statuses, Instant since);

    long countByRequestIpHashAndStatusInAndCreatedAtAfter(
            String requestIpHash, Collection<SmsCodeStatus> statuses, Instant since);

    long deleteByCreatedAtBefore(Instant cutoff);
}
