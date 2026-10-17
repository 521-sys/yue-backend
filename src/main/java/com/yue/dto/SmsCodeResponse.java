package com.yue.dto;

public record SmsCodeResponse(boolean accepted, int retryAfterSeconds) {
}
