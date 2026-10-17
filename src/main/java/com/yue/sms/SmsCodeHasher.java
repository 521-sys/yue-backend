package com.yue.sms;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class SmsCodeHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public SmsCodeHasher(String pepper) {
        if (pepper == null || pepper.length() < 32) {
            throw new IllegalArgumentException("SMS code pepper must be at least 32 characters");
        }
        this.key = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hashCode(String phone, String code) {
        return hmac("code:" + phone + ":" + code);
    }

    public String hashIp(String ip) {
        return hmac("ip:" + ip);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", e);
        }
    }
}
