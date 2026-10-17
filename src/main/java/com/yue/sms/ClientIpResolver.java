package com.yue.sms;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;

@Component
public class ClientIpResolver {
    private final SmsProperties properties;

    public ClientIpResolver(SmsProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!properties.getTrustedProxyAddresses().contains(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.indexOf('\r') >= 0 || forwarded.indexOf('\n') >= 0) {
            return remote;
        }
        String candidate = forwarded.split(",", 2)[0].trim();
        return isIpLiteral(candidate) ? candidate : remote;
    }

    private boolean isIpLiteral(String value) {
        if (value.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
            for (String part : value.split("\\.")) {
                if (Integer.parseInt(part) > 255) return false;
            }
            return true;
        }
        if (!value.contains(":") || !value.matches("[0-9a-fA-F:.]+")) return false;
        try {
            return InetAddress.getByName(value) instanceof Inet6Address;
        } catch (Exception ignored) {
            return false;
        }
    }
}
