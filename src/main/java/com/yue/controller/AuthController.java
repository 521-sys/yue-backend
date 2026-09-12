package com.yue.controller;

import com.yue.dto.AuthRequest;
import com.yue.dto.AuthResponse;
import com.yue.dto.WechatLoginRequest;
import com.yue.service.AuthService;
import com.yue.service.WeChatAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 账号接口：注册 / 账号密码登录 / 微信小程序 code 登录。
 * 返回 JWT，前端后续请求带 Authorization: Bearer <token>。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final WeChatAuthService wxAuthService;

    public AuthController(AuthService authService, WeChatAuthService wxAuthService) {
        this.authService = authService;
        this.wxAuthService = wxAuthService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody AuthRequest req) {
        return authService.register(req.getUsername(), req.getPassword());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest req) {
        return authService.login(req.getUsername(), req.getPassword());
    }

    /** 微信小程序登录：入参为 wx.login() 返回的 code */
    @PostMapping("/wechat")
    public AuthResponse wechatLogin(@Valid @RequestBody WechatLoginRequest req) {
        return wxAuthService.loginByCode(req.getCode());
    }

    /** 运行状态检查：告知前端当前是否已配置真实微信密钥（影响审核发布） */
    @PostMapping("/wechat/status")
    public Map<String, Object> wechatStatus() {
        return Map.of("configured", wxAuthService.isProductionConfigured());
    }
}
