package com.yue.repository;
import com.yue.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.*;
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog,Long>, JpaSpecificationExecutor<AdminAuditLog> {}
