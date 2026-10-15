package com.yue.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="admin_audit_logs") @Getter @Setter @NoArgsConstructor
public class AdminAuditLog {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=80) private String actor;
 @Column(nullable=false,length=40) private String action;
 @Column(nullable=false,length=20) private String targetType;
 @Column(nullable=false,length=80) private String targetId;
 @Column(nullable=false,length=500) private String summary;
 @Column(nullable=false) private Instant createdAt;
}
