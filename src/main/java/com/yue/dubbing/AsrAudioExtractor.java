package com.yue.dubbing;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AsrAudioExtractor {
    public record Audio(byte[] bytes, long seconds) {}
    private final DubbingAsrProperties properties;
    public AsrAudioExtractor(DubbingAsrProperties properties) { this.properties = properties; }
    public Audio extract(Path video) throws Exception {
        Path directory = Files.createTempDirectory("yue-asr-audio-");
        Path probe = directory.resolve("duration.txt");
        Path audio = directory.resolve("audio.mp3");
        try {
            run(List.of(properties.getFfprobe(), "-v", "error", "-protocol_whitelist", "file", "-format_whitelist", "mov,matroska,webm,avi,flv,ogg,wav,mp3", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", video.toString()), probe, 20);
            double duration = Double.parseDouble(Files.readString(probe).trim());
            if (!Double.isFinite(duration) || duration <= 0 || duration > 300)
                throw new IllegalArgumentException("请导入 5 分钟以内的视频");
            run(List.of(properties.getFfmpeg(), "-nostdin", "-v", "error", "-threads", "1", "-protocol_whitelist", "file", "-format_whitelist", "mov,matroska,webm,avi,flv,ogg,wav,mp3", "-i", video.toString(), "-map", "0:a:0", "-vn", "-t", "300", "-ac", "1", "-ar", "16000", "-codec:a", "libmp3lame", "-b:a", "64k", "-threads", "1", "-y", audio.toString()), null, 90);
            if (Files.size(audio) > 5 * 1024 * 1024) throw new IllegalArgumentException("提取音频超过限制");
            run(List.of(properties.getFfprobe(), "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", audio.toString()), probe, 20);
            double extractedDuration = Double.parseDouble(Files.readString(probe).trim());
            if (!Double.isFinite(extractedDuration) || extractedDuration <= 0 || extractedDuration > 301)
                throw new IllegalArgumentException("提取音频时长无效");
            return new Audio(Files.readAllBytes(audio), (long)Math.ceil(extractedDuration));
        } finally {
            Files.deleteIfExists(audio); Files.deleteIfExists(probe); Files.deleteIfExists(directory);
        }
    }
    private void run(List<String> command, Path output, int timeout) throws Exception {
        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.redirectOutput(output == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(output.toFile()));
        var process = builder.start();
        try {
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) throw new IllegalArgumentException("视频处理超时，请缩短视频后重试");
            if (process.exitValue() != 0) throw new IllegalArgumentException("视频无法读取或没有音轨，请使用 MP4 等常见视频格式");
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
