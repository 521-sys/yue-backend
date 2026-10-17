package com.yue.sms;

import com.yue.repository.SmsLoginCodeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SmsCodeCleanupJob {
    private final SmsLoginCodeRepository repository;

    public SmsCodeCleanupJob(SmsLoginCodeRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${app.sms.cleanup-cron:0 15 3 * * *}", zone = "Asia/Shanghai")
    @Transactional
    public void scheduledPurge() {
        purge(Instant.now());
    }

    @Transactional
    public long purge(Instant now) {
        return repository.deleteByCreatedAtBefore(now.minus(7, ChronoUnit.DAYS));
    }
}
