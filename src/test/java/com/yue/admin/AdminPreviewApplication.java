package com.yue.admin;

import com.yue.YueApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;

/** Local UI verification only. Never included in the production jar. */
public class AdminPreviewApplication {
 public static void main(String[] args){
  var context=SpringApplication.run(YueApplication.class,
   "--server.address=127.0.0.1","--server.port=18081","--spring.profiles.active=test",
   "--spring.datasource.url=jdbc:h2:mem:adminpreview;MODE=MySQL;DB_CLOSE_DELAY=-1",
   "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
   "--spring.jpa.hibernate.ddl-auto=create-drop","--admin.username=preview",
   "--admin.password-hash="+new BCryptPasswordEncoder().encode("local-preview-only"),
   "--app.jwt.secret=local-only-admin-preview-012345678901234567890123456789",
   "--admin.audio-dir=./target/admin-preview-audio","--app.dubbing-asr.enabled=false");
  var service=context.getBean(AdminSceneService.class);
  var scene=service.create("preview",new AdminDtos.SceneSaveRequest("tea-preview","茶餐厅点餐","茶餐厅","🍜","店员","顾客","日常生活","初级",0,0L,List.of(
   new AdminDtos.LineInput("A","你想食啲咩？","你想吃什么？","nei5 soeng2 sik6 di1 me1",null),
   new AdminDtos.LineInput("B","唔该，一份菠萝油。","麻烦来一份菠萝油。",null,null))));
  service.setStatus("preview",scene.scene().getId(),"PUBLISHED",scene.scene().getVersion());
 }
}
