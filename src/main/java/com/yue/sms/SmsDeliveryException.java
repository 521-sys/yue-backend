package com.yue.sms;

public class SmsDeliveryException extends RuntimeException {
    private final boolean unknownOutcome;

    public SmsDeliveryException(String message, boolean unknownOutcome) {
        super(message);
        this.unknownOutcome = unknownOutcome;
    }

    public SmsDeliveryException(String message, boolean unknownOutcome, Throwable cause) {
        super(message, cause);
        this.unknownOutcome = unknownOutcome;
    }

    public boolean unknownOutcome() {
        return unknownOutcome;
    }
}
