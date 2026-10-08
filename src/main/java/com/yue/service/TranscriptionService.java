package com.yue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/** Server-only Paraformer credentials; accepts 16kHz mono PCM16, at most 30 seconds. */
@Service
public class TranscriptionService {
    @Value("${DASHSCOPE_API_KEY:}") private String apiKey = "";
    private final ObjectMapper json = new ObjectMapper();
    private final Semaphore capacity = new Semaphore(2);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public String transcribe(MultipartFile audio) throws Exception {
        if (audio == null || audio.getSize() < 3200 || audio.getSize() > 960000 || audio.getSize() % 2 != 0)
            throw new IllegalArgumentException("请录制 0.1～30 秒语音");
        if (!"application/octet-stream".equals(audio.getContentType()))
            throw new IllegalArgumentException("录音格式错误，请刷新页面重试");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("语音识别服务尚未配置");
        if (!capacity.tryAcquire()) throw new IllegalStateException("语音识别繁忙，请稍后再试");
        WebSocket ws = null;
        try {
            byte[] bytes = audio.getBytes();
            var started = new CompletableFuture<Void>();
            var finished = new CompletableFuture<String>();
            StringBuilder text = new StringBuilder();
            String task = UUID.randomUUID().toString();
            ws = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiKey.trim())
                .buildAsync(URI.create("wss://dashscope.aliyuncs.com/api-ws/v1/inference"), new WebSocket.Listener() {
                    private final StringBuilder frame = new StringBuilder();
                    public void onOpen(WebSocket socket) { socket.request(1); }
                    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                        frame.append(data);
                        if (last) {
                            try {
                                var event = json.readTree(frame.toString());
                                switch (event.path("header").path("event").asText()) {
                                    case "task-started" -> started.complete(null);
                                    case "result-generated" -> {
                                        var sentence = event.path("payload").path("output").path("sentence");
                                        if (sentence.path("sentence_end").asBoolean() && !sentence.path("heartbeat").asBoolean())
                                            text.append(sentence.path("text").asText(""));
                                    }
                                    case "task-finished" -> finished.complete(text.toString().trim());
                                    case "task-failed" -> {
                                        String code = event.path("header").path("error_code").asText();
                                        var failure = new IllegalStateException(code.contains("Quota") || code.contains("Allocation")
                                            ? "语音识别额度已用尽，请联系管理员" : "云端语音识别失败，请稍后重试");
                                        started.completeExceptionally(failure); finished.completeExceptionally(failure);
                                    }
                                }
                            } catch (Exception e) {
                                started.completeExceptionally(e); finished.completeExceptionally(e);
                            } finally { frame.setLength(0); }
                        }
                        socket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }
                    public void onError(WebSocket socket, Throwable error) {
                        started.completeExceptionally(error); finished.completeExceptionally(error);
                    }
                    public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
                        var failure = new IllegalStateException("语音识别连接中断，请重试");
                        started.completeExceptionally(failure); finished.completeExceptionally(failure);
                        return CompletableFuture.completedFuture(null);
                    }
                }).get(12, TimeUnit.SECONDS);
            ws.sendText(json.writeValueAsString(Map.of("header", Map.of("action", "run-task", "task_id", task, "streaming", "duplex"),
                "payload", Map.of("task_group", "audio", "task", "asr", "function", "recognition", "model", "paraformer-realtime-v2",
                "parameters", Map.of("format", "pcm", "sample_rate", 16000, "language_hints", new String[]{"yue", "zh"}), "input", Map.of()))), true).get(5, TimeUnit.SECONDS);
            started.get(12, TimeUnit.SECONDS);
            for (int i = 0; i < bytes.length; i += 3200) {
                if (finished.isDone()) { finished.get(); break; }
                ws.sendBinary(ByteBuffer.wrap(bytes, i, Math.min(3200, bytes.length - i)), true).get(5, TimeUnit.SECONDS);
                Thread.sleep(100);
            }
            ws.sendText(json.writeValueAsString(Map.of("header", Map.of("action", "finish-task", "task_id", task, "streaming", "duplex"),
                "payload", Map.of("input", Map.of()))), true).get(5, TimeUnit.SECONDS);
            String result = finished.get(15, TimeUnit.SECONDS);
            if (result.isBlank()) throw new IllegalStateException("没有听清，请靠近麦克风再试一次");
            return result;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("无法连接语音识别服务，请稍后重试");
        } catch (TimeoutException e) {
            throw new IllegalStateException("语音识别超时，请重试");
        } finally {
            if (ws != null) ws.abort();
            capacity.release();
        }
    }
}
