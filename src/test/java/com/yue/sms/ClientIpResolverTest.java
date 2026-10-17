package com.yue.sms;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {
    private final SmsProperties properties = new SmsProperties();
    private final ClientIpResolver resolver = new ClientIpResolver(properties);

    @Test
    void ignoresForwardedHeaderFromUntrustedRemoteAddress() {
        MockHttpServletRequest request = request("198.51.100.4", "203.0.113.8");

        assertEquals("198.51.100.4", resolver.resolve(request));
    }

    @Test
    void trustedLoopbackProxyUsesFirstValidForwardedAddress() {
        MockHttpServletRequest request = request("127.0.0.1", "203.0.113.8, 127.0.0.1");

        assertEquals("203.0.113.8", resolver.resolve(request));
    }

    @Test
    void malformedOrMultilineForwardedValueFallsBackToRemoteAddress() {
        MockHttpServletRequest invalid = request("127.0.0.1", "not-an-ip, 203.0.113.8");
        MockHttpServletRequest multiline = request("127.0.0.1", "203.0.113.8\n198.51.100.4");

        assertEquals("127.0.0.1", resolver.resolve(invalid));
        assertEquals("127.0.0.1", resolver.resolve(multiline));
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
