package com.yue.sms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsCodeHasherTest {
    private final SmsCodeHasher hasher = new SmsCodeHasher(
            "test-pepper-0123456789abcdef0123456789abcdef");

    @Test
    void codeHashIsStableHexAndDoesNotContainPlainInputs() {
        String first = hasher.hashCode("13812345678", "123456");
        String second = hasher.hashCode("13812345678", "123456");

        assertEquals(first, second);
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertFalse(first.contains("123456"));
        assertFalse(first.contains("13812345678"));
    }

    @Test
    void codeHashIsBoundToBothPhoneAndCode() {
        String original = hasher.hashCode("13812345678", "123456");

        assertNotEquals(original, hasher.hashCode("13912345678", "123456"));
        assertNotEquals(original, hasher.hashCode("13812345678", "654321"));
        assertNotEquals(original, hasher.hashIp("13812345678:123456"));
    }

    @Test
    void ipHashUsesTheSameStableNonPlaintextContract() {
        String hash = hasher.hashIp("203.0.113.8");

        assertEquals(hash, hasher.hashIp("203.0.113.8"));
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertFalse(hash.contains("203.0.113.8"));
    }
}
