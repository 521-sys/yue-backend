package com.yue.dubbing;

import com.tencentcloudapi.asr.v20190614.AsrClient;
import com.tencentcloudapi.asr.v20190614.models.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TencentAsrGatewayTest {
    @Test void submitsOrdinaryCantoneseWithSubtitleTiming() throws Exception {
        AsrClient client = mock(AsrClient.class);
        CreateRecTaskResponse response = new CreateRecTaskResponse();
        Task task = new Task(); task.setTaskId(123L); response.setData(task);
        when(client.CreateRecTask(any())).thenReturn(response);
        assertEquals(123L, new TencentAsrGateway(client).submit(new byte[]{1, 2, 3}));
        var arg = ArgumentCaptor.forClass(CreateRecTaskRequest.class);
        verify(client).CreateRecTask(arg.capture());
        assertEquals("16k_yue", arg.getValue().getEngineModelType());
        assertEquals(3L, arg.getValue().getResTextFormat());
        assertEquals(1L, arg.getValue().getSourceType());
        assertEquals(1L, arg.getValue().getChannelNum());
        assertEquals(3L, arg.getValue().getDataLen());
        assertArrayEquals(new byte[]{1, 2, 3}, Base64.getDecoder().decode(arg.getValue().getData()));
    }

    @Test void mapsSentenceTimesAndRejectsInvalidTimes() throws Exception {
        AsrClient client = mock(AsrClient.class);
        var response = new DescribeTaskStatusResponse();
        var status = new TaskStatus(); status.setStatus(2L);
        var sentence = new SentenceDetail();
        sentence.setStartMs(120L); sentence.setEndMs(990L); sentence.setFinalSentence("你好");
        status.setResultDetail(new SentenceDetail[]{sentence}); response.setData(status);
        when(client.DescribeTaskStatus(any())).thenReturn(response);
        var gateway = new TencentAsrGateway(client);
        var result = gateway.poll(123L);
        assertEquals("succeeded", result.status());
        assertEquals(120, result.cues().getFirst().startMs());
        assertEquals("你好", result.cues().getFirst().text());
        sentence.setEndMs(100L);
        assertThrows(IllegalStateException.class, () -> gateway.poll(123L));
    }

    @Test void enforcesDirectUploadSizeBeforeCallingProvider() {
        AsrClient client = mock(AsrClient.class);
        assertThrows(IllegalArgumentException.class,
            () -> new TencentAsrGateway(client).submit(new byte[5 * 1024 * 1024 + 1]));
        verifyNoInteractions(client);
    }
}
