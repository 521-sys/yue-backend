package com.yue.sms;

public class SmsUnavailableException extends RuntimeException {
    public SmsUnavailableException() {
        super("SMS login is unavailable");
    }

    public SmsUnavailableException(String message) {
        super(message);
    }
}
