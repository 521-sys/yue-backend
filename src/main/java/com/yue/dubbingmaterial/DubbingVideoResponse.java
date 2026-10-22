package com.yue.dubbingmaterial;

import com.yue.admin.AdminApiException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DubbingVideoResponse {
    private static final Pattern SINGLE_RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");

    public ResponseEntity<ResourceRegion> respond(Path path, String contentType, String etag,
                                                   HttpHeaders requestHeaders) {
        try {
            if (!Files.isRegularFile(path)) throw new AdminApiException(404, "视频不存在");
            Resource resource = new FileSystemResource(path);
            long length = resource.contentLength();
            HttpHeaders headers = baseHeaders(contentType, etag);
            String rangeHeader = requestHeaders.getFirst(HttpHeaders.RANGE);
            if (rangeHeader == null || rangeHeader.isBlank()) {
                headers.setContentLength(length);
                return new ResponseEntity<>(new ResourceRegion(resource, 0, length), headers, HttpStatus.OK);
            }

            Matcher matcher = SINGLE_RANGE.matcher(rangeHeader.trim());
            if (!matcher.matches()) return unsatisfiable(headers, length);
            long start;
            long end;
            String first = matcher.group(1);
            String second = matcher.group(2);
            try {
                if (first.isEmpty()) {
                    if (second.isEmpty()) return unsatisfiable(headers, length);
                    long suffix = Long.parseLong(second);
                    if (suffix <= 0) return unsatisfiable(headers, length);
                    long count = Math.min(suffix, length);
                    start = length - count;
                    end = length - 1;
                } else {
                    start = Long.parseLong(first);
                    if (start >= length) return unsatisfiable(headers, length);
                    end = second.isEmpty() ? length - 1 : Math.min(Long.parseLong(second), length - 1);
                    if (end < start) return unsatisfiable(headers, length);
                }
            } catch (NumberFormatException exception) {
                return unsatisfiable(headers, length);
            }

            long count = end - start + 1;
            // ResourceRegionHttpMessageConverter writes Content-Range and Content-Length.
            // Pre-populating Content-Range here gives the converter an immutable copied list.
            return new ResponseEntity<>(new ResourceRegion(resource, start, count), headers, HttpStatus.PARTIAL_CONTENT);
        } catch (AdminApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AdminApiException(500, "视频读取失败");
        }
    }

    private HttpHeaders baseHeaders(String contentType, String etag) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentType(MediaType.parseMediaType(contentType));
        if (etag != null && !etag.isBlank()) headers.setETag(etag);
        return headers;
    }

    private ResponseEntity<ResourceRegion> unsatisfiable(HttpHeaders headers, long length) {
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
        headers.setContentLength(0);
        return new ResponseEntity<>(null, headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }
}
