package com.yue.dubbing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DubbingTranscriptionServiceTest {
    @TempDir Path directory;
    @Test void disabledServiceNeverReadsOrUploadsMedia() {
        var properties = new DubbingAsrProperties();
        var gateway = mock(TencentAsrGateway.class);
        try (var service = new DubbingTranscriptionService(properties, gateway, mock(AsrAudioExtractor.class), mock(AsrBudget.class))) {
            assertThrows(IllegalStateException.class, () -> service.submit(1L, new MockMultipartFile("file", new byte[]{1})));
            verifyNoInteractions(gateway);
        }
    }
    @Test void successfulJobIsPrivateAndBudgetReservedBeforeSubmit() throws Exception {
        var properties = new DubbingAsrProperties(); properties.setEnabled(true);
        var gateway = mock(TencentAsrGateway.class);
        var extractor = mock(AsrAudioExtractor.class);
        var budget = mock(AsrBudget.class);
        when(extractor.extract(any())).thenReturn(new AsrAudioExtractor.Audio(new byte[]{1}, 30));
        when(gateway.submit(any())).thenReturn(123L);
        when(gateway.poll(123L)).thenReturn(new TencentAsrGateway.Result("succeeded", java.util.List.of(new TencentAsrGateway.Cue(0,1000,"你好"))));
        try (var service = new DubbingTranscriptionService(properties, gateway, extractor, budget)) {
            var job = service.submit(1L, new MockMultipartFile("file", new byte[]{1}));
            assertThrows(java.util.NoSuchElementException.class, () -> service.get(2L, job.id()));
            for (int i=0; i<100 && !service.get(1L,job.id()).status().equals("succeeded"); i++) Thread.sleep(20);
            assertEquals("succeeded", service.get(1L,job.id()).status());
            var order = inOrder(budget, gateway);
            order.verify(budget).reserve(30);
            order.verify(gateway).submit(any());
        }
    }
    @Test void transientPollingFailureDoesNotResubmitPaidTask() throws Exception {
        var properties = new DubbingAsrProperties(); properties.setEnabled(true);
        var gateway = mock(TencentAsrGateway.class);
        var extractor = mock(AsrAudioExtractor.class);
        when(extractor.extract(any())).thenReturn(new AsrAudioExtractor.Audio(new byte[]{1}, 3));
        when(gateway.submit(any())).thenReturn(123L);
        when(gateway.poll(123L)).thenThrow(new com.tencentcloudapi.common.exception.TencentCloudSDKException("temporary network failure"))
            .thenReturn(new TencentAsrGateway.Result("succeeded", java.util.List.of()));
        try (var service = new DubbingTranscriptionService(properties, gateway, extractor, mock(AsrBudget.class))) {
            var job = service.submit(1L, new MockMultipartFile("file", new byte[]{1}));
            for (int i=0; i<400 && !service.get(1L,job.id()).status().equals("succeeded"); i++) Thread.sleep(20);
            assertEquals("succeeded", service.get(1L,job.id()).status());
            verify(gateway, times(1)).submit(any());
            verify(gateway, times(2)).poll(123L);
        }
    }
}
