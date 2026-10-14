package com.yue.controller;

import com.yue.dubbing.DubbingTranscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/dubbing/transcriptions")
public class DubbingTranscriptionController {
    private final DubbingTranscriptionService service;
    public DubbingTranscriptionController(DubbingTranscriptionService service) { this.service = service; }
    @PostMapping public ResponseEntity<?> submit(Authentication auth, @RequestPart("file") MultipartFile file) {
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) return ResponseEntity.status(401).build();
        try { return ResponseEntity.accepted().body(service.submit(userId, file)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        catch (IllegalStateException e) { return ResponseEntity.status(503).body(Map.of("error", e.getMessage())); }
        catch (Exception e) { return ResponseEntity.status(503).body(Map.of("error", "视频接收失败，请稍后重试")); }
    }
    @GetMapping("/{id}") public ResponseEntity<?> status(Authentication auth, @PathVariable String id) {
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) return ResponseEntity.status(401).build();
        try { return ResponseEntity.ok(service.get(userId, id)); }
        catch (NoSuchElementException e) { return ResponseEntity.status(404).body(Map.of("error", e.getMessage())); }
    }
}
