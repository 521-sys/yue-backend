package com.yue.dubbingmaterial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yue.admin.AdminApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DubbingVideoProbe {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String executable;
    private final Duration timeout;
    private final ProcessStarter processStarter;

    public DubbingVideoProbe(@Value("${app.dubbing-asr.ffprobe:ffprobe}") String executable) {
        this(executable, Duration.ofSeconds(10), command -> new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start());
    }

    DubbingVideoProbe(String executable, Duration timeout, ProcessStarter processStarter) {
        this.executable = executable;
        this.timeout = timeout;
        this.processStarter = processStarter;
    }

    public Metadata probe(Path path) {
        Process process = null;
        try {
            List<String> command = new ArrayList<>(List.of(executable, "-v", "error", "-show_entries",
                    "format=format_name,duration:stream=codec_type,codec_name", "-of", "json", path.toString()));
            process = processStarter.start(command);
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new AdminApiException(415, "视频解析超时");
            }
            if (process.exitValue() != 0) throw new AdminApiException(415, "视频内容无法解析");

            var root = JSON.readTree(process.getInputStream());
            var format = root.path("format");
            String container = text(format.path("format_name"));
            double seconds = format.path("duration").asDouble(-1);
            String videoCodec = null;
            String audioCodec = null;
            for (var stream : root.path("streams")) {
                String type = text(stream.path("codec_type"));
                if (videoCodec == null && "video".equals(type)) videoCodec = text(stream.path("codec_name"));
                if (audioCodec == null && "audio".equals(type)) audioCodec = text(stream.path("codec_name"));
            }
            long durationMs = seconds <= 0 ? -1 : Math.round(seconds * 1000);
            return new Metadata(durationMs, container, videoCodec, audioCodec);
        } catch (AdminApiException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new AdminApiException(500, "视频解析被中断");
        } catch (Exception exception) {
            if (process != null) process.destroyForcibly();
            throw new AdminApiException(500, "视频解析服务不可用");
        }
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    public record Metadata(long durationMs, String container, String videoCodec, String audioCodec) {}

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws Exception;
    }
}
