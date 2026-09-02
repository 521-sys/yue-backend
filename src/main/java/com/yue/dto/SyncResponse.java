package com.yue.dto;

import java.time.Instant;

/**
 * 学习状态同步响应：返回服务端写入时间，前端可用于冲突提示。
 */
public record SyncResponse(String status, Instant updatedAt) {
}
