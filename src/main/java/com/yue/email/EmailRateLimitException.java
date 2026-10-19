package com.yue.email;

public class EmailRateLimitException extends RuntimeException {
    private final int retryAfterSeconds;

    public EmailRateLimitException(int retryAfterSeconds) {
        super("Email request rate limited");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public int retryAfterSeconds() { return retryAfterSeconds; }
}
