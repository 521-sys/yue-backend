package com.yue.entity;

import com.yue.email.EmailCodeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "email_login_codes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_email_login_codes_active_email", columnNames = "active_email"),
        indexes = {
                @Index(name = "idx_email_login_codes_email_created", columnList = "email,created_at"),
                @Index(name = "idx_email_login_codes_ip_created", columnList = "request_ip_hash,created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLoginCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false, length = 64)
    private String requestIpHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    @Column(nullable = false)
    private int failedAttempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EmailCodeStatus status;

    @Column(length = 128)
    private String providerRequestId;

    @Column(length = 254)
    private String activeEmail;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = EmailCodeStatus.PENDING;
    }
}
