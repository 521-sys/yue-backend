package com.yue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 聊天代理：前端透传对话 messages，后端用服务端统一配置的
 * OpenAI 兼容大模型接口（DeepSeek / 智谱 / 通义等）完成请求并原样回传。
 * API Key 只保存在服务端环境变量（AI_API_KEY），所有用户无需自备 Key。
 * 鉴权复用全局 JWT 过滤器：未登录用户无法调用本接口。
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 均给空默认值：未配置 AI（如生产环境未设 AI_API_KEY）时应用照常启动，调用接口返回 503
    @Value("${app.ai.base-url:}")
    private String baseUrl;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.model:}")
    private String model;

    @Value("${app.ai.timeout-seconds:60}")
    private long timeoutSeconds;

    public AiChatController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Body: { "messages": [{"role":"system|user|assistant","content":"..."}...], "temperature": 0.7 }
     * 返回上游 OpenAI 兼容响应 JSON（前端读取 choices[0].message.content）。
     */
    @PostMapping("/chat")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "服务端未配置 AI（缺少 AI_API_KEY）"));
        }
        if (body == null || body.get("messages") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 messages"));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", body.get("messages"));
        Object temperature = body.get("temperature");
        payload.put("temperature", temperature == null ? 0.7 : temperature);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return ResponseEntity.status(502)
                        .body(Map.of("error", "上游 AI 接口异常（HTTP " + resp.statusCode() + "）"));
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.readValue(resp.body(), Map.class));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(502).body(Map.of("error", "AI 调用被中断"));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "AI 调用失败，请稍后重试"));
        }
    }
}
