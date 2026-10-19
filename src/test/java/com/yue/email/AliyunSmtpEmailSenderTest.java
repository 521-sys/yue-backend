package com.yue.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.*;

class AliyunSmtpEmailSenderTest {
    private RecordingMailSender mailSender;
    private AliyunSmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        EmailProperties properties = new EmailProperties();
        properties.getSmtp().setFrom("verify@mail.gnep.online");
        properties.getSmtp().setFromName("粤语学习");
        mailSender = new RecordingMailSender();
        sender = new AliyunSmtpEmailSender(mailSender, properties);
    }

    @Test
    void sendsUtf8VerificationMessageWithSafeEnvelope() throws Exception {
        EmailSendReceipt receipt = sender.sendLoginCode("learner@example.com", "123456", 10);

        MimeMessage message = mailSender.sent;
        assertNotNull(message);
        assertEquals("learner@example.com", message.getAllRecipients()[0].toString());
        assertTrue(message.getFrom()[0].toString().contains("verify@mail.gnep.online"));
        assertEquals("粤语学习登录验证码", message.getSubject());
        String body = String.valueOf(message.getContent());
        assertTrue(body.contains("123456"));
        assertTrue(body.contains("10 分钟"));
        assertFalse(body.contains("learner@example.com"));
        assertNotNull(receipt.providerRequestId());
    }

    @Test
    void classifiesAuthenticationRejectionAsKnownAndTransportFailureAsUnknown() {
        mailSender.failure = new MailAuthenticationException("bad credentials");
        EmailDeliveryException rejected = assertThrows(EmailDeliveryException.class,
                () -> sender.sendLoginCode("learner@example.com", "123456", 10));
        assertFalse(rejected.unknownOutcome());

        mailSender.failure = new MailSendException("connection reset");
        EmailDeliveryException unknown = assertThrows(EmailDeliveryException.class,
                () -> sender.sendLoginCode("learner@example.com", "123456", 10));
        assertTrue(unknown.unknownOutcome());
    }

    static class RecordingMailSender extends JavaMailSenderImpl {
        MimeMessage sent;
        RuntimeException failure;

        @Override
        public void send(MimeMessage mimeMessage) {
            if (failure != null) throw failure;
            sent = mimeMessage;
        }
    }
}
