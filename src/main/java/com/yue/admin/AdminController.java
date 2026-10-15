package com.yue.admin;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
@RestController @RequestMapping("/api/admin")
public class AdminController {
 private final AdminAuthService auth;private final AdminQueryService query;
 public AdminController(AdminAuthService auth,AdminQueryService query){this.auth=auth;this.query=query;}
 @PostMapping("/auth/login") public AdminLoginResponse login(@RequestBody AdminLoginRequest r){return auth.login(r.username(),r.password());}
 @GetMapping("/dashboard") public Object dashboard(){return query.dashboard();}
 @GetMapping("/users") public Object users(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return query.users(q,page,pageSize);}
 @GetMapping("/users/{id}") public Object user(@PathVariable long id){return query.user(id);}
 @GetMapping("/audit-logs") public Object logs(@RequestParam(defaultValue="")String action,@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return query.audit(action,from,to,page,pageSize);}
}
