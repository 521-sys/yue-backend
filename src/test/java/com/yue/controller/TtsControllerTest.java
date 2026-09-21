package com.yue.controller;

import com.yue.config.SecurityConfig;
import com.yue.security.JwtAuthFilter;
import com.yue.security.JwtUtil;
import com.yue.service.TtsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TtsController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class}) // 加载真实安全链（关闭 CSRF、/api/ai/** 需登录）
@WithMockUser // /api/ai/** 需登录，模拟已认证用户
class TtsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TtsService ttsService;

    // JwtAuthFilter 依赖 JwtUtil，@WebMvcTest 不扫描 @Component，需 mock 以满足安全过滤器装配
    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void ttsMissingText_returns400() throws Exception {
        mockMvc.perform(post("/api/ai/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void ttsBlankText_returns400() throws Exception {
        mockMvc.perform(post("/api/ai/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ttsTooLong_returns400() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TtsService.MAX_TEXT_LENGTH + 1; i++) sb.append('粤');
        mockMvc.perform(post("/api/ai/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + sb + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("过长")));
    }

    @Test
    void ttsServiceError_returns503() throws Exception {
        when(ttsService.synthesize(anyString()))
                .thenThrow(new java.io.IOException("edge-tts 不可用"));
        mockMvc.perform(post("/api/ai/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"早晨\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void ttsOk_returnsAudio() throws Exception {
        when(ttsService.synthesize(anyString()))
                .thenReturn(new byte[]{0x49, 0x44, 0x33, 0x01}); // 假 MP3 头
        mockMvc.perform(post("/api/ai/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"早晨，今日天气点样呀？\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(new byte[]{0x49, 0x44, 0x33, 0x01}));
    }
}
