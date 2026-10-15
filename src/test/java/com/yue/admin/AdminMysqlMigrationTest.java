package com.yue.admin;
import com.yue.YueApplication;
import org.springframework.boot.SpringApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, disposable loopback database only. */
@EnabledIfSystemProperty(named="admin.mysql",matches="true")
class AdminMysqlMigrationTest {
 private static final String URL="jdbc:mysql://127.0.0.1:33379/admin_v1_check?allowPublicKeyRetrieval=true&useSSL=false&characterEncoding=utf8";
 private org.springframework.context.ConfigurableApplicationContext start(String ddl){
  return SpringApplication.run(YueApplication.class,"--server.address=127.0.0.1","--server.port=0","--spring.profiles.active=test",
   "--spring.datasource.url="+URL,"--spring.datasource.username=root","--spring.datasource.password=",
   "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver","--spring.jpa.hibernate.ddl-auto="+ddl,
   "--app.jwt.secret=local-only-migration-check-012345678901234567890123456789","--app.dubbing-asr.enabled=false");
 }
 @Test void migrationAndSeedAreRepeatableAndMatchHibernate()throws Exception{
  try(var initial=start("create")){}
  try(var c=DriverManager.getConnection(URL,"root","");var s=c.createStatement()){
   s.execute("DROP TABLE audio_resources");s.execute("DROP TABLE admin_audit_logs");
   s.execute("ALTER TABLE scenes DROP COLUMN category,DROP COLUMN difficulty,DROP COLUMN status,DROP COLUMN sort_order,DROP COLUMN version,DROP COLUMN updated_at");
   s.execute("ALTER TABLE scene_lines DROP COLUMN jyutping");
   s.execute("INSERT INTO scenes(code,title,place,rolea,roleb,builtin,created_at) VALUES('existing','保留内容','香港','A','B',1,NOW(6))");
  }
  runSql("scripts/admin-v1-migration.sql");runSql("scripts/admin-v1-seed.sql");
  try(var c=DriverManager.getConnection(URL,"root","");var s=c.createStatement()){
   s.execute("UPDATE scenes SET status='OFFLINE',title='运营修改' WHERE code='canteen'");
  }
  runSql("scripts/admin-v1-migration.sql");runSql("scripts/admin-v1-seed.sql");
  try(var c=DriverManager.getConnection(URL,"root","");var s=c.createStatement()){
   try(var r=s.executeQuery("SELECT COUNT(*) FROM scenes")){r.next();assertEquals(6,r.getInt(1));}
   try(var r=s.executeQuery("SELECT status,title FROM scenes WHERE code='canteen'")){assertTrue(r.next());assertEquals("OFFLINE",r.getString(1));assertEquals("运营修改",r.getString(2));}
   try(var r=s.executeQuery("SELECT status,title FROM scenes WHERE code='existing'")){r.next();assertEquals("PUBLISHED",r.getString(1));assertEquals("保留内容",r.getString(2));}
   try(var r=s.executeQuery("SELECT COUNT(*) FROM scene_lines")){r.next();assertEquals(28,r.getInt(1));}
  }
  try(var validated=start("validate")){
   assertTrue(validated.isActive());
   var audio=validated.getBean(AdminAudioService.class);
   var scenes=validated.getBean(com.yue.service.SceneService.class);
   var repo=validated.getBean(com.yue.repository.AudioResourceRepository.class);
   var tx=new org.springframework.transaction.support.TransactionTemplate(validated.getBean(org.springframework.transaction.PlatformTransactionManager.class));
   var item=audio.upload("mysql-check",new org.springframework.mock.web.MockMultipartFile("file","check.wav","audio/wav",AdminAudioUploadTest.wav(1000)));
   // Establish a repeatable-read snapshot, then commit a new reference on another connection.
   tx.executeWithoutResult(status->{
    repo.findAll();
    try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
     executor.submit(()->scenes.create(123L,new com.yue.dto.SceneCreateRequest("private-lock-check","测试","香港","","A","B",java.util.List.of(new com.yue.dto.SceneLineRequest("A",1,"你好","你好",item.audioKey()))))).get(10,java.util.concurrent.TimeUnit.SECONDS);
    }catch(Exception e){throw new RuntimeException(e);}
    var error=assertThrows(AdminApiException.class,()->audio.delete("mysql-check",Long.parseLong(item.id().substring(7)),item.version()));
    assertEquals(409,error.status);
    status.setRollbackOnly();
   });
  }
 }
 private void runSql(String file)throws Exception{
  var p=new ProcessBuilder(System.getProperty("admin.mysql.client","C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"),"--no-defaults","--host=127.0.0.1","--port=33379","--user=root","--default-character-set=utf8mb4","admin_v1_check").redirectErrorStream(true).start();
  try(var out=p.getOutputStream()){out.write(Files.readAllBytes(Path.of(file)));}
  String output=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
  assertEquals(0,p.waitFor(),output);
 }
}
