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
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username"),
                @UniqueConstraint(columnNames = "wxOpenid")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名，全局唯一，3~32 字符；微信登录时自动生成 wx_<openid 后 6 位> */
    @Column(nullable = false, length = 32)
    private String username;

    /** BCrypt 哈希后的密码；微信登录用户存固定占位（仅账号密码登录时真正校验） */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** 微信用户 openid；小程序登录时写入，唯一。非微信用户为 null */
    @Column(length = 64)
    private String wxOpenid;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
