package com.yue.controller;

import com.yue.dto.AuthResponse;
import com.yue.email.*;
import com.yue.security.JwtUtil;
import com.yue.service.AuthService;
import com.yue.service.EmailAuthService;
import com.yue.service.SmsAuthService;
import com.yue.service.WeChatAuthService;
import com.yue.sms.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmailAuthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService authService;
    @MockBean WeChatAuthService weChatAuthService;
    @MockBean SmsAuthService smsAuthService;
    @MockBean EmailAuthService emailAuthService;
    @MockBean ClientIpResolver clientIpResolver;
    @MockBean JwtUtil jwtUtil;

    @Test
    void sendReturnsAcceptedBodyWithNormalizedEmailAndResolvedIp() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.8");
        when(emailAuthService.sendCode(eq("user@example.com"), eq("203.0.113.8"), any(Instant.class)))
                .thenReturn(60);

        mvc.perform(post("/api/auth/email/code").contentType("application/json")
                        .content("{\"email\":\"User@Example.COM\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));
    }

    @Test
    void loginReturnsAuthResponse() throws Exception {
        when(emailAuthService.login(eq("user@example.com"), eq("123456"), any(Instant.class)))
                .thenReturn(new AuthResponse("jwt", "mailuser", 7L));

        mvc.perform(post("/api/auth/email/login").contentType("application/json")
                        .content("{\"email\":\"User@Example.COM\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test
    void validationRejectsMalformedOversizedEmailAndNonAsciiOrExtraDigitCodes() throws Exception {
        String oversized = "a".repeat(245) + "@example.com";
        for (String body : new String[]{
                "{\"email\":\"not-email\"}",
                "{\"email\":\"" + oversized + "\"}",
                "{\"email\":\"user@example.com\",\"code\":\"１２３４５６\"}",
                "{\"email\":\"user@example.com\",\"code\":\"1234567\"}"}) {
            String path = body.contains("code") ? "/api/auth/email/login" : "/api/auth/email/code";
            mvc.perform(post(path).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(emailAuthService);
    }

    @Test
    void rateLimitAndProviderFailuresHaveStableSafeBodies() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.8");
        when(emailAuthService.sendCode(anyString(), anyString(), any()))
                .thenThrow(new EmailRateLimitException(42));
        mvc.perform(post("/api/auth/email/code").contentType("application/json")
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAfterSeconds").value(42));

        reset(emailAuthService);
        when(emailAuthService.sendCode(anyString(), anyString(), any()))
                .thenThrow(new EmailUnavailableException("secret SMTP payload"));
        mvc.perform(post("/api/auth/email/code").contentType("application/json")
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("邮箱服务暂不可用，请稍后重试"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));

        reset(emailAuthService);
        when(emailAuthService.sendCode(anyString(), anyString(), any()))
                .thenThrow(new EmailDeliveryException("provider detail", true));
        mvc.perform(post("/api/auth/email/code").contentType("application/json")
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("邮箱服务暂不可用，请稍后重试"));
    }
}
