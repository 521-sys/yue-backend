package com.yue.service;

import com.yue.dto.*;
import com.yue.entity.*;
import com.yue.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SceneService {
    private final SceneRepository scenes;
    private final SceneLineRepository lines;
    private final UserSceneFavoriteRepository favorites;
    private final AudioResourceRepository audio;
    public SceneService(SceneRepository scenes, SceneLineRepository lines, UserSceneFavoriteRepository favorites, AudioResourceRepository audio) { this.scenes=scenes; this.lines=lines; this.favorites=favorites; this.audio=audio; }

    @Transactional(readOnly=true)
    public List<SceneResponse> list(Long userId) { return scenes.findAllByOrderByCreatedAtDesc().stream().filter(s -> visible(s,userId)).sorted(java.util.Comparator.comparingInt(Scene::getSortOrder)).map(s -> response(s,userId)).toList(); }
    @Transactional(readOnly=true)
    public SceneResponse get(Long userId, Long id) { Scene s=find(id); requireVisible(s,userId); return response(s,userId); }
    @Transactional
    public SceneResponse create(Long userId, SceneCreateRequest req) {
        require(req);
        req.lines().stream().filter(Objects::nonNull).map(SceneLineRequest::audioKey).filter(Objects::nonNull).filter(k->k.startsWith("upload:")).distinct().sorted()
            .forEach(k->audio.lockByKey(k).orElseThrow(()->new com.yue.admin.AdminApiException(400,"音频不存在")));
        for(var line:req.lines()) if(line!=null&&line.audioKey()!=null&&!line.audioKey().isBlank()&&!line.audioKey().startsWith("upload:")) {
            if(!line.audioKey().matches("[a-zA-Z0-9_-]+\\.(mp3|wav)")||!new org.springframework.core.io.ClassPathResource("static/audio/"+line.audioKey()).exists())throw new com.yue.admin.AdminApiException(400,"静态音频不存在");
        }
        if (scenes.findAllByOrderByCreatedAtDesc().stream().anyMatch(s -> s.getCode().equals(req.code()))) throw new IllegalArgumentException("场景 code 已存在");
        Scene s = scenes.save(Scene.builder().code(req.code().trim()).title(req.title().trim()).place(req.place().trim()).emoji(req.emoji()).roleA(req.roleA().trim()).roleB(req.roleB().trim()).ownerUserId(userId).builtin(false).build());
        int i=1; for (SceneLineRequest r : req.lines()) lines.save(toLine(s.getId(), r, i++));
        return response(s,userId);
    }
    @Transactional
    public void delete(Long userId, Long id) { Scene s=find(id); if (s.isBuiltin() || !Objects.equals(s.getOwnerUserId(),userId)) throw new IllegalArgumentException("只能删除自己创建的场景"); favorites.deleteAll(favorites.findByUserIdOrderByCreatedAtDesc(userId).stream().filter(f->f.getSceneId().equals(id)).toList()); lines.deleteBySceneId(id); scenes.delete(s); }
    @Transactional
    public void favorite(Long userId, Long id) { requireVisible(find(id),userId); if (!favorites.existsByUserIdAndSceneId(userId,id)) favorites.save(UserSceneFavorite.builder().userId(userId).sceneId(id).build()); }
    @Transactional
    public void unfavorite(Long userId, Long id) { favorites.deleteByUserIdAndSceneId(userId,id); }
    @Transactional(readOnly=true)
    public List<SceneResponse> favorites(Long userId) { return favorites.findByUserIdOrderByCreatedAtDesc(userId).stream().map(f->scenes.findById(f.getSceneId()).filter(s->visible(s,userId)).map(s->response(s,userId)).orElse(null)).filter(Objects::nonNull).toList(); }
    private SceneResponse response(Scene s, Long userId) { return SceneResponse.of(s, userId!=null && favorites.existsByUserIdAndSceneId(userId,s.getId()), lines.findBySceneIdOrderBySequenceNoAsc(s.getId())); }
    private Scene find(Long id) { return scenes.findById(id).orElseThrow(() -> new com.yue.admin.AdminApiException(404,"场景不存在")); }
    private static SceneLine toLine(Long id, SceneLineRequest r, int fallback) { if(r==null||r.cantoneseText()==null||r.mandarinText()==null) throw new IllegalArgumentException("台词内容不能为空"); int n=r.sequenceNo()==null?fallback:r.sequenceNo(); if(!Set.of("A","B").contains(r.speaker())) throw new IllegalArgumentException("speaker 必须是 A 或 B"); return SceneLine.builder().sceneId(id).sequenceNo(n).speaker(r.speaker()).cantoneseText(r.cantoneseText().trim()).mandarinText(r.mandarinText().trim()).audioKey(r.audioKey()).build(); }
    private static void require(SceneCreateRequest r) { if(r==null||blank(r.code())||blank(r.title())||blank(r.place())||blank(r.roleA())||blank(r.roleB())||r.lines()==null||r.lines().isEmpty()) throw new IllegalArgumentException("场景资料和至少一条台词不能为空"); if(r.code().length()>80||r.title().length()>80) throw new IllegalArgumentException("场景文本过长"); }
    private boolean visible(Scene s,Long userId){return s.isBuiltin()?"PUBLISHED".equals(s.getStatus()):userId!=null&&java.util.Objects.equals(s.getOwnerUserId(),userId);}
    private void requireVisible(Scene s,Long userId){if(!visible(s,userId))throw new com.yue.admin.AdminApiException(404,"场景不存在或已下架");}
    private static boolean blank(String s){return s==null||s.isBlank();}
}
