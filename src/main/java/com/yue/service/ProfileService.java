package com.yue.service;

import com.yue.dto.ProfileResponse;
import com.yue.entity.User;
import com.yue.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人资料服务：查询/更新昵称与预设头像。
 * 手机号脱敏返回（138****1234），避免完整手机号泄露到前端展示层。
 */
@Service
public class ProfileService {

    private final UserRepository userRepo;

    public ProfileService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return new ProfileResponse(u.getUsername(), maskPhone(u.getPhone()), u.getNickname(), u.getAvatar());
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, String nickname, String avatar) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (nickname != null && !nickname.isBlank()) {
            String n = nickname.trim();
            if (n.length() > 16) throw new IllegalArgumentException("昵称最长 16 个字符");
            u.setNickname(n);
        }
        if (avatar != null && !avatar.isBlank()) {
            String a = avatar.trim();
            if (a.length() > 32) throw new IllegalArgumentException("头像标识过长");
            u.setAvatar(a);
        }
        userRepo.save(u);
        return new ProfileResponse(u.getUsername(), maskPhone(u.getPhone()), u.getNickname(), u.getAvatar());
    }

    /** 138****1234 形式脱敏；无手机号返回 null */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
