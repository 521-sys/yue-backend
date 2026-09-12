package com.yue.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yue.dto.AuthResponse;
import com.yue.entity.User;
import com.yue.repository.UserRepository;
import com.yue.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * 微信小程序登录服务：
 *   小程序 wx.login() → code → 后端调微信 jscode2session → 换 openid →
 *   已存在则直接登录；不存在则创建新用户 → 返回 JWT。
 * 环境变量 WX_APPID / WX_SECRET 未配置时启用"开发模式"：按 code 原样生成 openid，
 * 方便没有微信密钥时先联调（注意：生产必须配置真实密钥，否则任何人都能伪造任意身份登录）。
 */
@Service
public class WeChatAuthService {

    private static final String WX_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

    // 微信登录占位密码：仅写入哈希，实际不会被账号密码登录使用
    private static final String WX_PASSWORD_PLACEHOLDER = "__wx_login_only__";

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final String appid;
    private final String secret;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    public WeChatAuthService(
            UserRepository userRepo,
            PasswordEncoder encoder,
            JwtUtil jwtUtil,
            ObjectMapper mapper,
            @Value("${app.wechat.appid:}") String appid,
            @Value("${app.wechat.secret:}") String secret) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.mapper = mapper;
        this.appid = (appid == null ? "" : appid).trim();
        this.secret = (secret == null ? "" : secret).trim();
        this.restClient = RestClient.create();
    }

    public boolean isProductionConfigured() {
        return !appid.isEmpty() && !secret.isEmpty();
    }

    @Transactional
    public AuthResponse loginByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code 不能为空");
        }
        String openid = exchangeOpenid(code);
        User user = userRepo.findByWxOpenid(openid).orElseGet(() -> createWxUser(openid));
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getId());
    }

    /**
     * code 换 openid。未配置真实密钥时走开发模式：直接用 code 作为 openid。
     */
    private String exchangeOpenid(String code) {
        if (isProductionConfigured()) {
            try {
                String resp = restClient.get()
                        .uri(WX_URL, appid, secret, code)
                        .retrieve()
                        .body(String.class);
                JsonNode node = mapper.readTree(resp);
                JsonNode openidNode = node.get("openid");
                if (openidNode == null || openidNode.asText().isBlank()) {
                    String err = node.has("errmsg") ? node.get("errmsg").asText() : "未知错误";
                    throw new IllegalArgumentException("微信登录失败：" + err);
                }
                return openidNode.asText();
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("微信登录服务调用失败", e);
            }
        }
        // 开发模式：无密钥时本地模拟，只用于开发者自调试
        return "dev_openid_" + code;
    }

    private User createWxUser(String openid) {
        String username = generateUniqueUsername(openid);
        User u = User.builder()
                .username(username)
                .passwordHash(encoder.encode(WX_PASSWORD_PLACEHOLDER + openid))
                .wxOpenid(openid)
                .build();
        return userRepo.save(u);
    }

    /** 生成可读的唯一用户名：wx_ + openid 末尾 6 位，冲突时追加随机后缀 */
    private String generateUniqueUsername(String openid) {
        String tail = openid.length() >= 6 ? openid.substring(openid.length() - 6) : openid;
        String base = "wx_" + tail;
        if (!userRepo.existsByUsername(base)) return base;
        for (int i = 1; i < 100; i++) {
            String cand = base + i;
            if (!userRepo.existsByUsername(cand)) return cand;
        }
        return "wx_" + System.currentTimeMillis();
    }
}
