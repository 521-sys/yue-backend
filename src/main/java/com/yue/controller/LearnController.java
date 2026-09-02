package com.yue.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
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
     * 上传/覆盖学习状态。Body 为整个 LearningState 的 JSON 字符串。
     */
    @PutMapping("/state")
    public SyncResponse upsertState(@AuthenticationPrincipal Long userId, @RequestBody String stateJson) {
        Instant updatedAt = learnService.upsertState(userId, stateJson);
        return new SyncResponse("synced", updatedAt);
    }
}
