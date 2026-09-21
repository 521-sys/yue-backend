package com.yue.controller;

import com.yue.service.TtsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 粤语 TTS 接口：把文本合成为粤语 MP3 音频返回。
 * <p>
 * Body: { "text": "早晨，今日天气点样呀？" }
 * 返回: audio/mpeg 音频流（成功 200）或 JSON 错误。
 * 鉴权：复用全局 JWT 过滤器（/api/ai/** 需登录），防止免费合成接口被滥用。
 */
@RestController
@RequestMapping("/api/ai")
public class TtsController {

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @PostMapping("/tts")
    public ResponseEntity<?> tts(@RequestBody Map<String, String> body) {
        String text = body == null ? null : body.get("text");
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 text"));
        }
        text = text.trim();
        if (text.length() > TtsService.MAX_TEXT_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("error", "text 过长（最多 " + TtsService.MAX_TEXT_LENGTH + " 字）"));
        }
        try {
            byte[] audio = ttsService.synthesize(text);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(audio);
        } catch (java.io.IOException e) {
            return ResponseEntity.status(503).body(Map.of("error", "语音合成失败：" + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).body(Map.of("error", "语音合成被中断"));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("error", "语音合成失败，请稍后重试"));
        }
    }
}
