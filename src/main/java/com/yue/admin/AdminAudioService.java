package com.yue.admin;
import com.yue.entity.*;
import com.yue.repository.*;
import static com.yue.admin.AdminDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.util.*;
@Service @Transactional
public class AdminAudioService {
 private final AudioResourceRepository repo;private final SceneLineRepository lines;private final AudioStorage storage;private final AdminAuditService audit;
 public AdminAudioService(AudioResourceRepository r,SceneLineRepository l,AudioStorage s,AdminAuditService a){repo=r;lines=l;storage=s;audit=a;}
 public AudioItem upload(String actor,MultipartFile file){
  var stored=storage.store(file);cleanup(stored.fileName(),false);
  var a=new AudioResource();a.setAudioKey("upload:"+UUID.randomUUID());set(a,file,stored);a=repo.saveAndFlush(a);
  audit.record(actor,"UPLOAD","AUDIO",a.getId(),a.getName());return item(a);
 }
 public AudioItem replace(String actor,long id,Long version,MultipartFile file){
  var a=locked(id);version(a,version);String old=a.getFileName();
  var stored=storage.store(file);cleanup(stored.fileName(),false);cleanup(old,true);
  set(a,file,stored);repo.saveAndFlush(a);audit.record(actor,"REPLACE","AUDIO",id,a.getName());return item(a);
 }
 public void delete(String actor,long id,Long version){
  var a=locked(id);version(a,version);if(!lines.currentReferences(a.getAudioKey()).isEmpty())throw new AdminApiException(409,"音频正在被台词使用");
  repo.delete(a);cleanup(a.getFileName(),true);audit.record(actor,"DELETE","AUDIO",id,a.getName());
 }
 @Transactional(readOnly=true) public PageResult<AudioItem> list(String q,int page,int size){
  AdminDtos.page(page,size,org.springframework.data.domain.Sort.unsorted());
  List<AudioItem> items=new ArrayList<>(repo.findAll().stream().map(this::item).toList());
  try{for(var r:new PathMatchingResourcePatternResolver().getResources("classpath*:static/audio/*")){
   String n=r.getFilename();if(n!=null&&n.matches("[a-zA-Z0-9_-]+\\.(mp3|wav)"))items.add(new AudioItem("static:"+n,n,n,"/audio/"+n,r.contentLength(),n.endsWith(".wav")?"audio/wav":"audio/mpeg",lines.countByAudioKey(n),true,0L));
  }}catch(java.io.IOException e){throw new AdminApiException(500,"无法读取静态音频目录");}
  var filtered=items.stream().filter(a->a.name().toLowerCase().contains(q.toLowerCase())).sorted(Comparator.comparing(AudioItem::id)).toList();
  int start=(int)Math.min((long)(page-1)*size,filtered.size());return new PageResult<>(filtered.subList(start,Math.min(start+size,filtered.size())),filtered.size(),page,size);
 }
 @Transactional(readOnly=true) public Object references(String key){return lines.findByAudioKey(key).stream().map(l->Map.of("sceneId",l.getSceneId(),"sequenceNo",l.getSequenceNo(),"text",l.getCantoneseText())).toList();}
 public record AudioSnapshot(byte[] bytes,String contentType) {}
 public AudioSnapshot resource(String opaque){
  var a=repo.readLockedByKey("upload:"+opaque).orElseThrow(()->new AdminApiException(404,"音频不存在"));
  // Capture the bounded file while holding the lock so replacement cannot remove it mid-read.
  try{return new AudioSnapshot(java.nio.file.Files.readAllBytes(storage.path(a.getFileName())),a.getContentType());}
  catch(java.io.IOException e){throw new AdminApiException(404,"音频文件不存在");}
 }
 private AudioResource locked(long id){return repo.lockById(id).orElseThrow(()->new AdminApiException(404,"音频不存在"));}
 private void version(AudioResource a,Long v){if(v==null||!v.equals(a.getVersion()))throw new AdminApiException(409,"音频已更新，请刷新");}
 private void set(AudioResource a,MultipartFile f,AudioStorage.Stored s){
  String n=Objects.toString(f.getOriginalFilename(),"音频").replace('\\','/');n=n.substring(n.lastIndexOf('/')+1);if(n.length()>200)n=n.substring(0,200);
  a.setName(n);a.setFileName(s.fileName());a.setContentType(s.contentType());a.setSize(s.size());
 }
 private AudioItem item(AudioResource a){return new AudioItem("upload:"+a.getId(),a.getName(),a.getAudioKey(),"/audio/uploads/"+a.getAudioKey().substring(7)+"?v="+a.getVersion(),a.getSize(),a.getContentType(),lines.countByAudioKey(a.getAudioKey()),false,a.getVersion());}
 private void cleanup(String file,boolean onCommit){
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCompletion(int status){if(onCommit?status==STATUS_COMMITTED:status!=STATUS_COMMITTED)storage.remove(file);}
  });
 }
}
