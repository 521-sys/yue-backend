package com.yue.controller;
import com.yue.service.SceneService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/public/scenes")
public class PublicSceneController {
 private final SceneService service;public PublicSceneController(SceneService s){service=s;}
 @GetMapping public Object list(){return service.list(null);}
 @GetMapping("/{id}") public Object get(@PathVariable Long id){return service.get(null,id);}
}
