package com.yue.repository;

import com.yue.entity.SmsLoginCode;
import com.yue.sms.SmsCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface SmsLoginCodeRepository extends JpaRepository<SmsLoginCode, Long> {
    Optional<SmsLoginCode> findFirstByPhoneAndStatusOrderByCreatedAtDesc(
            String phone, SmsCodeStatus status);

    long countByPhoneAndStatusInAndCreatedAtAfter(
            String phone, Collection<SmsCodeStatus> statuses, Instant since);

    long countByRequestIpHashAndStatusInAndCreatedAtAfter(
            String requestIpHash, Collection<SmsCodeStatus> statuses, Instant since);

    long deleteByCreatedAtBefore(Instant cutoff);
}
