package com.yue.dubbingmaterial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DubbingVideoResponseTest {
    @TempDir Path directory;
    private Path video;
    private DubbingVideoResponse responses;

    @BeforeEach void setUp() throws Exception {
        video = directory.resolve("video.mp4");
        Files.write(video, new byte[1000]);
        responses = new DubbingVideoResponse();
    }

    @Test void streamsTheWholeFileWithoutLoadingItIntoMemory() {
        var response = responses.respond(video, "video/mp4", "\"video-v1\"", new HttpHeaders());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1000, response.getHeaders().getContentLength());
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
        assertEquals("\"video-v1\"", response.getHeaders().getETag());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getPosition());
        assertEquals(1000, response.getBody().getCount());
    }

    @Test void supportsClosedOpenEndedAndSuffixRanges() {
        assertRange("bytes=0-99", 0, 100);
        assertRange("bytes=100-", 100, 900);
        assertRange("bytes=-100", 900, 100);
    }

    @Test void rejectsMultipleAndUnsatisfiableRanges() {
        assertUnsatisfiable("bytes=0-9,20-29");
        assertUnsatisfiable("bytes=1000-");
        assertUnsatisfiable("bytes=100-99");
    }

    private void assertRange(String range, long position, long count) {
        HttpHeaders request = new HttpHeaders();
        request.set(HttpHeaders.RANGE, range);
        var response = responses.respond(video, "video/mp4", "\"video-v1\"", request);
        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        assertNotNull(response.getBody());
        assertEquals(position, response.getBody().getPosition());
        assertEquals(count, response.getBody().getCount());
    }

    private void assertUnsatisfiable(String range) {
        HttpHeaders request = new HttpHeaders();
        request.set(HttpHeaders.RANGE, range);
        var response = responses.respond(video, "video/mp4", "\"video-v1\"", request);
        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, response.getStatusCode());
        assertEquals("bytes */1000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        assertNull(response.getBody());
    }
}
