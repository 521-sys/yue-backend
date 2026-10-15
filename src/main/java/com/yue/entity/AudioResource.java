package com.yue.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="audio_resources") @Getter @Setter @NoArgsConstructor
public class AudioResource {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=80) private String audioKey;
 @Column(nullable=false,length=200) private String name;
 @Column(nullable=false,length=80) private String fileName;
 @Column(nullable=false,length=40) private String contentType;
 @Column(nullable=false) private long size;
 @Version @Column(nullable=false) private Long version;
 @Column(nullable=false) private Instant createdAt;
 @PrePersist void create(){createdAt=Instant.now();}
}
