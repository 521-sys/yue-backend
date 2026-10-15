package com.yue.controller;
import com.yue.admin.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/audio/uploads")
public class AudioResourceController {
 private final AdminAudioService audio;
 public AudioResourceController(AdminAudioService a){audio=a;}
 @GetMapping("/{opaque}") public ResponseEntity<Resource> get(@PathVariable String opaque){
  var a=audio.resource(opaque);Resource r=new ByteArrayResource(a.bytes());
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.contentType())).cacheControl(CacheControl.noCache()).body(r);
 }
}
