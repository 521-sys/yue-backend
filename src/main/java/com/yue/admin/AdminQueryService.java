package com.yue.admin;
import com.yue.entity.*;
import com.yue.repository.*;
import static com.yue.admin.AdminDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.time.Instant;
@Service @Transactional(readOnly=true)
public class AdminQueryService {
 private final UserRepository users;private final LearnStateRepository learning;private final SceneRepository scenes;private final SceneLineRepository lines;private final UserSceneFavoriteRepository favorites;private final AdminAuditLogRepository logs;private final AudioResourceRepository audio;private final ObjectMapper mapper;
 public AdminQueryService(UserRepository u,LearnStateRepository l,SceneRepository s,SceneLineRepository sl,UserSceneFavoriteRepository f,AdminAuditLogRepository logs,AudioResourceRepository a,ObjectMapper m){users=u;learning=l;scenes=s;lines=sl;favorites=f;this.logs=logs;audio=a;mapper=m;}
 public PageResult<Map<String,Object>> users(String q,int page,int size){
  Specification<User> spec=(r,x,b)->q.isBlank()?b.conjunction():b.or(b.like(b.lower(r.get("username")),"%"+q.toLowerCase()+"%"),b.like(b.lower(r.get("nickname")),"%"+q.toLowerCase()+"%"));
  return PageResult.of(users.findAll(spec,AdminDtos.page(page,size,Sort.by("id").descending())).map(this::safe));
 }
 public Map<String,Object> user(long id){
  User user=users.findById(id).orElseThrow(()->new AdminApiException(404,"用户不存在"));
  Map<String,Object> result=new LinkedHashMap<>();result.put("user",safe(user));
  learning.findByUserId(id).ifPresent(l->{result.put("lastSyncedAt",l.getUpdatedAt());try{
   JsonNode n=mapper.readTree(l.getStateJson());Map<String,Integer> progress=new LinkedHashMap<>();
   for(String key:List.of("learned","stuck","reviewed"))if(n.has(key)&&n.get(key).isArray())progress.put(key+"Count",n.get(key).size());
   if(!progress.isEmpty())result.put("progress",progress);
  }catch(Exception ignored){}});
  result.put("favorites",favorites.findByUserIdOrderByCreatedAtDesc(id).stream().map(f->scenes.findById(f.getSceneId()).map(s->Map.of("id",s.getId(),"title",s.getTitle(),"status",s.getStatus())).orElse(null)).filter(Objects::nonNull).toList());return result;
 }
 public Map<String,Object> dashboard(){
  long missing=scenes.findAllByOrderByCreatedAtDesc().stream().filter(Scene::isBuiltin).flatMap(s->lines.findBySceneIdOrderBySequenceNoAsc(s.getId()).stream()).filter(l->!audioExists(l.getAudioKey())).count();
  return Map.of("users",users.count(),"scenes",scenes.countByBuiltinTrue(),"published",scenes.countByBuiltinTrueAndStatus("PUBLISHED"),"audio",audio.count()+staticAudioCount(),"missingAudio",missing,"recent",logs.findAll(org.springframework.data.domain.PageRequest.of(0,10,Sort.by("id").descending())).getContent());
 }
 private boolean audioExists(String key){return key!=null&&!key.isBlank()&&(key.startsWith("upload:")?audio.findByAudioKey(key).isPresent():key.matches("[a-zA-Z0-9_-]+\\.(mp3|wav)")&&new org.springframework.core.io.ClassPathResource("static/audio/"+key).exists());}
 private int staticAudioCount(){try{return new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources("classpath*:static/audio/*").length;}catch(Exception e){return 0;}}
 public PageResult<AdminAuditLog> audit(String action,Instant from,Instant to,int page,int size){
  if(from!=null&&to!=null&&from.isAfter(to))throw new AdminApiException(400,"日期范围不正确");
  Specification<AdminAuditLog> spec=(r,x,b)->b.and(action.isBlank()?b.conjunction():b.equal(r.get("action"),action),from==null?b.conjunction():b.greaterThanOrEqualTo(r.get("createdAt"),from),to==null?b.conjunction():b.lessThan(r.get("createdAt"),to));
  return PageResult.of(logs.findAll(spec,AdminDtos.page(page,size,Sort.by("id").descending())));
 }
 private Map<String,Object> safe(User u){
  Map<String,Object> m=new LinkedHashMap<>();m.put("id",u.getId());m.put("username",u.getUsername());m.put("nickname",u.getNickname());m.put("createdAt",u.getCreatedAt());
  String p=u.getPhone();m.put("phone",p==null?"":p.length()<7?"***":p.substring(0,3)+"****"+p.substring(p.length()-4));return m;
 }
}
