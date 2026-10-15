package com.yue.admin;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
@RestController @RequestMapping("/api/admin/audio")
public class AdminAudioController {
 private final AdminAudioService service;public AdminAudioController(AdminAudioService s){service=s;}
 @GetMapping public Object list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return service.list(q,page,pageSize);}
 @GetMapping("/references") public Object references(@RequestParam String key){return service.references(key);}
 @PostMapping public Object upload(Principal p,@RequestParam MultipartFile file){return service.upload(p.getName(),file);}
 @PutMapping("/{id}") public Object replace(Principal p,@PathVariable long id,@RequestParam Long version,@RequestParam MultipartFile file){return service.replace(p.getName(),id,version,file);}
 @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void delete(Principal p,@PathVariable long id,@RequestParam Long version){service.delete(p.getName(),id,version);}
}
