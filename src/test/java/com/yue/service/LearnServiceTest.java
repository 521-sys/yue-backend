package com.yue.service;

import com.yue.entity.LearnState;
import com.yue.repository.LearnStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LearnService 单元测试：读取/覆盖写入/非法 JSON/超大 payload。
 */
@ExtendWith(MockitoExtension.class)
class LearnServiceTest {

    @Mock
    private LearnStateRepository repo;

    private LearnService learnService;

    @BeforeEach
    void setUp() {
        learnService = new LearnService(repo, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    @DisplayName("无云端记录时 getState 返回 null")
    void getState_noRecord_returnsNull() {
        when(repo.findByUserId(1L)).thenReturn(Optional.empty());
        assertNull(learnService.getState(1L));
    }

    @Test
    @DisplayName("有记录时返回存储的 JSON")
    void getState_returnsJson() {
        LearnState ls = LearnState.builder()
                .id(1L).userId(1L).stateJson("{\"coins\":9}")
                .updatedAt(Instant.now()).build();
        when(repo.findByUserId(1L)).thenReturn(Optional.of(ls));

        assertEquals("{\"coins\":9}", learnService.getState(1L));
    }

    @Test
    @DisplayName("首次上传：创建新记录")
    void upsert_newRecord_creates() {
        when(repo.findByUserId(1L)).thenReturn(Optional.empty());
        when(repo.save(any(LearnState.class))).thenAnswer(inv -> {
            LearnState arg = inv.getArgument(0);
            arg.setId(100L);
            arg.setUpdatedAt(Instant.now());
            return arg;
        });

        Instant result = learnService.upsertState(1L, "{\"coins\":128}");

        assertNotNull(result);
        verify(repo).save(argThat(ls -> Long.valueOf(1L).equals(ls.getUserId())
                && "{\"coins\":128}".equals(ls.getStateJson())));
    }

    @Test
    @DisplayName("再次上传：覆盖已有记录")
    void upsert_existing_overwrites() {
        LearnState existing = LearnState.builder()
                .id(1L).userId(1L).stateJson("old")
                .updatedAt(Instant.now()).build();
        when(repo.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(LearnState.class))).thenAnswer(inv -> inv.getArgument(0));

        learnService.upsertState(1L, "{\"coins\":66}");

        assertEquals("{\"coins\":66}", existing.getStateJson());
    }

    @Test
    @DisplayName("非法 JSON 拒绝落库")
    void upsert_invalidJson_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> learnService.upsertState(1L, "{\"bad"));
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("超过 512KB 的 payload 拒绝落库")
    void upsert_tooLarge_throws() {
        String big = "{\"a\":\"" + "x".repeat(600 * 1024) + "\"}";
        assertThrows(IllegalArgumentException.class,
                () -> learnService.upsertState(1L, big));
        verify(repo, never()).save(any());
    }
}
