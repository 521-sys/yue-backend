package com.yue.dto;
import com.yue.entity.Scene;
import com.yue.entity.SceneLine;
import java.util.List;
public record SceneResponse(Long id, String code, String title, String place, String emoji, String roleA, String roleB, boolean builtin, boolean favorite, List<SceneLine> lines) {
    public static SceneResponse of(Scene s, boolean favorite, List<SceneLine> lines) { return new SceneResponse(s.getId(), s.getCode(), s.getTitle(), s.getPlace(), s.getEmoji(), s.getRoleA(), s.getRoleB(), s.isBuiltin(), favorite, lines); }
}
