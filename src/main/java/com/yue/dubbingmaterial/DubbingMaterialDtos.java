package com.yue.dubbingmaterial;

import java.time.Instant;
import java.util.List;

public final class DubbingMaterialDtos {
    private DubbingMaterialDtos() {}

    public record MaterialSummary(Long id, String code, String title, String difficulty, List<String> tags,
                                  String status, int sortOrder, int partCount, Long materialVersion,
                                  Instant updatedAt) {}

    public record MaterialDetail(Long id, String code, String title, String difficulty, List<String> tags,
                                 String sourceLabel, String sourceUrl, String licenseLabel, String licenseUrl,
                                 String status, int sortOrder, Long materialVersion, Instant createdAt,
                                 Instant updatedAt, List<PartDetail> parts, CoverItem cover) {}

    public record CoverItem(String url, String originalName, String contentType, long sizeBytes,
                            int width, int height, long coverVersion) {}

    public record MaterialSaveRequest(String code, String title, String difficulty, List<String> tags,
                                      String sourceLabel, String sourceUrl, String licenseLabel, String licenseUrl,
                                      Integer sortOrder, Long materialVersion, List<PartSaveInput> parts) {}

    public record PartSaveInput(Long id, String code, String label, String title, Integer sortOrder, Long version,
                                Long materialVersion, List<CueInput> cues) {}

    public record PartDetail(Long id, String code, String label, String title, int sortOrder, Long version,
                             VideoItem video, List<CueInput> cues) {}

    public record CueInput(String code, Long startMs, Long endMs, String text) {}

    public record StatusRequest(String status, Long materialVersion) {}

    public record VideoItem(Long partId, String url, String originalName, String contentType, Long sizeBytes,
                            Long durationMs, String videoCodec, String audioCodec, long videoVersion, Long version) {}

    public record PublicMaterial(String id, String title, String difficulty, List<String> tags,
                                 String sourceLabel, String sourceUrl, String licenseLabel, String licenseUrl,
                                 List<PublicPart> parts, String coverUrl) {}

    public record PublicPart(String id, String label, String title, String videoSrc, List<PublicCue> cues) {}

    public record PublicCue(String id, long startMs, long endMs, String text) {}
}
