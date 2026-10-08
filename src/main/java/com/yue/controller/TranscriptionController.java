package com.yue.controller;

import com.yue.service.TranscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class TranscriptionController {
    private final TranscriptionService service;
    public TranscriptionController(TranscriptionService service) { this.service = service; }
    @PostMapping("/transcribe")
    public ResponseEntity<Map<String, String>> transcribe(@RequestPart("audio") MultipartFile audio) {
        try { return ResponseEntity.ok(Map.of("text", service.transcribe(audio))); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        catch (IllegalStateException e) { return ResponseEntity.status(503).body(Map.of("error", e.getMessage())); }
        catch (Exception e) { return ResponseEntity.status(503).body(Map.of("error", "语音识别失败，请重试")); }
    }
}
