package com.yue.sms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsPropertiesTest {
    @Test
    void defaultsAreConservativeAndDisabled() {
        SmsProperties properties = new SmsProperties();

        assertFalse(properties.isEnabled());
        assertEquals(300, properties.getExpirySeconds());
        assertEquals(60, properties.getCooldownSeconds());
        assertEquals(10, properties.getPhoneDailyLimit());
        assertEquals(30, properties.getIpDailyLimit());
        assertEquals(5, properties.getMaxFailures());
        assertEquals(java.util.Set.of("127.0.0.1", "::1"), properties.getTrustedProxyAddresses());
    }

    @Test
    void disabledOrIncompleteConfigurationKeepsContextUsableButCannotSend() {
        SmsConfiguration configuration = new SmsConfiguration();
        SmsProperties disabled = new SmsProperties();
        SmsSender disabledSender = assertDoesNotThrow(() -> configuration.smsSender(disabled));
        assertThrows(SmsUnavailableException.class,
                () -> disabledSender.sendLoginCode("13812345678", "123456", 5));

        SmsProperties incomplete = new SmsProperties();
        incomplete.setEnabled(true);
        incomplete.setCodePepper("test-pepper-0123456789abcdef0123456789abcdef");
        SmsSender incompleteSender = assertDoesNotThrow(() -> configuration.smsSender(incomplete));
        assertThrows(SmsUnavailableException.class,
                () -> incompleteSender.sendLoginCode("13812345678", "123456", 5));
    }
}
