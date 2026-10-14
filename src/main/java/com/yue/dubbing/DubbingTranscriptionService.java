package com.yue.dubbing;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class DubbingTranscriptionService implements AutoCloseable {
    public record Job(String id, String status, List<TencentAsrGateway.Cue> cues, String error) {}
    private static class Entry {
        final long owner; final Instant created = Instant.now(); volatile Job job;
        Entry(long owner, String id) { this.owner = owner; job = new Job(id, "preparing", List.of(), null); }
    }
    private final Map<String, Entry> jobs = new ConcurrentHashMap<>();
    private final Semaphore slot = new Semaphore(1);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "dubbing-asr"); t.setDaemon(true); return t; });
    private final DubbingAsrProperties properties;
    private final TencentAsrGateway gateway;
    private final AsrAudioExtractor extractor;
    private final AsrBudget budget;
    public DubbingTranscriptionService(DubbingAsrProperties properties, TencentAsrGateway gateway, AsrAudioExtractor extractor, AsrBudget budget) {
        this.properties = properties; this.gateway = gateway; this.extractor = extractor; this.budget = budget;
    }
    public Job submit(long owner, MultipartFile file) throws Exception {
        if (!properties.isEnabled()) throw new IllegalStateException("云端粤语识别尚未开通，请先导入 SRT 或手动添加台词");
        if (file.isEmpty() || file.getSize() > 200L * 1024 * 1024) throw new IllegalArgumentException("视频必须非空且不超过 200 MB");
        if (!slot.tryAcquire()) throw new IllegalStateException("识别服务正忙，请稍后重试");
        Path video = null;
        try {
            jobs.entrySet().removeIf(e -> e.getValue().created.isBefore(Instant.now().minusSeconds(3600)));
            if (jobs.size() >= 100) throw new IllegalStateException("识别任务过多，请稍后重试");
            video = Files.createTempFile("yue-asr-video-", ".media");
            file.transferTo(video);
            String id = UUID.randomUUID().toString();
            var entry = new Entry(owner, id); jobs.put(id, entry);
            Path input = video;
            worker.submit(() -> process(entry, input));
            return entry.job;
        } catch (Exception e) {
            if (video != null) Files.deleteIfExists(video);
            slot.release(); throw e;
        }
    }
    public Job get(long owner, String id) {
        var entry = jobs.get(id);
        if (entry == null || entry.owner != owner || entry.created.isBefore(Instant.now().minusSeconds(3600)))
            throw new NoSuchElementException("识别任务不存在或已过期，请勿立即重复提交");
        return entry.job;
    }
    public boolean available() { return properties.isEnabled() && slot.availablePermits() > 0; }
    private void process(Entry entry, Path input) {
        String id = entry.job.id();
        try {
            var audio = extractor.extract(input);
            budget.reserve(audio.seconds());
            long remoteId = gateway.submit(audio.bytes());
            entry.job = new Job(id, "recognizing", List.of(), null);
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
            while (System.nanoTime() < deadline) {
                TencentAsrGateway.Result result;
                try { result = gateway.poll(remoteId); }
                catch (com.tencentcloudapi.common.exception.TencentCloudSDKException transientFailure) {
                    // Queries are read-only; the paid task creation above is never repeated.
                    Thread.sleep(5000);
                    continue;
                }
                if (result.status().equals("succeeded")) { entry.job = new Job(id, "succeeded", result.cues(), null); return; }
                if (result.status().equals("failed")) throw new IllegalStateException("云端识别失败，请确认音频包含清晰粤语");
                Thread.sleep(5000);
            }
            throw new IllegalStateException("识别等待超时，任务可能仍在云端处理，请勿立即重复提交");
        } catch (IllegalArgumentException | IllegalStateException e) {
            entry.job = new Job(id, "failed", List.of(), e.getMessage());
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            entry.job = new Job(id, "failed", List.of(), "识别暂时失败，原台词已保留；提交结果可能不明，请勿立即重复提交");
        } finally {
            try { Files.deleteIfExists(input); } catch (Exception ignored) { }
            slot.release();
        }
    }
    @Override @jakarta.annotation.PreDestroy public void close() { worker.shutdownNow(); }
}
