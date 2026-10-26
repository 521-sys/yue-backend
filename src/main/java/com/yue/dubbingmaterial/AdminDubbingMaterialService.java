package com.yue.dubbingmaterial;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yue.admin.AdminApiException;
import com.yue.admin.AdminAuditService;
import com.yue.admin.AdminDtos;
import com.yue.entity.DubbingCue;
import com.yue.entity.DubbingMaterial;
import com.yue.entity.DubbingPart;
import com.yue.repository.DubbingCueRepository;
import com.yue.repository.DubbingMaterialRepository;
import com.yue.repository.DubbingPartRepository;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Instant;
import java.util.*;

import static com.yue.dubbingmaterial.DubbingMaterialDtos.*;

@Service
@Transactional
public class AdminDubbingMaterialService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "OFFLINE");

    private final DubbingMaterialRepository materials;
    private final DubbingPartRepository parts;
    private final DubbingCueRepository cues;
    private final DubbingVideoStorage storage;
    private final DubbingCoverStorage covers;
    private final AdminAuditService audit;

    public AdminDubbingMaterialService(DubbingMaterialRepository materials, DubbingPartRepository parts,
                                       DubbingCueRepository cues, DubbingVideoStorage storage,
                                       AdminAuditService audit, DubbingCoverStorage covers) {
        this.materials = materials;
        this.parts = parts;
        this.cues = cues;
        this.storage = storage;
        this.audit = audit;
        this.covers = covers;
    }

    @Transactional(readOnly = true)
    public AdminDtos.PageResult<MaterialSummary> list(String query, String status, int page, int pageSize) {
        String q = Objects.toString(query, "").trim().toLowerCase(Locale.ROOT);
        String state = Objects.toString(status, "").trim();
        if (!state.isEmpty() && !STATUSES.contains(state)) throw new AdminApiException(400, "发布状态不正确");
        Specification<DubbingMaterial> specification = (root, ignored, builder) -> builder.and(
                q.isEmpty() ? builder.conjunction() : builder.or(
                        builder.like(builder.lower(root.get("title")), "%" + q + "%"),
                        builder.like(builder.lower(root.get("code")), "%" + q + "%")),
                state.isEmpty() ? builder.conjunction() : builder.equal(root.get("status"), state));
        var result = materials.findAll(specification, AdminDtos.page(page, pageSize,
                Sort.by("sortOrder").ascending().and(Sort.by("id").ascending())));
        return new AdminDtos.PageResult<>(result.getContent().stream().map(this::summary).toList(),
                result.getTotalElements(), result.getNumber() + 1, result.getSize());
    }

    @Transactional(readOnly = true)
    public MaterialDetail get(long id) { return detail(find(id)); }

    public MaterialDetail create(String actor, MaterialSaveRequest request) {
        validateMaterial(request);
        String code = request.code().trim();
        if (materials.existsByCode(code)) throw new AdminApiException(409, "素材编码已存在");
        DubbingMaterial material = new DubbingMaterial();
        material.setCode(code);
        material.setStatus("DRAFT");
        apply(material, request);
        material = materials.saveAndFlush(material);
        if (request.parts() != null && !request.parts().isEmpty()) writeParts(material, request.parts());
        audit.record(actor, "CREATE", "DUBBING_MATERIAL", material.getId(), material.getTitle());
        return detail(material);
    }

    public MaterialDetail update(String actor, long id, MaterialSaveRequest request) {
        validateMaterial(request);
        DubbingMaterial material = locked(id);
        version(material, request.materialVersion());
        if (!material.getCode().equals(request.code().trim())) throw new AdminApiException(400, "素材编码不可修改");
        apply(material, request);
        writeParts(material, request.parts());
        touch(material);
        materials.saveAndFlush(material);
        if ("PUBLISHED".equals(material.getStatus())) validatePublication(material);
        audit.record(actor, "UPDATE", "DUBBING_MATERIAL", id, material.getTitle());
        return detail(material);
    }

    public MaterialDetail addPart(String actor, long id, PartSaveInput input) {
        DubbingMaterial material = locked(id);
        version(material, input == null ? null : input.materialVersion());
        if ("PUBLISHED".equals(material.getStatus())) throw new AdminApiException(409, "请先下架再新增 Part");
        validatePart(input, null);
        ensureUniquePartCode(material.getId(), input.code(), null);
        DubbingPart part = new DubbingPart();
        part.setMaterialId(material.getId());
        apply(part, input);
        part = parts.saveAndFlush(part);
        writeCues(part, input.cues());
        touch(material);
        materials.saveAndFlush(material);
        audit.record(actor, "ADD_PART", "DUBBING_MATERIAL", id, input.title().trim());
        return detail(material);
    }

    public MaterialDetail upload(String actor, long materialId, long partId, Long materialVersion,
                                 Long partVersion, MultipartFile file) {
        DubbingMaterial material = locked(materialId);
        version(material, materialVersion);
        if ("PUBLISHED".equals(material.getStatus())) throw new AdminApiException(409, "请先下架再替换视频");
        DubbingPart part = ownedPart(materialId, partId);
        partVersion(part, partVersion);
        DubbingVideoStorage.StoredVideo stored = storage.store(file);
        cleanup(stored.fileName(), false);
        for (DubbingCue cue : cues.findByPartIdOrderBySequenceNoAscIdAsc(partId)) {
            if (cue.getEndMs() > stored.durationMs()) throw new AdminApiException(400, "台词时间超过新视频时长");
        }
        String oldFile = part.getFileName();
        if (oldFile != null) cleanup(oldFile, true);
        if (part.getVideoKey() == null) part.setVideoKey(UUID.randomUUID().toString());
        part.setOriginalName(safeName(file == null ? null : file.getOriginalFilename()));
        part.setFileName(stored.fileName());
        part.setContentType(stored.contentType());
        part.setSizeBytes(stored.sizeBytes());
        part.setDurationMs(stored.durationMs());
        part.setVideoCodec(stored.videoCodec());
        part.setAudioCodec(stored.audioCodec());
        part.setVideoVersion(part.getVideoVersion() + 1);
        parts.saveAndFlush(part);
        touch(material);
        materials.saveAndFlush(material);
        audit.record(actor, oldFile == null ? "UPLOAD_VIDEO" : "REPLACE_VIDEO", "DUBBING_PART", partId, part.getTitle());
        return detail(material);
    }

    public MaterialDetail setStatus(String actor, long id, StatusRequest request) {
        DubbingMaterial material = locked(id);
        if (request == null || !STATUSES.contains(request.status())) throw new AdminApiException(400, "发布状态不正确");
        version(material, request.materialVersion());
        if ("PUBLISHED".equals(request.status())) validatePublication(material);
        material.setStatus(request.status());
        touch(material);
        materials.saveAndFlush(material);
        audit.record(actor, request.status(), "DUBBING_MATERIAL", id, material.getTitle());
        return detail(material);
    }

    public void delete(String actor, long id, Long expectedVersion) {
        DubbingMaterial material = locked(id);
        version(material, expectedVersion);
        if ("PUBLISHED".equals(material.getStatus())) throw new AdminApiException(409, "请先下架再删除");
        if(material.getCoverFileName()!=null)cleanupCover(material.getCoverFileName(),true);
        List<DubbingPart> owned = parts.findByMaterialIdOrderBySortOrderAscIdAsc(id);
        for (DubbingPart part : owned) if (part.getFileName() != null) cleanup(part.getFileName(), true);
        cues.deleteByPartIdIn(owned.stream().map(DubbingPart::getId).toList());
        parts.deleteByMaterialId(id);
        materials.delete(material);
        audit.record(actor, "DELETE", "DUBBING_MATERIAL", id, material.getTitle());
    }

    @Transactional(readOnly = true)
    public VideoAccess video(long materialId, long partId) {
        find(materialId);
        DubbingPart part = ownedPart(materialId, partId);
        if (part.getFileName() == null) throw new AdminApiException(404, "视频不存在");
        return access(part);
    }

    private void writeParts(DubbingMaterial material, List<PartSaveInput> inputs) {
        if (inputs == null) throw new AdminApiException(400, "Part 列表不能为空");
        if (inputs.size() > 100) throw new AdminApiException(400, "Part 数量不能超过 100");
        assertDistinct(inputs.stream().map(PartSaveInput::code).toList(), "Part 编码不能重复");
        Map<Long, DubbingPart> existing = new LinkedHashMap<>();
        for (DubbingPart part : parts.findByMaterialIdOrderBySortOrderAscIdAsc(material.getId())) existing.put(part.getId(), part);
        Set<Long> retained = new HashSet<>();
        for (PartSaveInput input : inputs) {
            DubbingPart part;
            if (input.id() == null) {
                if ("PUBLISHED".equals(material.getStatus())) throw new AdminApiException(409, "请先下架再新增 Part");
                validatePart(input, null);
                ensureUniquePartCode(material.getId(), input.code(), null);
                part = new DubbingPart();
                part.setMaterialId(material.getId());
            } else {
                part = existing.get(input.id());
                if (part == null) throw new AdminApiException(404, "Part 不存在");
                retained.add(part.getId());
                validatePart(input, part);
                partVersion(part, input.version());
                if (!part.getCode().equals(input.code().trim())) throw new AdminApiException(400, "Part 编码不可修改");
            }
            apply(part, input);
            part = parts.saveAndFlush(part);
            writeCues(part, input.cues());
        }
        List<DubbingPart> removed = existing.values().stream().filter(part -> !retained.contains(part.getId())).toList();
        if (!removed.isEmpty() && "PUBLISHED".equals(material.getStatus())) throw new AdminApiException(409, "请先下架再删除 Part");
        for (DubbingPart part : removed) {
            cues.deleteByPartId(part.getId());
            parts.delete(part);
            if (part.getFileName() != null) cleanup(part.getFileName(), true);
        }
        parts.flush();
    }

    private void writeCues(DubbingPart part, List<CueInput> inputs) {
        if (inputs == null || inputs.size() > 200) throw new AdminApiException(400, "台词数量应在 0 到 200 句之间");
        assertDistinct(inputs.stream().map(CueInput::code).toList(), "台词编码不能重复");
        cues.deleteByPartId(part.getId());
        cues.flush();
        int sequence = 1;
        for (CueInput input : inputs) {
            validateCue(input, part.getDurationMs());
            cues.save(DubbingCue.builder().partId(part.getId()).code(input.code().trim()).sequenceNo(sequence++)
                    .startMs(input.startMs()).endMs(input.endMs()).text(input.text().trim()).build());
        }
        cues.flush();
    }

    private void validatePublication(DubbingMaterial material) {
        if(material.getCoverFileName()==null)throw new AdminApiException(400,"请先上传素材封面");
        required(material.getTitle(), 120, "素材标题");
        required(material.getDifficulty(), 20, "难度");
        required(material.getSourceLabel(), 200, "来源说明");
        url(material.getSourceUrl(), "来源链接");
        required(material.getLicenseLabel(), 80, "许可名称");
        url(material.getLicenseUrl(), "许可链接");
        List<DubbingPart> owned = parts.findByMaterialIdOrderBySortOrderAscIdAsc(material.getId());
        if (owned.isEmpty()) throw new AdminApiException(400, "至少添加一个 Part 后才能发布");
        for (DubbingPart part : owned) {
            if (part.getFileName() == null || part.getDurationMs() == null || part.getDurationMs() <= 0)
                throw new AdminApiException(400, "每个 Part 都需要有效视频");
            List<DubbingCue> lines = cues.findByPartIdOrderBySequenceNoAscIdAsc(part.getId());
            if (lines.isEmpty()) throw new AdminApiException(400, "每个 Part 至少需要一句台词");
            for (DubbingCue cue : lines) if (cue.getStartMs() < 0 || cue.getEndMs() <= cue.getStartMs()
                    || cue.getEndMs() > part.getDurationMs()) throw new AdminApiException(400, "台词时间超出视频范围");
        }
    }

    private void validateMaterial(MaterialSaveRequest request) {
        if (request == null) throw new AdminApiException(400, "素材资料不能为空");
        required(request.code(), 80, "素材编码");
        stableCode(request.code(), "素材编码");
        required(request.title(), 120, "素材标题");
        required(request.difficulty(), 20, "难度");
        required(request.sourceLabel(), 200, "来源说明");
        url(request.sourceUrl(), "来源链接");
        required(request.licenseLabel(), 80, "许可名称");
        url(request.licenseUrl(), "许可链接");
        if (request.tags() == null || request.tags().size() > 20) throw new AdminApiException(400, "标签数量不能超过 20");
        for (String tag : request.tags()) required(tag, 30, "标签");
        if (request.parts() == null) throw new AdminApiException(400, "Part 列表不能为空");
    }

    private void validatePart(PartSaveInput input, DubbingPart existing) {
        if (input == null) throw new AdminApiException(400, "Part 资料不能为空");
        required(input.code(), 80, "Part 编码");
        stableCode(input.code(), "Part 编码");
        required(input.label(), 40, "Part 标签");
        required(input.title(), 120, "Part 标题");
        if (input.cues() == null) throw new AdminApiException(400, "台词列表不能为空");
    }

    private void validateCue(CueInput input, Long durationMs) {
        if (input == null) throw new AdminApiException(400, "台词资料不能为空");
        required(input.code(), 80, "台词编码");
        stableCode(input.code(), "台词编码");
        required(input.text(), 5000, "台词文字");
        if (input.startMs() == null || input.endMs() == null || input.startMs() < 0 || input.endMs() <= input.startMs())
            throw new AdminApiException(400, "台词时间不正确");
        if (durationMs != null && input.endMs() > durationMs) throw new AdminApiException(400, "台词时间超过视频时长");
    }

    private void apply(DubbingMaterial material, MaterialSaveRequest request) {
        material.setTitle(request.title().trim());
        material.setDifficulty(request.difficulty().trim());
        try { material.setTagsJson(JSON.writeValueAsString(request.tags().stream().map(String::trim).toList())); }
        catch (Exception exception) { throw new AdminApiException(400, "标签格式不正确"); }
        material.setSourceLabel(request.sourceLabel().trim());
        material.setSourceUrl(request.sourceUrl().trim());
        material.setLicenseLabel(request.licenseLabel().trim());
        material.setLicenseUrl(request.licenseUrl().trim());
        material.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void apply(DubbingPart part, PartSaveInput input) {
        if (part.getCode() == null) part.setCode(input.code().trim());
        part.setLabel(input.label().trim());
        part.setTitle(input.title().trim());
        part.setSortOrder(input.sortOrder() == null ? 0 : input.sortOrder());
    }

    private MaterialSummary summary(DubbingMaterial material) {
        return new MaterialSummary(material.getId(), material.getCode(), material.getTitle(), material.getDifficulty(),
                tags(material), material.getStatus(), material.getSortOrder(),
                parts.findByMaterialIdOrderBySortOrderAscIdAsc(material.getId()).size(), material.getVersion(), material.getUpdatedAt());
    }

    private MaterialDetail detail(DubbingMaterial material) {
        List<PartDetail> details = parts.findByMaterialIdOrderBySortOrderAscIdAsc(material.getId()).stream().map(part ->
                new PartDetail(part.getId(), part.getCode(), part.getLabel(), part.getTitle(), part.getSortOrder(),
                        part.getVersion(), videoItem(material.getId(), part),
                        cues.findByPartIdOrderBySequenceNoAscIdAsc(part.getId()).stream()
                                .map(cue -> new CueInput(cue.getCode(), cue.getStartMs(), cue.getEndMs(), cue.getText())).toList())).toList();
        return new MaterialDetail(material.getId(), material.getCode(), material.getTitle(), material.getDifficulty(),
                tags(material), material.getSourceLabel(), material.getSourceUrl(), material.getLicenseLabel(),
                material.getLicenseUrl(), material.getStatus(), material.getSortOrder(), material.getVersion(),
                material.getCreatedAt(), material.getUpdatedAt(), details, coverItem(material));
    }

    public MaterialDetail uploadCover(String actor,long id,Long expectedVersion,MultipartFile file){
        var material=locked(id);version(material,expectedVersion);
        if("PUBLISHED".equals(material.getStatus()))throw new AdminApiException(409,"请先下架再替换封面");
        var stored=covers.store(file);cleanupCover(stored.fileName(),false);
        String old=material.getCoverFileName();if(old!=null)cleanupCover(old,true);
        material.setCoverKey(UUID.randomUUID().toString());material.setCoverFileName(stored.fileName());
        material.setCoverOriginalName(safeName(file.getOriginalFilename()));material.setCoverContentType(stored.contentType());
        material.setCoverSizeBytes(stored.sizeBytes());material.setCoverWidth(stored.width());material.setCoverHeight(stored.height());
        material.setCoverVersion(material.getCoverVersion()+1);touch(material);materials.saveAndFlush(material);
        audit.record(actor,old==null?"UPLOAD_COVER":"REPLACE_COVER","DUBBING_MATERIAL",id,material.getTitle());return detail(material);
    }
    @Transactional(readOnly=true)
    public VideoAccess cover(long id){var m=find(id);return new VideoAccess(covers.path(m.getCoverFileName()),m.getCoverContentType(),"\"cover-"+m.getCoverKey()+"-v"+m.getCoverVersion()+"\"");}
    private CoverItem coverItem(DubbingMaterial m){
        if(m.getCoverFileName()==null)return null;
        return new CoverItem("/api/admin/dubbing-materials/"+m.getId()+"/cover?v="+m.getCoverVersion(),m.getCoverOriginalName(),m.getCoverContentType(),m.getCoverSizeBytes(),m.getCoverWidth(),m.getCoverHeight(),m.getCoverVersion());
    }
    private void cleanupCover(String file,boolean onCommit){
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(onCommit?status==STATUS_COMMITTED:status!=STATUS_COMMITTED){try{covers.remove(file);}catch(Exception e){LoggerFactory.getLogger(AdminDubbingMaterialService.class).warn("封面文件清理失败: {}",file);}}}
        });
    }

    private VideoItem videoItem(long materialId, DubbingPart part) {
        if (part.getFileName() == null) return null;
        return new VideoItem(part.getId(), "/api/admin/dubbing-materials/" + materialId + "/parts/" + part.getId() + "/video?v=" + part.getVideoVersion(),
                part.getOriginalName(), part.getContentType(), part.getSizeBytes(), part.getDurationMs(),
                part.getVideoCodec(), part.getAudioCodec(), part.getVideoVersion(), part.getVersion());
    }

    private List<String> tags(DubbingMaterial material) {
        try { return JSON.readValue(material.getTagsJson(), new TypeReference<>() {}); }
        catch (Exception exception) { throw new AdminApiException(500, "素材标签数据损坏"); }
    }

    private DubbingMaterial find(long id) {
        return materials.findById(id).orElseThrow(() -> new AdminApiException(404, "素材不存在"));
    }

    private DubbingMaterial locked(long id) {
        return materials.lockById(id).orElseThrow(() -> new AdminApiException(404, "素材不存在"));
    }

    private DubbingPart ownedPart(long materialId, long partId) {
        DubbingPart part = parts.findById(partId).orElseThrow(() -> new AdminApiException(404, "Part 不存在"));
        if (!Objects.equals(part.getMaterialId(), materialId)) throw new AdminApiException(404, "Part 不存在");
        return part;
    }

    private void version(DubbingMaterial material, Long expected) {
        if (expected == null || !Objects.equals(material.getVersion(), expected))
            throw new AdminApiException(409, "素材已更新，请重新加载");
    }

    private void partVersion(DubbingPart part, Long expected) {
        if (expected == null || !Objects.equals(part.getVersion(), expected))
            throw new AdminApiException(409, "Part 已更新，请重新加载");
    }

    private void ensureUniquePartCode(long materialId, String code, Long ignoredId) {
        boolean duplicate = parts.findByMaterialIdOrderBySortOrderAscIdAsc(materialId).stream()
                .anyMatch(part -> !Objects.equals(part.getId(), ignoredId) && part.getCode().equalsIgnoreCase(code.trim()));
        if (duplicate) throw new AdminApiException(409, "Part 编码已存在");
    }

    private void assertDistinct(List<String> codes, String message) {
        Set<String> seen = new HashSet<>();
        for (String code : codes) {
            if (code == null || !seen.add(code.trim().toLowerCase(Locale.ROOT))) throw new AdminApiException(409, message);
        }
    }

    private void stableCode(String value, String label) {
        if (!value.trim().matches("[A-Za-z0-9_-]+")) throw new AdminApiException(400, label + "只能包含字母、数字、下划线和连字符");
    }

    private void required(String value, int max, String label) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new AdminApiException(400, label + "为空或长度超过 " + max + " 字符");
    }

    private void url(String value, String label) {
        required(value, 1000, label);
        try {
            URI uri = URI.create(value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
                throw new IllegalArgumentException();
        } catch (Exception exception) { throw new AdminApiException(400, label + "必须是 http 或 https 绝对地址"); }
    }

    private String safeName(String original) {
        String name = Objects.toString(original, "video.mp4").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private void touch(DubbingMaterial material) { material.setUpdatedAt(Instant.now()); }

    private VideoAccess access(DubbingPart part) {
        return new VideoAccess(storage.path(part.getFileName()), part.getContentType(),
                "\"dubbing-" + part.getVideoKey() + "-v" + part.getVideoVersion() + "\"");
    }

    private void cleanup(String fileName, boolean onCommit) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (onCommit ? status == STATUS_COMMITTED : status != STATUS_COMMITTED) {
                    try { storage.remove(fileName); }
                    catch (Exception exception) { LoggerFactory.getLogger(AdminDubbingMaterialService.class)
                            .warn("视频文件清理失败: {}", fileName); }
                }
            }
        });
    }

    public record VideoAccess(java.nio.file.Path path, String contentType, String etag) {}
}
