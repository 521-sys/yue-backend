package com.yue.admin;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
@Component
public class AudioStorage {
 private final Path root;private final String ffprobe;
 public AudioStorage(@Value("${admin.audio-dir:./data/admin-audio}")String directory,@Value("${app.dubbing-asr.ffprobe:ffprobe}")String probe){root=Path.of(directory).toAbsolutePath().normalize();ffprobe=probe;}
 public record Stored(String fileName,String contentType,long size){}
 public Stored store(MultipartFile file){
  if(file==null||file.isEmpty())throw new AdminApiException(400,"请选择音频文件");
  if(file.getSize()>20L*1024*1024)throw new AdminApiException(413,"音频不能超过 20 MiB");
  String name=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
  String ext=name.endsWith(".wav")?".wav":name.endsWith(".mp3")?".mp3":null;
  if(ext==null)throw new AdminApiException(415,"仅支持 MP3 或 WAV");
  Path dest=null;
  try{
   Files.createDirectories(root);dest=root.resolve(UUID.randomUUID()+ext);
   try(InputStream in=file.getInputStream();OutputStream out=Files.newOutputStream(dest,StandardOpenOption.CREATE_NEW)){
    byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>20L*1024*1024)throw new AdminApiException(413,"音频不能超过 20 MiB");out.write(buffer,0,n);}
   }
   byte[] head;try(var in=Files.newInputStream(dest)){head=in.readNBytes(12);}
   boolean wav=head.length==12&&new String(head,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(head,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE");
   boolean mp3=head.length>=3&&((head[0]=='I'&&head[1]=='D'&&head[2]=='3')||((head[0]&255)==255&&(head[1]&224)==224));
   if(ext.equals(".wav")?!wav:!mp3)throw new AdminApiException(415,"文件内容不是有效音频");
   if(wav){try(var input=javax.sound.sampled.AudioSystem.getAudioInputStream(dest.toFile())){if(input.getFrameLength()<=0)throw new AdminApiException(415,"音频为空");}}
   else{
    Process p=new ProcessBuilder(ffprobe,"-v","error","-select_streams","a:0","-show_entries","stream=codec_name","-of","default=noprint_wrappers=1:nokey=1",dest.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    if(!p.waitFor(10,TimeUnit.SECONDS)){p.destroyForcibly();throw new AdminApiException(415,"音频解析超时");}
    if(p.exitValue()!=0||!new String(p.getInputStream().readNBytes(1024)).trim().equals("mp3"))throw new AdminApiException(415,"无效 MP3 音频");
   }
   return new Stored(dest.getFileName().toString(),ext.equals(".wav")?"audio/wav":"audio/mpeg",Files.size(dest));
  }catch(AdminApiException e){if(dest!=null)remove(dest.getFileName().toString());throw e;}
   catch(Exception e){if(dest!=null)remove(dest.getFileName().toString());if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new AdminApiException(e instanceof javax.sound.sampled.UnsupportedAudioFileException?415:500,"音频保存或解析失败");}
 }
 public Path path(String file){Path p=root.resolve(file).normalize();if(!p.getParent().equals(root))throw new AdminApiException(404,"音频不存在");return p;}
 public void remove(String file){try{Files.deleteIfExists(path(file));}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("音频文件清理失败: {}",file);}}
}
