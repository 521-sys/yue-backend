package com.yue.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

public class AliyunSmtpEmailSender implements EmailSender {
    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public AliyunSmtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public EmailSendReceipt sendLoginCode(String email, String code, int validMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.getSmtp().getFrom(), properties.getSmtp().getFromName());
            helper.setTo(email);
            helper.setSubject("粤语学习登录验证码");
            helper.setText(html(code, validMinutes), true);
            message.saveChanges();
            String messageId = message.getMessageID();
            mailSender.send(message);
            return new EmailSendReceipt(messageId == null ? UUID.randomUUID().toString() : messageId);
        } catch (MailAuthenticationException rejected) {
            throw new EmailDeliveryException("SMTP authentication rejected", false, rejected);
        } catch (MailException transport) {
            throw new EmailDeliveryException("SMTP delivery outcome unknown", true, transport);
        } catch (MessagingException | UnsupportedEncodingException invalidMessage) {
            throw new EmailDeliveryException("Email message rejected before delivery", false, invalidMessage);
        }
    }

    private String html(String code, int validMinutes) {
        return "<div style=\"font-family:sans-serif;color:#17264d\">"
                + "<h2>粤语学习登录验证码</h2>"
                + "<p>你的验证码是：</p>"
                + "<p style=\"font-size:28px;font-weight:700;letter-spacing:6px\">" + code + "</p>"
                + "<p>验证码将在 " + validMinutes + " 分钟内有效，请勿转发给他人。</p>"
                + "<p style=\"color:#7886a5\">如果不是你本人操作，请忽略此邮件。</p>"
                + "</div>";
    }
}
