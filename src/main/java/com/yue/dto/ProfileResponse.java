package com.yue.dto;

/**
 * 个人资料响应：手机号已脱敏展示（138****1234），昵称/头像为空时前端用默认。
 */
public record ProfileResponse(String username, String phone, String nickname, String avatar) {
}
