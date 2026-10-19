package com.yue.email;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class EmailCodeHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public EmailCodeHasher(String pepper) {
        if (pepper == null || pepper.length() < 32) {
            throw new IllegalArgumentException("Email code pepper must be at least 32 characters");
        }
        this.key = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hashCode(String email, String code) { return hmac("code:" + email + ":" + code); }
    public String hashIp(String ip) { return hmac("ip:" + ip); }

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
