package com.yue.controller;

import com.yue.dto.*;
import com.yue.service.SceneService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/scenes")
public class SceneController {
    private final SceneService service;
    public SceneController(SceneService service) { this.service=service; }
    @GetMapping public List<SceneResponse> list(@AuthenticationPrincipal Long userId) { return service.list(userId); }
    @GetMapping("/{id}") public SceneResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) { return service.get(userId,id); }
    @PostMapping public SceneResponse create(@AuthenticationPrincipal Long userId, @RequestBody SceneCreateRequest req) { return service.create(userId,req); }
    @DeleteMapping("/{id}") public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) { service.delete(userId,id); }
    @GetMapping("/favorites") public List<SceneResponse> favorites(@AuthenticationPrincipal Long userId) { return service.favorites(userId); }
    @PostMapping("/{id}/favorite") public void favorite(@AuthenticationPrincipal Long userId, @PathVariable Long id) { service.favorite(userId,id); }
    @DeleteMapping("/{id}/favorite") public void unfavorite(@AuthenticationPrincipal Long userId, @PathVariable Long id) { service.unfavorite(userId,id); }
}
