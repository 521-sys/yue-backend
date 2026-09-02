package com.yue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yue.entity.LearnState;
import com.yue.repository.LearnStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 学习状态服务：负责读写当前用户的 LearningState JSON。
 *
 * 同步策略：last-write-wins（整体覆盖）。
 *  - GET：无云端记录时返回 null，前端用本地默认状态。
 *  - PUT：覆盖写入整体 state_json，返回 updatedAt。
 */
@Service
public class LearnService {

    private final LearnStateRepository repo;
    private final ObjectMapper mapper;

    public LearnService(LearnStateRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public String getState(Long userId) {
        return repo.findByUserId(userId)
                .map(LearnState::getStateJson)
                .orElse(null);
    }

    @Transactional
    public Instant upsertState(Long userId, String stateJson) {
        // 校验是合法 JSON，防止脏数据落库
        try {
            mapper.readTree(stateJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("stateJson 不是合法 JSON");
        }
        LearnState ls = repo.findByUserId(userId)
                .orElseGet(() -> LearnState.builder().userId(userId).build());
        ls.setStateJson(stateJson);
        LearnState saved = repo.save(ls);
        return saved.getUpdatedAt();
    }
}
