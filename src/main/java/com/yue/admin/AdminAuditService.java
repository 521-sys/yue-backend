package com.yue.admin;
import com.yue.entity.AdminAuditLog;
import com.yue.repository.AdminAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
@Service
public class AdminAuditService {
 private final AdminAuditLogRepository logs;
 public AdminAuditService(AdminAuditLogRepository logs){this.logs=logs;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void record(String actor,String action,String type,Object id,String summary){
  var log=new AdminAuditLog();log.setActor(actor);log.setAction(action);log.setTargetType(type);
  log.setTargetId(String.valueOf(id));log.setSummary(summary.length()>500?summary.substring(0,500):summary);
  log.setCreatedAt(Instant.now());logs.save(log);
 }
}
