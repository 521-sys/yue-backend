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
                @UniqueConstraint(columnNames = "wxOpenid"),
                @UniqueConstraint(columnNames = "phone"),
                @UniqueConstraint(columnNames = "email")
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

    /** 登录用户名，全局唯一，3~32 字符；微信登录时自动生成 wx_<openid 后 6 位>，手机号登录时自动生成 user<尾号> */
    @Column(nullable = false, length = 32)
    private String username;

    /** BCrypt 哈希后的密码；微信登录用户存固定占位（仅账号密码登录时真正校验） */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** 微信用户 openid；小程序登录时写入，唯一。非微信用户为 null */
    @Column(length = 64)
    private String wxOpenid;

    /** 手机号（H5 手机号登录）；唯一，可为空（老用户/微信用户无手机号） */
    @Column(length = 20)
    private String phone;

    /** 邮箱验证码登录标识；规范化为小写后保存，唯一，可为空 */
    @Column(length = 254)
    private String email;

    /** 显示昵称；手机号用户默认「用户<尾号4位>」，个人中心可改 */
    @Column(length = 32)
    private String nickname;

    /** 预设头像标识（emoji 或 key）；空 = 默认头像 */
    @Column(length = 32)
    private String avatar;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
