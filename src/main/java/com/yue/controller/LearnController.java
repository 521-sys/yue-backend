package com.yue.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yue.dto.SyncResponse;
import com.yue.service.LearnService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 学习状态同步接口：GET 拉取、PUT 上传（整体覆盖）。
 * 需登录鉴权，userId 由 JWT 解析后注入。
 */
@RestController
@RequestMapping("/api/learn")
public class LearnController {

    private final LearnService learnService;
    private final ObjectMapper mapper;

    public LearnController(LearnService learnService, ObjectMapper mapper) {
        this.learnService = learnService;
        this.mapper = mapper;
    }

    /**
     * 拉取当前用户的学习状态。
     * 无云端记录时返回 hasCloudData=false，前端使用本地默认状态。
     */
    @GetMapping("/state")
    public Map<String, Object> getState(@AuthenticationPrincipal Long userId) throws JsonProcessingException {
        String json = learnService.getState(userId);
        Map<String, Object> result = new HashMap<>();
        if (json == null) {
            result.put("hasCloudData", false);
        } else {
            result.put("hasCloudData", true);
            result.put("state", mapper.readValue(json, Object.class));
        }
        return result;
    }

    /**
     * 上传/覆盖学习状态。
     * 兼容两种请求体格式（便于同时服务 React H5 前端与微信小程序）：
     *   1) React 前端：直接发送 LearningState 的 JSON 字符串（@RequestBody String 原样接收）
     *   2) 小程序端：发送 { "stateJson": "<LearningState 的 JSON 字符串>" } 的包装对象
     * 两种格式最终都会被归一化为真正的 LearningState JSON 字符串后再入库。
     */
    @PutMapping("/state")
    public SyncResponse upsertState(@AuthenticationPrincipal Long userId, @RequestBody String rawBody) {
        String stateJson = unwrapStateJson(rawBody);
        Instant updatedAt = learnService.upsertState(userId, stateJson);
        return new SyncResponse("synced", updatedAt);
    }

    /**
     * 兼容包装格式：如果 body 是 {stateJson:"..."} 则提取内层字符串，否则认为已为原生 JSON 字符串。
     */
    private String unwrapStateJson(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return rawBody;
        try {
            JsonNode node = mapper.readTree(rawBody);
            if (node != null && node.has("stateJson") && node.get("stateJson").isTextual()) {
                return node.get("stateJson").asText();
            }
        } catch (Exception ignored) {
            // 非 JSON 对象或解析失败 → 退化为直接使用原始 body（React 前端的纯 JSON 字符串情形）
        }
        return rawBody;
    }
}
