package com.yue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 用户表：承载账号体系。
 * 密码以 BCrypt 哈希存储，不存明文。
 */
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名，全局唯一，3~32 字符 */
    @Column(nullable = false, length = 32)
    private String username;

    /** BCrypt 哈希后的密码 */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
