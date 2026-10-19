package com.yue.service;

import com.yue.dto.AuthResponse;
import com.yue.entity.User;
import com.yue.repository.UserRepository;
import com.yue.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 账号服务：注册、登录。
 * 密码用 BCrypt 加盐哈希，不存明文。
 */
@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(String username, String password) {
        if (userRepo.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = User.builder()
                .username(username)
                .passwordHash(encoder.encode(password))
                .build();
        userRepo.save(user);
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getId());
    }

    public AuthResponse login(String username, String password) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getId());
    }

    // ==================== 手机号 + 密码（方案 B，后续可升级验证码） ====================

    /** 手机号注册：手机号唯一；用户名自动生成 user<尾号4位>（冲突追加序号），昵称默认「用户<尾号4位>」 */
    @Transactional
    public AuthResponse phoneRegister(String phone, String password) {
        if (userRepo.existsByPhone(phone)) {
            throw new IllegalArgumentException("该手机号已注册");
        }
        String tail = phone.substring(phone.length() - 4);
        User user = User.builder()
                .username(uniqueUsernameFromPhone(phone))
                .passwordHash(encoder.encode(password))
                .phone(phone)
                .nickname("用户" + tail)
                .build();
        userRepo.save(user);
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getId(), user.getNickname(), user.getAvatar());
    }

    /** 手机号登录：手机号 + 密码校验；错误统一提示，避免暴露手机号是否注册 */
    public AuthResponse phoneLogin(String phone, String password) {
        User user = userRepo.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("手机号或密码错误"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("手机号或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getId(), user.getNickname(), user.getAvatar());
    }

    AuthResponse loginOrRegisterSmsUser(String phone) {
        User user = userRepo.findByPhone(phone).orElse(null);
        if (user == null) {
            byte[] secret = new byte[32];
            secureRandom.nextBytes(secret);
            String tail = phone.substring(phone.length() - 4);
            user = User.builder()
                    .username(uniqueUsernameFromPhone(phone))
                    .passwordHash(encoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secret)))
                    .phone(phone)
                    .nickname("用户" + tail)
                    .build();
            try {
                // No outer transaction here: saveAndFlush owns the transaction so a phone-unique
                // race can be caught and followed by a clean read of the winning row.
                user = userRepo.saveAndFlush(user);
            } catch (DataIntegrityViolationException collision) {
                User winner = userRepo.findByPhone(phone).orElse(null);
                if (winner == null) throw collision;
                user = winner;
            }
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getId(), user.getNickname(), user.getAvatar());
    }

    AuthResponse loginOrRegisterEmailUser(String email) {
        String normalized = normalizeEmail(email);
        User user = userRepo.findByEmail(normalized).orElse(null);
        if (user == null) {
            byte[] secret = new byte[32];
            secureRandom.nextBytes(secret);
            user = User.builder()
                    .username(uniqueUsernameFromEmail(normalized))
                    .passwordHash(encoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secret)))
                    .email(normalized)
                    .nickname("粤语学习者")
                    .build();
            try {
                user = userRepo.saveAndFlush(user);
            } catch (DataIntegrityViolationException collision) {
                User winner = userRepo.findByEmail(normalized).orElse(null);
                if (winner == null) throw collision;
                user = winner;
            }
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getId(), user.getNickname(), user.getAvatar());
    }

    private String uniqueUsernameFromPhone(String phone) {
        String tail = phone.substring(phone.length() - 4);
        String base = "user" + tail;
        if (!userRepo.existsByUsername(base)) return base;
        for (int i = 1; i < 100; i++) {
            String cand = base + i;
            if (!userRepo.existsByUsername(cand)) return cand;
        }
        return "user" + System.currentTimeMillis();
    }

    private String uniqueUsernameFromEmail(String email) {
        String base = "mail" + sha256(email).substring(0, 10);
        if (!userRepo.existsByUsername(base)) return base;
        for (int i = 1; i < 100; i++) {
            String candidate = base + i;
            if (!userRepo.existsByUsername(candidate)) return candidate;
        }
        return "mail" + System.currentTimeMillis();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("邮箱格式不正确");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
