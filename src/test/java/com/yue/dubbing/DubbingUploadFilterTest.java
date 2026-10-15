package com.yue.dubbing;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DubbingUploadFilterTest {
    @Test void adminAudioAllowsBoundedMultipartForUploadAndReplacement() throws Exception {
        var filter = new DubbingUploadFilter(new DubbingAsrProperties(), mock(DubbingTranscriptionService.class));
        for(String method: new String[]{"POST","PUT"}) {
            var request = new MockHttpServletRequest(method, method.equals("POST")?"/api/admin/audio":"/api/admin/audio/12");
            request.setContentType("multipart/form-data; boundary=test");
            request.setContent(new byte[3 * 1024 * 1024]);
            var chain = new MockFilterChain();
            filter.doFilter(request,new MockHttpServletResponse(),chain);
            assertNotNull(chain.getRequest());
        }
        var request = new MockHttpServletRequest("POST","/api/admin/audio");
        request.setContentType("multipart/form-data; boundary=test");
        request.setContent(new byte[21 * 1024 * 1024 + 1]);
        var response = new MockHttpServletResponse();
        filter.doFilter(request,response,new MockFilterChain());
        assertEquals(413,response.getStatus());
    }
    @Test void disabledFeatureRejectsBeforeReadingMultipart() throws Exception {
        var service = mock(DubbingTranscriptionService.class);
        var filter = new DubbingUploadFilter(new DubbingAsrProperties(), service);
        var request = new MockHttpServletRequest("POST", "/api/dubbing/transcriptions");
        request.setContentType("multipart/form-data; boundary=test");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(503, response.getStatus()); assertNull(chain.getRequest());
    }
    @Test void otherEndpointsDoNotGainLargeUploadAllowance() throws Exception {
        var filter = new DubbingUploadFilter(new DubbingAsrProperties(), mock(DubbingTranscriptionService.class));
        var request = new MockHttpServletRequest("POST", "/api/ai/transcribe");
        request.setContentType("multipart/form-data; boundary=test");
        request.setContent(new byte[3 * 1024 * 1024]);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(413, response.getStatus());
    }
}
