package com.yue.dubbingmaterial;

import com.yue.admin.AdminApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DubbingVideoStorageTest {
    private static final long MAX_BYTES = 200L * 1024 * 1024;
    private static final byte[] MP4_HEAD = new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};

    @TempDir Path directory;

    @Test void storesARealH264AacFixtureAndAtomicallyPromotesIt() throws Exception {
        Path source = directory.resolve("fixture.mp4");
        Process generated = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error",
                "-f", "lavfi", "-i", "color=c=black:s=64x64:d=1",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", "-y", source.toString()).start();
        assertTrue(generated.waitFor(20, TimeUnit.SECONDS));
        assertEquals(0, generated.exitValue());

        Path store = directory.resolve("store");
        var storage = new DubbingVideoStorage(store.toString(), new DubbingVideoProbe("ffprobe"));
        try (InputStream input = Files.newInputStream(source)) {
            var saved = storage.store(new MockMultipartFile("file", "part.mp4", "video/mp4", input));
            assertTrue(saved.fileName().matches("[0-9a-f-]{36}\\.mp4"));
            assertEquals("video/mp4", saved.contentType());
            assertEquals("h264", saved.videoCodec());
            assertEquals("aac", saved.audioCodec());
            assertTrue(saved.durationMs() > 0 && saved.durationMs() <= 5 * 60 * 1000);
            assertTrue(Files.isRegularFile(storage.path(saved.fileName())));
            try (var files = Files.list(store)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".upload")));
            }
        }
    }

    @Test void rejectsFakeMp4BeforeProbeAndCleansTemporaryFile() throws Exception {
        var probe = mock(DubbingVideoProbe.class);
        var storage = new DubbingVideoStorage(directory.toString(), probe);
        var error = assertThrows(AdminApiException.class, () -> storage.store(
                new MockMultipartFile("file", "fake.mp4", "video/mp4", "not an mp4".getBytes())));
        assertEquals(415, error.status);
        verifyNoInteractions(probe);
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void rejectsWrongCodecsMissingAudioAndExcessiveDuration() {
        assertRejected(new DubbingVideoProbe.Metadata(1000, "mov,mp4", "hevc", "aac"), 415);
        assertRejected(new DubbingVideoProbe.Metadata(1000, "mov,mp4", "h264", "mp3"), 415);
        assertRejected(new DubbingVideoProbe.Metadata(1000, "mov,mp4", "h264", null), 415);
        assertRejected(new DubbingVideoProbe.Metadata(300_001, "mov,mp4", "h264", "aac"), 415);
    }

    @Test void enforcesThe200MiBBoundaryWithoutAllocatingA200MiBArray() {
        var probe = mock(DubbingVideoProbe.class);
        when(probe.probe(any())).thenReturn(new DubbingVideoProbe.Metadata(1000, "mov,mp4", "h264", "aac"));
        var storage = new DubbingVideoStorage(directory.toString(), probe);

        assertDoesNotThrow(() -> storage.store(new SizedMultipartFile(MAX_BYTES, MP4_HEAD)));
        var tooLarge = new SizedMultipartFile(MAX_BYTES + 1, MP4_HEAD);
        var error = assertThrows(AdminApiException.class, () -> storage.store(tooLarge));
        assertEquals(413, error.status);
        assertFalse(tooLarge.opened);
    }

    @Test void timesOutTheProbeAndDeletesTheTemporaryUpload() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);
        when(process.destroyForcibly()).thenReturn(process);
        var probe = new DubbingVideoProbe("fake-ffprobe", Duration.ofMillis(5), command -> process);
        var storage = new DubbingVideoStorage(directory.toString(), probe);

        var error = assertThrows(AdminApiException.class, () -> storage.store(new SizedMultipartFile(12, MP4_HEAD)));
        assertEquals(415, error.status);
        verify(process).destroyForcibly();
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void rejectsTraversalAndRemoveIsIdempotent() {
        var storage = new DubbingVideoStorage(directory.toString(), mock(DubbingVideoProbe.class));
        assertEquals(404, assertThrows(AdminApiException.class, () -> storage.path("../outside.mp4")).status);
        assertDoesNotThrow(() -> storage.remove("missing.mp4"));
        assertDoesNotThrow(() -> storage.remove("missing.mp4"));
    }

    private void assertRejected(DubbingVideoProbe.Metadata metadata, int status) {
        var probe = mock(DubbingVideoProbe.class);
        when(probe.probe(any())).thenReturn(metadata);
        var storage = new DubbingVideoStorage(directory.toString(), probe);
        var error = assertThrows(AdminApiException.class,
                () -> storage.store(new SizedMultipartFile(12, MP4_HEAD)));
        assertEquals(status, error.status);
    }

    private static final class SizedMultipartFile implements MultipartFile {
        private final long size;
        private final byte[] content;
        private boolean opened;

        private SizedMultipartFile(long size, byte[] content) { this.size = size; this.content = content; }
        public String getName() { return "file"; }
        public String getOriginalFilename() { return "part.mp4"; }
        public String getContentType() { return "video/mp4"; }
        public boolean isEmpty() { return size == 0; }
        public long getSize() { return size; }
        public byte[] getBytes() { return content.clone(); }
        public InputStream getInputStream() { opened = true; return new ByteArrayInputStream(content); }
        public void transferTo(java.io.File dest) throws IOException { Files.copy(getInputStream(), dest.toPath()); }
    }
}
