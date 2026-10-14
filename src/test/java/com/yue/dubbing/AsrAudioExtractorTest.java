package com.yue.dubbing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AsrAudioExtractorTest {
    @TempDir Path directory;
    @Test void extractsRealAudioAndRejectsPlaylistInput() throws Exception {
        Path audio = directory.resolve("input.wav");
        var generated = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-y", audio.toString()).start();
        assertTrue(generated.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, generated.exitValue());
        var extractor = new AsrAudioExtractor(new DubbingAsrProperties());
        var result = extractor.extract(audio);
        // Independently measure exactly the bytes that will be uploaded.
        Path extracted = directory.resolve("extracted.mp3"); Files.write(extracted, result.bytes());
        var measured = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", extracted.toString()).start();
        String actual = new String(measured.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, measured.waitFor());
        assertEquals((long)Math.ceil(Double.parseDouble(actual)), result.seconds());
        assertTrue(result.bytes().length > 1000 && result.bytes().length < 100000);
        Path playlist = directory.resolve("bad.m3u8");
        Files.writeString(playlist, "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\n" + audio.toUri() + "\n#EXT-X-ENDLIST\n");
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(playlist));
    }
}
