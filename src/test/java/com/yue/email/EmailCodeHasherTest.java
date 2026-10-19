package com.yue.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailCodeHasherTest {
    private final EmailCodeHasher hasher = new EmailCodeHasher(
            "test-pepper-0123456789abcdef0123456789abcdef");

    @Test
    void codeHashIsStableHexAndBoundToNormalizedEmailAndCode() {
        String original = hasher.hashCode("user@example.com", "123456");

        assertEquals(original, hasher.hashCode("user@example.com", "123456"));
        assertTrue(original.matches("[0-9a-f]{64}"));
        assertFalse(original.contains("123456"));
        assertFalse(original.contains("user@example.com"));
        assertNotEquals(original, hasher.hashCode("other@example.com", "123456"));
        assertNotEquals(original, hasher.hashCode("user@example.com", "654321"));
        assertNotEquals(original, hasher.hashIp("user@example.com:123456"));
    }

    @Test
    void constructorRejectsShortPepper() {
        assertThrows(IllegalArgumentException.class, () -> new EmailCodeHasher("too-short"));
    }
}
