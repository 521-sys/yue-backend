package com.yue.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试：签发/解析/过期/篡改。
 */
class JwtUtilTest {

    private static final String SECRET =
            "unit-test-secret-key-0123456789abcdef0123456789abcdef-0123456789abcdef";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 3600000);
    }

    @Test
    @DisplayName("签发后可解析出 userId 与 username")
    void generateAndParse_roundtrip() {
        String token = jwtUtil.generate(42L, "tom");
        Claims claims = jwtUtil.parse(token);
        assertEquals(42L, jwtUtil.extractUserId(token).longValue());
        assertEquals("tom", claims.get("username", String.class));
    }

    @Test
    @DisplayName("有效 token 校验通过")
    void isValid_true_forFreshToken() {
        String token = jwtUtil.generate(1L, "alice");
        assertTrue(jwtUtil.isValid(token));
    }

    @Test
    @DisplayName("乱串 token 校验不通过")
    void isValid_false_forGarbage() {
        assertFalse(jwtUtil.isValid("not-a-token"));
        assertFalse(jwtUtil.isValid(""));
    }

    @Test
    @DisplayName("过期 token 校验不通过")
    void isValid_false_forExpired() {
        JwtUtil expiredUtil = new JwtUtil(SECRET, -1000);
        String token = expiredUtil.generate(1L, "alice");
        assertFalse(jwtUtil.isValid(token));
    }

    @Test
    @DisplayName("密钥不同的签名校验不通过（防篡改）")
    void isValid_false_forWrongSignature() {
        JwtUtil otherUtil = new JwtUtil(
                "another-secret-key-9876543210fedcba9876543210fedcba-9876543210fedcba", 3600000);
        String token = jwtUtil.generate(1L, "alice");
        assertFalse(otherUtil.isValid(token));
    }
}
