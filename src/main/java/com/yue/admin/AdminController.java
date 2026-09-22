package com.yue.admin;
import com.yue.entity.*; import com.yue.repository.*; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/admin") public class AdminController { private final AdminAuthService auth; private final UserRepository users; private final SceneRepository scenes; private final SceneLineRepository lines;
 public AdminController(AdminAuthService a,UserRepository u,SceneRepository s,SceneLineRepository l){auth=a;users=u;scenes=s;lines=l;}
 @PostMapping("/auth/login") public AdminLoginResponse login(@RequestBody AdminLoginRequest r){return auth.login(r.username(),r.password());}
 @GetMapping("/dashboard") public Map<String,Object> dashboard(){return Map.of("users",users.count(),"scenes",scenes.count());}
 @GetMapping("/users") public Map<String,Object> list(){return Map.of("items",users.findAll().stream().map(this::safe).toList());}
 @GetMapping("/scenes") public Map<String,Object> sceneList(){return Map.of("items",scenes.findAllByOrderByCreatedAtDesc());}
 @GetMapping("/scenes/{id}") public Map<String,Object> scene(@PathVariable Long id){Scene s=scenes.findById(id).orElseThrow(); return Map.of("scene",s,"lines",lines.findBySceneIdOrderBySequenceNoAsc(id));}
 @PostMapping("/scenes") public Scene create(@RequestBody Scene s){s.setId(null);s.setBuiltin(false);return scenes.save(s);}
 @PutMapping("/scenes/{id}") public Scene update(@PathVariable Long id,@RequestBody Scene in){Scene s=scenes.findById(id).orElseThrow();s.setTitle(in.getTitle());s.setPlace(in.getPlace());s.setEmoji(in.getEmoji());s.setRoleA(in.getRoleA());s.setRoleB(in.getRoleB());return scenes.save(s);}
 @DeleteMapping("/scenes/{id}") public void delete(@PathVariable Long id){if(!scenes.existsById(id))throw new NoSuchElementException();lines.deleteBySceneId(id);scenes.deleteById(id);}
 private Map<String,Object> safe(User u){Map<String,Object> m=new LinkedHashMap<>();m.put("id",u.getId());m.put("username",u.getUsername());m.put("nickname",u.getNickname()==null?"":u.getNickname());m.put("phone",mask(u.getPhone()));m.put("createdAt",u.getCreatedAt());return m;} private String mask(String s){return s==null||s.length()<7?s:s.substring(0,3)+"****"+s.substring(s.length()-4);} }
