package com.yue.dubbingmaterial;

import com.yue.admin.AdminApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.io.*;
import java.util.*;

@Component
public class DubbingCoverStorage {
    public static final long MAX_BYTES=5L*1024*1024;
    private final Path root;
    public DubbingCoverStorage(@Value("${admin.dubbing-cover-dir:./data/dubbing-covers}") String directory){root=Path.of(directory).toAbsolutePath().normalize();}
    public StoredCover store(MultipartFile file){
        if(file==null||file.isEmpty())throw new AdminApiException(400,"请选择 JPEG 或 PNG 封面");
        if(file.getSize()>MAX_BYTES)throw new AdminApiException(413,"封面不能超过 5 MiB");
        Path temporary=null,destination=null;
        try{
            Files.createDirectories(root);
            temporary=root.resolve(UUID.randomUUID()+".upload");
            long size=0;
            try(var input=file.getInputStream();var output=Files.newOutputStream(temporary,StandardOpenOption.CREATE_NEW)){
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){size+=n;if(size>MAX_BYTES)throw new AdminApiException(413,"封面不能超过 5 MiB");output.write(buffer,0,n);}
            }
            String format;int width,height;
            try(var input=ImageIO.createImageInputStream(temporary.toFile())){
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext())throw new AdminApiException(415,"封面内容必须是 JPEG 或 PNG");
                var reader=readers.next();
                try{
                    format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    if(!Set.of("jpeg","png").contains(format))throw new AdminApiException(415,"封面内容必须是 JPEG 或 PNG");
                    reader.setInput(input,true,true);width=reader.getWidth(0);height=reader.getHeight(0);
                    if(width<1||height<1||width>4096||height>4096||(long)width*height>16_000_000)throw new AdminApiException(415,"封面最大边长 4096 像素且不超过 1600 万像素");
                    if(reader.read(0)==null)throw new AdminApiException(415,"封面图片损坏");
                }finally{reader.dispose();}
            }
            destination=root.resolve(UUID.randomUUID()+(format.equals("jpeg")?".jpg":".png"));
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);temporary=null;
            return new StoredCover(destination.getFileName().toString(),"image/"+format,size,width,height);
        }catch(AdminApiException e){throw e;}catch(javax.imageio.IIOException e){throw new AdminApiException(415,"封面图片损坏");}
        catch(Exception e){throw new AdminApiException(500,"封面保存失败");}
        finally{if(temporary!=null)delete(temporary);}
    }
    public Path path(String name){
        if(name==null||name.isBlank())throw new AdminApiException(404,"封面不存在");
        var path=root.resolve(name).normalize();if(!root.equals(path.getParent())||Files.isSymbolicLink(path))throw new AdminApiException(404,"封面不存在");return path;
    }
    public void remove(String name){delete(path(name));}
    private void delete(Path path){try{Files.deleteIfExists(path);}catch(IOException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("封面文件清理失败: {}",path.getFileName());}}
    public record StoredCover(String fileName,String contentType,long sizeBytes,int width,int height){}
}
