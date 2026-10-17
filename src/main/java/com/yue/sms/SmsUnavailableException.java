package com.yue.sms;

public class SmsUnavailableException extends RuntimeException {
    public SmsUnavailableException(String message) {
        super(message);
    }
}
