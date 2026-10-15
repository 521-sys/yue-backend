package com.yue.admin;
import static com.yue.admin.AdminDtos.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
@RestController @RequestMapping("/api/admin/scenes")
public class AdminSceneController {
 private final AdminSceneService service;public AdminSceneController(AdminSceneService s){service=s;}
 @GetMapping public Object list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return service.list(q,category,status,page,pageSize);}
 @GetMapping("/{id}") public SceneDetail get(@PathVariable long id){return service.get(id);}
 @PostMapping public SceneDetail create(Principal p,@RequestBody SceneSaveRequest r){return service.create(p.getName(),r);}
 @PutMapping("/{id}") public SceneDetail update(Principal p,@PathVariable long id,@RequestBody SceneSaveRequest r){return service.update(p.getName(),id,r);}
 @PutMapping("/{id}/status") public SceneDetail status(Principal p,@PathVariable long id,@RequestBody StatusRequest r){return service.setStatus(p.getName(),id,r.status(),r.version());}
 @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void delete(Principal p,@PathVariable long id,@RequestParam Long version){service.delete(p.getName(),id,version);}
}
