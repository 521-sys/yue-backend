package com.yue.sms;

public class SmsInvalidCodeException extends IllegalArgumentException {
    public SmsInvalidCodeException() {
        super("验证码无效或已过期");
    }
}
