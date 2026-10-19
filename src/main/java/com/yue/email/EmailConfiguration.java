package com.yue.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.security.SecureRandom;
import java.util.HexFormat;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {
    @Bean
    public EmailSender emailSender(EmailProperties properties) {
        if (properties.isReady()) {
            EmailProperties.Smtp smtp = properties.getSmtp();
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(smtp.getHost());
            mailSender.setPort(smtp.getPort());
            mailSender.setUsername(smtp.getUsername());
            mailSender.setPassword(smtp.getPassword());
            mailSender.setDefaultEncoding("UTF-8");
            var javaMail = mailSender.getJavaMailProperties();
            javaMail.setProperty("mail.transport.protocol", "smtp");
            javaMail.setProperty("mail.smtp.auth", "true");
            javaMail.setProperty("mail.smtp.ssl.enable", Boolean.toString(smtp.isSsl()));
            javaMail.setProperty("mail.smtp.connectiontimeout", "10000");
            javaMail.setProperty("mail.smtp.timeout", "15000");
            javaMail.setProperty("mail.smtp.writetimeout", "15000");
            return new AliyunSmtpEmailSender(mailSender, properties);
        }
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
