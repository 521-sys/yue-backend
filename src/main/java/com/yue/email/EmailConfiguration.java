package com.yue.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.HexFormat;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {
    @Bean
    public EmailSender emailSender(EmailProperties properties) {
        return (email, code, validMinutes) -> {
            throw new EmailUnavailableException("邮箱登录暂未开放");
        };
    }

    @Bean
    public EmailCodeHasher emailCodeHasher(EmailProperties properties) {
        String pepper = properties.getCodePepper();
        if (pepper == null || pepper.length() < 32) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            pepper = HexFormat.of().formatHex(random);
        }
        return new EmailCodeHasher(pepper);
    }
}
