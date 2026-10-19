package com.yue.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailPropertiesTest {
    @Test
    void defaultsAreConservativeAndDisabled() {
        EmailProperties properties = new EmailProperties();

        assertFalse(properties.isEnabled());
        assertFalse(properties.isReady());
        assertEquals(600, properties.getExpirySeconds());
        assertEquals(60, properties.getCooldownSeconds());
        assertEquals(10, properties.getEmailDailyLimit());
        assertEquals(30, properties.getIpDailyLimit());
        assertEquals(5, properties.getMaxFailures());
        assertEquals("0 25 3 * * *", properties.getCleanupCron());
        assertEquals("smtpdm.aliyun.com", properties.getSmtp().getHost());
        assertEquals(465, properties.getSmtp().getPort());
        assertTrue(properties.getSmtp().isSsl());
    }

    @Test
    void disabledOrIncompleteConfigurationCannotSend() {
        EmailConfiguration configuration = new EmailConfiguration();
        EmailProperties disabled = new EmailProperties();
        EmailSender disabledSender = assertDoesNotThrow(() -> configuration.emailSender(disabled));
        assertThrows(EmailUnavailableException.class,
                () -> disabledSender.sendLoginCode("user@example.com", "123456", 10));

        EmailProperties incomplete = new EmailProperties();
        incomplete.setEnabled(true);
        incomplete.setCodePepper("test-pepper-0123456789abcdef0123456789abcdef");
        EmailSender incompleteSender = assertDoesNotThrow(() -> configuration.emailSender(incomplete));
        assertThrows(EmailUnavailableException.class,
                () -> incompleteSender.sendLoginCode("user@example.com", "123456", 10));
    }
}
