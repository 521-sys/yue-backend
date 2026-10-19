package com.yue.repository;

import com.yue.email.EmailCodeStatus;
import com.yue.entity.EmailLoginCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface EmailLoginCodeRepository extends JpaRepository<EmailLoginCode, Long> {
    Optional<EmailLoginCode> findFirstByActiveEmailOrderByCreatedAtDesc(String activeEmail);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailLoginCode> findFirstByActiveEmailAndStatusOrderByCreatedAtDesc(
            String activeEmail, EmailCodeStatus status);

    Optional<EmailLoginCode> findFirstByEmailAndStatusOrderByCreatedAtDesc(
            String email, EmailCodeStatus status);

    long countByEmailAndStatusInAndCreatedAtAfter(
            String email, Collection<EmailCodeStatus> statuses, Instant since);

    long countByRequestIpHashAndStatusInAndCreatedAtAfter(
            String requestIpHash, Collection<EmailCodeStatus> statuses, Instant since);

    long deleteByCreatedAtBefore(Instant cutoff);
}
