package com.yue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 学习状态表：每个用户一行，整体存储前端的 LearningState JSON。
 *
 * 设计说明：
 *  - 采用整体 JSON 存储而非拆分多表，目的是与前端 LearningState 结构完全对齐，
 *    简化"云端同步"的读写——前端直接 PUT 整个 state，后端整体覆盖。
 *  - 同步策略为 last-write-wins（最后写入胜出），前端登录后 GET 拉取，
 *    本地操作累积后定时/退出时 PUT 上传。
 *  - 如未来需细粒度合并，可在 state_json 之外增加字段或拆表。
 */
@Entity
@Table(name = "learn_state",
        uniqueConstraints = @UniqueConstraint(columnNames = "userId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联用户，一一对应 */
    @Column(nullable = false, unique = true)
    private Long userId;

    /** 前端 LearningState 的完整 JSON（learned/stuck/reviewed/coins/streak/ownedItems 等） */
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String stateJson;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
