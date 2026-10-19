package com.yue.email;

public interface EmailSender {
    EmailSendReceipt sendLoginCode(String email, String code, int validMinutes);
}
