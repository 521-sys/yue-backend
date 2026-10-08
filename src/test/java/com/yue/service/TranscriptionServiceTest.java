package com.yue.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class TranscriptionServiceTest {
    @Test void rejectsOversizedPcmBeforeCallingProvider() {
        var service = new TranscriptionService();
        var audio = new MockMultipartFile("audio", "speech.pcm", "application/octet-stream", new byte[960002]);
        assertThrows(IllegalArgumentException.class, () -> service.transcribe(audio));
    }
    @Test void rejectsMissingCloudKeyWithoutLocalWhisperFallback() {
        var service = new TranscriptionService();
        var audio = new MockMultipartFile("audio", "speech.pcm", "application/octet-stream", new byte[16000]);
        var error = assertThrows(IllegalStateException.class, () -> service.transcribe(audio));
        assertEquals("语音识别服务尚未配置", error.getMessage());
    }
    @Test void rejectsInvalidPcmLength() {
        var service = new TranscriptionService();
        var audio = new MockMultipartFile("audio", "speech.pcm", "application/octet-stream", new byte[16001]);
        assertThrows(IllegalArgumentException.class, () -> service.transcribe(audio));
    }
}
