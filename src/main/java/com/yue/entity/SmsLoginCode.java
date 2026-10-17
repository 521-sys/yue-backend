package com.yue.entity;

import com.yue.sms.SmsCodeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "sms_login_codes",
        uniqueConstraints = @UniqueConstraint(name = "uk_sms_login_codes_active_phone", columnNames = "active_phone"),
        indexes = {
                @Index(name = "idx_sms_login_codes_phone_created", columnList = "phone,created_at"),
                @Index(name = "idx_sms_login_codes_ip_created", columnList = "request_ip_hash,created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsLoginCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

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
    private SmsCodeStatus status;

    @Column(length = 128)
    private String providerRequestId;

    @Column(length = 20)
    private String activePhone;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = SmsCodeStatus.PENDING;
    }
}
