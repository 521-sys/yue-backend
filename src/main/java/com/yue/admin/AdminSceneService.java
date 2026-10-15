package com.yue.admin;
import com.yue.entity.*;
import com.yue.repository.*;
import static com.yue.admin.AdminDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
@Service @Transactional
public class AdminSceneService {
 private final SceneRepository scenes; private final SceneLineRepository lines;
 private final UserSceneFavoriteRepository favorites; private final AudioResourceRepository audio; private final AdminAuditService audit;
 public AdminSceneService(SceneRepository s,SceneLineRepository l,UserSceneFavoriteRepository f,AudioResourceRepository a,AdminAuditService audit){scenes=s;lines=l;favorites=f;audio=a;this.audit=audit;}
 @Transactional(readOnly=true)
 public PageResult<Scene> list(String q,String category,String status,int page,int size){
  Specification<Scene> spec=(r,x,b)->b.and(
   q.isBlank()?b.conjunction():b.or(b.like(b.lower(r.get("title")),"%"+q.toLowerCase()+"%"),b.like(b.lower(r.get("code")),"%"+q.toLowerCase()+"%")),
   category.isBlank()?b.conjunction():b.equal(r.get("category"),category),
   status.isBlank()?b.conjunction():b.equal(r.get("status"),status));
  return PageResult.of(scenes.findAll(spec,AdminDtos.page(page,size,Sort.by("sortOrder").ascending().and(Sort.by("id").descending()))));
 }
 @Transactional(readOnly=true) public SceneDetail get(long id){return detail(find(id));}
 public SceneDetail create(String actor,SceneSaveRequest r){
  validate(r);if(scenes.existsByCode(r.code().trim()))throw new AdminApiException(409,"场景编码已存在");
  Scene s=new Scene();s.setCode(r.code().trim());s.setBuiltin(true);apply(s,r);
  s=scenes.saveAndFlush(s);writeLines(s,r.lines());audit.record(actor,"CREATE","SCENE",s.getId(),s.getTitle());return detail(s);
 }
 public SceneDetail update(String actor,long id,SceneSaveRequest r){
  validate(r);Scene s=find(id);official(s);version(s,r.version());
  if(!s.getCode().equals(r.code().trim()))throw new AdminApiException(400,"场景编码不可修改");
  if("PUBLISHED".equals(s.getStatus())&&r.lines().isEmpty())throw new AdminApiException(400,"已发布场景至少需要一句台词");
  apply(s,r);s.setUpdatedAt(java.time.Instant.now());scenes.saveAndFlush(s);
  writeLines(s,r.lines());audit.record(actor,"UPDATE","SCENE",id,s.getTitle());return detail(s);
 }
 public SceneDetail setStatus(String actor,long id,String status,Long version){
  Scene s=find(id);official(s);version(s,version);
  if(!Set.of("DRAFT","PUBLISHED","OFFLINE").contains(status==null?"":status))throw new AdminApiException(400,"发布状态不正确");
  if("PUBLISHED".equals(status)&&lines.findBySceneIdOrderBySequenceNoAsc(id).isEmpty())throw new AdminApiException(400,"至少添加一句台词后才能发布");
  s.setStatus(status);s.setUpdatedAt(java.time.Instant.now());scenes.saveAndFlush(s);
  audit.record(actor,status,"SCENE",id,s.getTitle());return detail(s);
 }
 public void delete(String actor,long id,Long version){
  Scene s=find(id);official(s);version(s,version);
  if("PUBLISHED".equals(s.getStatus()))throw new AdminApiException(409,"请先下架再删除");
  favorites.deleteBySceneId(id);lines.deleteBySceneId(id);scenes.delete(s);
  audit.record(actor,"DELETE","SCENE",id,s.getTitle());
 }
 private Scene find(long id){return scenes.findById(id).orElseThrow(()->new AdminApiException(404,"场景不存在"));}
 private void official(Scene s){if(!s.isBuiltin())throw new AdminApiException(403,"用户自建场景仅供查看");}
 private void version(Scene s,Long v){if(v==null||!Objects.equals(s.getVersion(),v))throw new AdminApiException(409,"内容已更新，请重新加载后再编辑");}
 private SceneDetail detail(Scene s){return new SceneDetail(s,lines.findBySceneIdOrderBySequenceNoAsc(s.getId()));}
 private void apply(Scene s,SceneSaveRequest r){
  s.setTitle(r.title().trim());s.setPlace(r.place().trim());s.setEmoji(r.emoji());s.setRoleA(r.roleA().trim());s.setRoleB(r.roleB().trim());
  s.setCategory(r.category()==null||r.category().isBlank()?"日常生活":r.category().trim());
  s.setDifficulty(r.difficulty()==null?"初级":r.difficulty());s.setSortOrder(r.sortOrder()==null?0:r.sortOrder());
 }
 private void writeLines(Scene s,List<LineInput> entries){
  // Lock referenced upload resources in stable order; deletion acquires the same lock.
  entries.stream().map(LineInput::audioKey).filter(Objects::nonNull).filter(k->k.startsWith("upload:")).distinct().sorted()
   .forEach(k->audio.lockByKey(k).orElseThrow(()->new AdminApiException(400,"音频不存在")));
  for(var l:entries) if(l.audioKey()!=null&&!l.audioKey().isBlank()&&!l.audioKey().startsWith("upload:")){
   if(!l.audioKey().matches("[a-zA-Z0-9_-]+\\.(mp3|wav)")||!new ClassPathResource("static/audio/"+l.audioKey()).exists())throw new AdminApiException(400,"静态音频不存在");
  }
  lines.deleteBySceneId(s.getId());lines.flush();int n=1;
  for(var l:entries)lines.save(SceneLine.builder().sceneId(s.getId()).sequenceNo(n++).speaker(l.speaker()).cantoneseText(l.cantoneseText().trim()).mandarinText(l.mandarinText().trim()).jyutping(l.jyutping()).audioKey(l.audioKey()==null||l.audioKey().isBlank()?null:l.audioKey()).build());
  lines.flush();
 }
 private void validate(SceneSaveRequest r){
  if(r==null)throw new AdminApiException(400,"场景资料不能为空");
  required(r.code(),80);if(!r.code().matches("[a-zA-Z0-9_-]+"))throw new AdminApiException(400,"编码只能包含字母、数字、下划线和连字符");
  required(r.title(),80);required(r.place(),80);required(r.roleA(),40);required(r.roleB(),40);
  optional(r.emoji(),16);optional(r.category(),40);
  if(r.difficulty()!=null&&!Set.of("初级","中级","高级").contains(r.difficulty()))throw new AdminApiException(400,"难度不正确");
  if(r.lines()==null||r.lines().size()>200)throw new AdminApiException(400,"台词数量应在 0 到 200 句之间");
  for(var l:r.lines()){if(l==null||l.speaker()==null||!Set.of("A","B").contains(l.speaker()))throw new AdminApiException(400,"请选择台词角色");required(l.cantoneseText(),5000);required(l.mandarinText(),5000);optional(l.jyutping(),2000);optional(l.audioKey(),80);}
 }
 private void required(String s,int max){if(s==null||s.isBlank()||s.length()>max)throw new AdminApiException(400,"必填内容为空或长度超过 "+max+" 字符");}
 private void optional(String s,int max){if(s!=null&&s.length()>max)throw new AdminApiException(400,"内容长度超过 "+max+" 字符");}
}
