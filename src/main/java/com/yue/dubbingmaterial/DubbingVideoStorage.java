package com.yue.dubbingmaterial;

import com.yue.admin.AdminApiException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Component
public class DubbingVideoStorage {
    static final long MAX_BYTES = 200L * 1024 * 1024;
    static final long MAX_DURATION_MS = 5L * 60 * 1000;

    private final Path root;
    private final DubbingVideoProbe probe;

    public DubbingVideoStorage(@Value("${admin.dubbing-video-dir:./data/dubbing-videos}") String directory,
                               DubbingVideoProbe probe) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.probe = probe;
    }

    public StoredVideo store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new AdminApiException(400, "请选择 MP4 视频");
        if (file.getSize() > MAX_BYTES) throw new AdminApiException(413, "视频不能超过 200 MiB");
        String original = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!original.endsWith(".mp4")) throw new AdminApiException(415, "仅支持 MP4 视频");

        Path temporary = null;
        Path destination = null;
        try {
            Files.createDirectories(root);
            String token = UUID.randomUUID().toString();
            temporary = root.resolve(token + ".upload");
            destination = root.resolve(token + ".mp4");
            long size = copyBounded(file, temporary);
            requireMp4Magic(temporary);
            DubbingVideoProbe.Metadata metadata = probe.probe(temporary);
            validate(metadata);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;
            return new StoredVideo(destination.getFileName().toString(), "video/mp4", size,
                    metadata.durationMs(), metadata.videoCodec(), metadata.audioCodec());
        } catch (AdminApiException exception) {
            deleteQuietly(temporary);
            deleteQuietly(destination);
            throw exception;
        } catch (Exception exception) {
            deleteQuietly(temporary);
            deleteQuietly(destination);
            throw new AdminApiException(500, "视频保存失败");
        }
    }

    public Path path(String fileName) {
        if (fileName == null || fileName.isBlank()) throw new AdminApiException(404, "视频不存在");
        Path resolved = root.resolve(fileName).normalize();
        if (!root.equals(resolved.getParent())) throw new AdminApiException(404, "视频不存在");
        return resolved;
    }

    public void remove(String fileName) {
        try {
            Files.deleteIfExists(path(fileName));
        } catch (Exception exception) {
            LoggerFactory.getLogger(getClass()).warn("视频文件清理失败: {}", fileName);
        }
    }

    private long copyBounded(MultipartFile file, Path temporary) throws Exception {
        long total = 0;
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) throw new AdminApiException(413, "视频不能超过 200 MiB");
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) throw new AdminApiException(400, "请选择 MP4 视频");
        return total;
    }

    private void requireMp4Magic(Path file) throws Exception {
        byte[] header;
        try (InputStream input = Files.newInputStream(file)) { header = input.readNBytes(12); }
        if (header.length < 12 || !"ftyp".equals(new String(header, 4, 4, StandardCharsets.US_ASCII))) {
            throw new AdminApiException(415, "文件内容不是有效 MP4 视频");
        }
    }

    private void validate(DubbingVideoProbe.Metadata metadata) {
        String container = Objects.toString(metadata.container(), "").toLowerCase(Locale.ROOT);
        if (!container.contains("mp4") || !"h264".equalsIgnoreCase(metadata.videoCodec())
                || !"aac".equalsIgnoreCase(metadata.audioCodec())) {
            throw new AdminApiException(415, "视频必须为 MP4、H.264 视频和 AAC 音频");
        }
        if (metadata.durationMs() <= 0 || metadata.durationMs() > MAX_DURATION_MS) {
            throw new AdminApiException(415, "视频时长必须在 5 分钟以内");
        }
    }

    private void deleteQuietly(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); }
        catch (Exception exception) { LoggerFactory.getLogger(getClass()).warn("临时视频清理失败: {}", file.getFileName()); }
    }

    public record StoredVideo(String fileName, String contentType, long sizeBytes, long durationMs,
                              String videoCodec, String audioCodec) {}
}
