package com.yue.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TtsService 单元测试：
 * 不依赖本机 edge-tts（外部工具），覆盖参数注入、缓存命中与命令失败分支。
 */
class TtsServiceTest {

    private TtsService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TtsService();
        setField("voice", "zh-HK-HiuGaaiNeural");
        setField("command", "definitely-not-exist-tts-cmd");
        setField("timeoutSeconds", 5L);
        setField("workDir", System.getProperty("java.io.tmpdir") + "/yue-tts-test");
        setField("cacheMaxEntries", 200);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = TtsService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, byte[]> cache() throws Exception {
        Field f = TtsService.class.getDeclaredField("cache");
        f.setAccessible(true);
        return (java.util.Map<String, byte[]>) f.get(service);
    }

    private void putCache(String text, byte[] audio) throws Exception {
        Method m = TtsService.class.getDeclaredMethod("putCache", String.class, byte[].class);
        m.setAccessible(true);
        m.invoke(service, text, audio);
    }

    @Test
    void synthesizeCommandMissing_throwsIOException() {
        assertThrows(java.io.IOException.class, () -> service.synthesize("早晨"));
    }

    @Test
    void synthesizeCacheHit_returnsCachedBytesWithoutRunningCommand() throws Exception {
        byte[] fake = {0x49, 0x44, 0x33, 0x01};
        putCache("早晨", fake);
        // 命令必然失败，但缓存命中后不应再执行命令
        byte[] out = service.synthesize("早晨");
        assertArrayEquals(fake, out);
    }

    @Test
    void synthesizeDifferentText_doesNotHitCache() throws Exception {
        putCache("早晨", new byte[]{1});
        assertThrows(java.io.IOException.class, () -> service.synthesize("午安"));
    }
}
