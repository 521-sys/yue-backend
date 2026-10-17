package com.yue.sms;

public interface SmsSender {
    SmsSendReceipt sendLoginCode(String phone, String code, int validMinutes);
}
