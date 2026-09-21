package com.yue.dto;
import java.util.List;
public record SceneCreateRequest(String code, String title, String place, String emoji, String roleA, String roleB, List<SceneLineRequest> lines) {}
