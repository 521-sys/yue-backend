package com.yue.dubbing;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.security.Principal;
import java.util.concurrent.*;
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

    @Test void onlyAuthenticatedMovieVideoRoutesReceiveThe201MiBAllowance() throws Exception {
        var filter = new DubbingUploadFilter(new DubbingAsrProperties(), mock(DubbingTranscriptionService.class));
        for (String method : new String[]{"POST", "PUT"}) {
            var request = movieRequest(method, 200L * 1024 * 1024);
            var chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest());
        }

        var tooLarge = movieRequest("POST", 201L * 1024 * 1024 + 1);
        var tooLargeResponse = new MockHttpServletResponse();
        filter.doFilter(tooLarge, tooLargeResponse, new MockFilterChain());
        assertEquals(413, tooLargeResponse.getStatus());

        var anonymous = movieRequest("POST", 3L * 1024 * 1024);
        anonymous.setUserPrincipal(null);
        var anonymousResponse = new MockHttpServletResponse();
        filter.doFilter(anonymous, anonymousResponse, new MockFilterChain());
        assertEquals(413, anonymousResponse.getStatus());
    }

    @Test void movieAndAsrUploadsShareTheSingleUploadPermit() throws Exception {
        var properties = new DubbingAsrProperties();
        properties.setEnabled(true);
        var service = mock(DubbingTranscriptionService.class);
        when(service.available()).thenReturn(true);
        var filter = new DubbingUploadFilter(properties, service);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var first = movieRequest("POST", 1024);
            Future<?> running = executor.submit(() -> {
                try {
                    filter.doFilter(first, new MockHttpServletResponse(), (request, response) -> {
                        entered.countDown();
                        try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new java.io.IOException(interrupted);
                        }
                    });
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            var asr = new MockHttpServletRequest("POST", "/api/dubbing/transcriptions");
            asr.setContentType("multipart/form-data; boundary=test");
            asr.setContent(new byte[8]);
            var response = new MockHttpServletResponse();
            filter.doFilter(asr, response, new MockFilterChain());
            assertEquals(503, response.getStatus());

            release.countDown();
            running.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private MockHttpServletRequest movieRequest(String method, long contentLength) {
        var request = new MockHttpServletRequest(method, "/api/admin/dubbing-materials/7/parts/9/video") {
            @Override public long getContentLengthLong() { return contentLength; }
            @Override public int getContentLength() { return contentLength > Integer.MAX_VALUE ? -1 : (int) contentLength; }
        };
        request.setContentType("multipart/form-data; boundary=test");
        request.setUserPrincipal((Principal) () -> "operator");
        return request;
    }
}
