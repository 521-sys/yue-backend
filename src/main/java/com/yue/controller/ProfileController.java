package com.yue.controller;

import com.yue.dto.ProfileResponse;
import com.yue.dto.ProfileUpdateRequest;
import com.yue.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 个人资料接口（需登录，JWT 中取 userId）：
 *   GET /api/user/profile  查询（手机号脱敏）
 *   PUT /api/user/profile  更新昵称/头像
 */
@RestController
@RequestMapping("/api/user")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public ProfileResponse getProfile(@AuthenticationPrincipal Long userId) {
        return profileService.getProfile(userId);
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(@AuthenticationPrincipal Long userId,
                                         @Valid @RequestBody ProfileUpdateRequest req) {
        return profileService.updateProfile(userId, req.getNickname(), req.getAvatar());
    }
}
