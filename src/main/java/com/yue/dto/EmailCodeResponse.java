package com.yue.dto;

public record EmailCodeResponse(boolean accepted, int retryAfterSeconds) {
}
