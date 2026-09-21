package com.yue.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 粤语 TTS 合成服务。
 * <p>
 * 通过调用微软 Edge TTS 命令行（edge-tts，免费、无需 Key，音色 zh-HK-HiuGaaiNeural
 * 与前端 208 条离线音频同款「晓佳」）把文本合成为 MP3。
 * 服务器需预装：python3 + pip install edge-tts（生产建议装到虚拟环境，用 TTS_COMMAND 指定绝对路径）。
 * <p>
 * 使用 ProcessBuilder 数组传参（不经过 shell），杜绝命令注入；相同文本走内存缓存，避免重复合成。
 */
@Service
public class TtsService {

    /** 单次合成文本上限（防滥用，粤语对话单句足够） */
    public static final int MAX_TEXT_LENGTH = 200;

    @Value("${app.tts.voice:zh-HK-HiuGaaiNeural}")
    private String voice;

    @Value("${app.tts.command:edge-tts}")
    private String command;

    @Value("${app.tts.timeout-seconds:30}")
    private long timeoutSeconds;

    @Value("${app.tts.work-dir:${java.io.tmpdir}/yue-tts}")
    private String workDir;

    @Value("${app.tts.cache-max-entries:200}")
    private int cacheMaxEntries;

    /** 文本 -> MP3 字节（相同文本不重复合成） */
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    /** 合成粤语 MP3。text 需已校验（非空、长度不超 MAX_TEXT_LENGTH）。 */
    public byte[] synthesize(String text) throws IOException, InterruptedException {
        String key = text.trim();
        byte[] hit = cache.get(key);
        if (hit != null) return hit;

        Path dir = Path.of(workDir);
        Files.createDirectories(dir);
        Path out = dir.resolve("yue-tts-" + UUID.randomUUID() + ".mp3");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    command,
                    "--voice", voice,
                    "--text", key,
                    "--write-media", out.toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                throw new IOException("TTS 合成超时");
            }
            if (proc.exitValue() != 0) {
                throw new IOException("edge-tts 退出码 " + proc.exitValue());
            }
            byte[] audio = Files.readAllBytes(out);
            if (audio.length == 0) {
                throw new IOException("TTS 合成为空");
            }
            putCache(key, audio);
            return audio;
        } finally {
            Files.deleteIfExists(out);
        }
    }

    private void putCache(String key, byte[] audio) {
        if (cache.size() >= cacheMaxEntries) {
            cache.clear(); // 简单上限策略：超限即清空，避免内存无限增长
        }
        cache.put(key, audio);
    }
}
