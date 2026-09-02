package com.yue.dto;

/**
 * 登录/注册成功响应：返回 JWT 与用户基本信息。
 * 前端拿到 token 后，后续请求带 Authorization: Bearer <token>。
 */
public record AuthResponse(String token, String username, Long userId) {
}
