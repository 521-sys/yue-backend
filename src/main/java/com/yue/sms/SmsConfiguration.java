package com.yue.sms;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.SecureRandom;
import java.util.HexFormat;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfiguration {
    @Bean
    public SmsSender smsSender(SmsProperties properties) {
        if (!properties.isReady()) {
            return (phone, code, validMinutes) -> {
                throw new SmsUnavailableException("短信登录暂未开放");
            };
        }
        return new TencentSmsSender(properties);
    }

    @Bean
    public SmsCodeHasher smsCodeHasher(SmsProperties properties) {
        String pepper = properties.getCodePepper();
        if (pepper == null || pepper.length() < 32) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            pepper = HexFormat.of().formatHex(random);
        }
        return new SmsCodeHasher(pepper);
    }
}
