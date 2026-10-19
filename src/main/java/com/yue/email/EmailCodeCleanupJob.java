package com.yue.email;

import com.yue.repository.EmailLoginCodeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class EmailCodeCleanupJob {
    private final EmailLoginCodeRepository repository;

    public EmailCodeCleanupJob(EmailLoginCodeRepository repository) { this.repository = repository; }

    @Scheduled(cron = "${app.email.cleanup-cron:0 25 3 * * *}", zone = "Asia/Shanghai")
    @Transactional
    public void scheduledPurge() { purge(Instant.now()); }

    @Transactional
    public long purge(Instant now) {
        return repository.deleteByCreatedAtBefore(now.minus(7, ChronoUnit.DAYS));
    }
}
