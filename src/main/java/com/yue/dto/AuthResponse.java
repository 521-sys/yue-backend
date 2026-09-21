package com.yue.dto;

/**
 * 登录/注册成功响应：返回 JWT 与用户基本信息。
 * 前端拿到 token 后，后续请求带 Authorization: Bearer <token>。
 * nickname / avatar 可为空（老账号未设置时前端用默认展示）。
 */
public record AuthResponse(String token, String username, Long userId, String nickname, String avatar) {

    /** 兼容旧调用（无昵称/头像的账号密码登录场景） */
    public AuthResponse(String token, String username, Long userId) {
        this(token, username, userId, null, null);
    }
}
