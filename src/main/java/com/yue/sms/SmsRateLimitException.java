package com.yue.sms;

public class SmsRateLimitException extends RuntimeException {
    private final int retryAfterSeconds;

    public SmsRateLimitException(int retryAfterSeconds) {
        super("SMS request rate limited");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
