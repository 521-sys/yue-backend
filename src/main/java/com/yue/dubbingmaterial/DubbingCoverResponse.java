package com.yue.dubbingmaterial;

import com.yue.admin.AdminApiException;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.context.request.WebRequest;
import java.nio.file.*;

public final class DubbingCoverResponse {
    private DubbingCoverResponse(){}
    public static ResponseEntity<Resource> respond(Path path,String type,String etag,WebRequest request,boolean publicly){
        if(!Files.isRegularFile(path))throw new AdminApiException(404,"封面不存在");
        var cache=publicly?CacheControl.maxAge(java.time.Duration.ZERO).cachePublic().mustRevalidate():CacheControl.noStore();
        if(request.checkNotModified(etag))return ResponseEntity.status(304).eTag(etag).cacheControl(cache).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).eTag(etag).cacheControl(cache)
            .header("X-Content-Type-Options","nosniff").body(new FileSystemResource(path));
    }
}
