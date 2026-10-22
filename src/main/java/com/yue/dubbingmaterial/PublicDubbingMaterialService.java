package com.yue.dubbingmaterial;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yue.admin.AdminApiException;
import com.yue.entity.DubbingCue;
import com.yue.entity.DubbingMaterial;
import com.yue.entity.DubbingPart;
import com.yue.repository.DubbingCueRepository;
import com.yue.repository.DubbingMaterialRepository;
import com.yue.repository.DubbingPartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

import static com.yue.dubbingmaterial.DubbingMaterialDtos.*;

@Service
@Transactional(readOnly = true)
public class PublicDubbingMaterialService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DubbingMaterialRepository materials;
    private final DubbingPartRepository parts;
    private final DubbingCueRepository cues;
    private final DubbingVideoStorage storage;

    public PublicDubbingMaterialService(DubbingMaterialRepository materials, DubbingPartRepository parts,
                                        DubbingCueRepository cues, DubbingVideoStorage storage) {
        this.materials = materials;
        this.parts = parts;
        this.cues = cues;
        this.storage = storage;
    }

    public List<PublicMaterial> list() {
        return materials.findByStatusOrderBySortOrderAscIdAsc("PUBLISHED").stream().map(this::map).toList();
    }

    public PublicMaterial get(String code) { return map(published(code)); }

    public VideoAccess video(String materialCode, String partCode) {
        DubbingMaterial material = published(materialCode);
        DubbingPart part = parts.findByMaterialIdAndCode(material.getId(), partCode)
                .orElseThrow(() -> new AdminApiException(404, "视频不存在"));
        requireCompletePart(part);
        return new VideoAccess(storage.path(part.getFileName()), part.getContentType(),
                "\"dubbing-" + part.getVideoKey() + "-v" + part.getVideoVersion() + "\"");
    }

    private DubbingMaterial published(String code) {
        DubbingMaterial material = materials.findByCode(code)
                .orElseThrow(() -> new AdminApiException(404, "素材不存在"));
        if (!"PUBLISHED".equals(material.getStatus())) throw new AdminApiException(404, "素材不存在");
        return material;
    }

    private PublicMaterial map(DubbingMaterial material) {
        try {
            if (blank(material.getTitle()) || blank(material.getDifficulty()) || blank(material.getSourceLabel())
                    || blank(material.getSourceUrl()) || blank(material.getLicenseLabel()) || blank(material.getLicenseUrl()))
                throw corrupt();
            List<DubbingPart> owned = parts.findByMaterialIdOrderBySortOrderAscIdAsc(material.getId());
            if (owned.isEmpty()) throw corrupt();
            List<PublicPart> publicParts = owned.stream().map(part -> {
                requireCompletePart(part);
                List<DubbingCue> lines = cues.findByPartIdOrderBySequenceNoAscIdAsc(part.getId());
                if (lines.isEmpty()) throw corrupt();
                List<PublicCue> publicCues = lines.stream().map(cue -> {
                    if (blank(cue.getCode()) || blank(cue.getText()) || cue.getStartMs() < 0
                            || cue.getEndMs() <= cue.getStartMs() || cue.getEndMs() > part.getDurationMs()) throw corrupt();
                    return new PublicCue(cue.getCode(), cue.getStartMs(), cue.getEndMs(), cue.getText());
                }).toList();
                String videoSrc = "/api/public/dubbing-materials/" + material.getCode() + "/parts/" + part.getCode()
                        + "/video?v=" + part.getVideoVersion();
                return new PublicPart(part.getCode(), part.getLabel(), part.getTitle(), videoSrc, publicCues);
            }).toList();
            List<String> tags = JSON.readValue(material.getTagsJson(), new TypeReference<>() {});
            return new PublicMaterial(material.getCode(), material.getTitle(), material.getDifficulty(), tags,
                    material.getSourceLabel(), material.getSourceUrl(), material.getLicenseLabel(), material.getLicenseUrl(), publicParts);
        } catch (AdminApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw corrupt();
        }
    }

    private void requireCompletePart(DubbingPart part) {
        if (blank(part.getCode()) || blank(part.getLabel()) || blank(part.getTitle()) || blank(part.getVideoKey())
                || blank(part.getFileName()) || blank(part.getContentType()) || part.getSizeBytes() == null
                || part.getDurationMs() == null || part.getDurationMs() <= 0 || !"h264".equals(part.getVideoCodec())
                || !"aac".equals(part.getAudioCodec())) throw corrupt();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private AdminApiException corrupt() { return new AdminApiException(500, "已发布配音素材数据不完整"); }

    public record VideoAccess(Path path, String contentType, String etag) {}
}
