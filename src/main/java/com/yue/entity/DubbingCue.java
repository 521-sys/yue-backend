package com.yue.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dubbing_cues",
        uniqueConstraints = @UniqueConstraint(name = "uk_dubbing_cue_part_code", columnNames = {"partId", "code"}),
        indexes = @Index(name = "idx_dubbing_cue_part_sequence", columnList = "partId,sequenceNo,id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DubbingCue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long partId;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false) private Integer sequenceNo;
    @Column(nullable = false) private Long startMs;
    @Column(nullable = false) private Long endMs;
    @Column(nullable = false, columnDefinition = "TEXT") private String text;
}
