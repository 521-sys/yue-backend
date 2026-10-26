package com.yue.dubbingmaterial;

import com.yue.admin.AdminApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DubbingCoverStorageTest {
    @TempDir Path root;
    static byte[] image(String format, int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),format,out);
        return out.toByteArray();
    }
    @Test void detectsRealJpegAndPngRegardlessOfFilenameOrMime() throws Exception {
        var storage = new DubbingCoverStorage(root.toString());
        for (String format : new String[]{"png","jpeg"}) {
            var saved = storage.store(new MockMultipartFile("file","wrong.bin","text/plain",image(format,1280,720)));
            assertEquals("image/"+format,saved.contentType());
            assertEquals(1280,saved.width()); assertEquals(720,saved.height());
            assertTrue(Files.isRegularFile(storage.path(saved.fileName())));
        }
    }
    @Test void rejectsInvalidEmptyOversizedAndExcessiveDimensions() throws Exception {
        var storage = new DubbingCoverStorage(root.toString());
        for(byte[] bytes : new byte[][]{new byte[0],"not an image".getBytes(),new byte[5*1024*1024+1],image("png",4097,1),image("png",4001,4000)}) {
            assertThrows(AdminApiException.class,()->storage.store(new MockMultipartFile("file","fake.jpg","image/jpeg",bytes)));
        }
        try(var files=Files.list(root)){assertEquals(0,files.count());}
        assertThrows(AdminApiException.class,()->storage.path("../outside.jpg"));
    }
    @Test void readsTheActualStreamSizeRatherThanTrustingMultipartMetadata(){
        var storage=new DubbingCoverStorage(root.toString());
        var dishonest=new MockMultipartFile("file","cover.png","image/png",new byte[5*1024*1024+1]){@Override public long getSize(){return 1;}};
        assertEquals(413,assertThrows(AdminApiException.class,()->storage.store(dishonest)).status);
    }
    @Test void rejectsExcessiveDimensionsInAPngHeaderBeforeFullDecode() throws Exception {
        byte[] png=image("png",1,1);java.nio.ByteBuffer.wrap(png,16,4).putInt(100000);
        var storage=new DubbingCoverStorage(root.toString());
        assertEquals(415,assertThrows(AdminApiException.class,()->storage.store(new MockMultipartFile("file",png))).status);
    }
    @Test void rejectsSymbolicLinksOutsideCoverDirectory() throws Exception {
        Path outside=Files.createTempFile("outside-cover-", ".jpg"),link=root.resolve("link.jpg");
        try{
            try{Files.createSymbolicLink(link,outside);}catch(java.io.IOException|UnsupportedOperationException e){org.junit.jupiter.api.Assumptions.assumeTrue(false,"Symlink creation unavailable");}
            var storage=new DubbingCoverStorage(root.toString());
            assertThrows(AdminApiException.class,()->storage.path("link.jpg"));
        }finally{Files.deleteIfExists(link);Files.deleteIfExists(outside);}
    }
}
