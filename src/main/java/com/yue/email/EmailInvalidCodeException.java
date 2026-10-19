package com.yue.email;

public class EmailInvalidCodeException extends IllegalArgumentException {
    public EmailInvalidCodeException() { super("验证码无效或已过期"); }
}
