package com.yue.controller;

import com.yue.dto.AuthRequest;
import com.yue.dto.AuthResponse;
import com.yue.dto.EmailCodeRequest;
import com.yue.dto.EmailCodeResponse;
import com.yue.dto.EmailLoginRequest;
import com.yue.dto.PhoneAuthRequest;
import com.yue.dto.WechatLoginRequest;
import com.yue.dto.SmsCodeRequest;
import com.yue.dto.SmsCodeResponse;
import com.yue.dto.SmsLoginRequest;
import com.yue.service.AuthService;
import com.yue.service.EmailAuthService;
import com.yue.service.SmsAuthService;
import com.yue.service.WeChatAuthService;
import com.yue.sms.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Locale;

import java.util.Map;

/**
 * 账号接口：注册 / 账号密码登录 / 手机号注册登录 / 微信小程序 code 登录。
 * 返回 JWT，前端后续请求带 Authorization: Bearer <token>。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final WeChatAuthService wxAuthService;
    private final SmsAuthService smsAuthService;
    private final EmailAuthService emailAuthService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, WeChatAuthService wxAuthService,
                          SmsAuthService smsAuthService, EmailAuthService emailAuthService,
                          ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.wxAuthService = wxAuthService;
        this.smsAuthService = smsAuthService;
        this.emailAuthService = emailAuthService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/email/code")
    public ResponseEntity<EmailCodeResponse> sendEmailCode(
            @Valid @RequestBody EmailCodeRequest req, HttpServletRequest request) {
        int retry = emailAuthService.sendCode(normalizeEmail(req.email()),
                clientIpResolver.resolve(request), Instant.now());
        return ResponseEntity.accepted().body(new EmailCodeResponse(true, retry));
    }

    @PostMapping("/email/login")
    public AuthResponse emailLogin(@Valid @RequestBody EmailLoginRequest req) {
        return emailAuthService.login(normalizeEmail(req.email()), req.code(), Instant.now());
    }

    @PostMapping("/sms/code")
    public ResponseEntity<SmsCodeResponse> sendSmsCode(
            @Valid @RequestBody SmsCodeRequest req, HttpServletRequest request) {
        int retry = smsAuthService.sendCode(req.phone(), clientIpResolver.resolve(request), Instant.now());
        return ResponseEntity.accepted().body(new SmsCodeResponse(true, retry));
    }

    @PostMapping("/sms/login")
    public AuthResponse smsLogin(@Valid @RequestBody SmsLoginRequest req) {
        return smsAuthService.login(req.phone(), req.code(), Instant.now());
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody AuthRequest req) {
        return authService.register(req.getUsername(), req.getPassword());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest req) {
        return authService.login(req.getUsername(), req.getPassword());
    }

    /** H5 手机号注册（手机号 + 密码） */
    @PostMapping("/phone/register")
    public AuthResponse phoneRegister(@Valid @RequestBody PhoneAuthRequest req) {
        return authService.phoneRegister(req.getPhone(), req.getPassword());
    }

    /** H5 手机号登录（手机号 + 密码） */
    @PostMapping("/phone/login")
    public AuthResponse phoneLogin(@Valid @RequestBody PhoneAuthRequest req) {
        return authService.phoneLogin(req.getPhone(), req.getPassword());
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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
