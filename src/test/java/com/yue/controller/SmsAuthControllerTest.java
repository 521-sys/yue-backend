package com.yue.controller;

import com.yue.dto.AuthResponse;
import com.yue.service.AuthService;
import com.yue.service.SmsAuthService;
import com.yue.service.WeChatAuthService;
import com.yue.sms.ClientIpResolver;
import com.yue.sms.SmsRateLimitException;
import com.yue.sms.SmsUnavailableException;
import com.yue.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class SmsAuthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService authService;
    @MockBean WeChatAuthService weChatAuthService;
    @MockBean SmsAuthService smsAuthService;
    @MockBean ClientIpResolver clientIpResolver;
    @MockBean JwtUtil jwtUtil;

    @Test void sendReturnsAcceptedBodyAndResolvedIp() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.8");
        when(smsAuthService.sendCode(eq("13812345678"), eq("203.0.113.8"), any(Instant.class))).thenReturn(60);
        mvc.perform(post("/api/auth/sms/code").contentType("application/json")
                        .content("{\"phone\":\"13812345678\"}")
                        .header("X-Forwarded-For", "198.51.100.2"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));
    }

    @Test void loginReturnsAuthResponse() throws Exception {
        when(smsAuthService.login(eq("13812345678"), eq("123456"), any(Instant.class)))
                .thenReturn(new AuthResponse("jwt", "user5678", 7L));
        mvc.perform(post("/api/auth/sms/login").contentType("application/json")
                        .content("{\"phone\":\"13812345678\",\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test void strictValidationRejectsSpacesFullWidthAndExtraDigits() throws Exception {
        for (String body : new String[]{
                "{\"phone\":\" 13812345678\"}",
                "{\"phone\":\"13812345678\",\"code\":\"１２３４５６\"}",
                "{\"phone\":\"13812345678\",\"code\":\"1234567\"}"}) {
            String path = body.contains("code") ? "/api/auth/sms/login" : "/api/auth/sms/code";
            mvc.perform(post(path).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(smsAuthService);
    }

    @Test void rateLimitAndUnavailableHaveStableSafeBodies() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.8");
        when(smsAuthService.sendCode(anyString(), anyString(), any())).thenThrow(new SmsRateLimitException(42));
        mvc.perform(post("/api/auth/sms/code").contentType("application/json")
                        .content("{\"phone\":\"13812345678\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAfterSeconds").value(42));
        reset(smsAuthService);
        when(smsAuthService.sendCode(anyString(), anyString(), any()))
                .thenThrow(new SmsUnavailableException("secret provider payload"));
        mvc.perform(post("/api/auth/sms/code").contentType("application/json")
                        .content("{\"phone\":\"13812345678\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
