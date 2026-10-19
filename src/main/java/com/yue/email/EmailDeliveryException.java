package com.yue.email;

public class EmailDeliveryException extends RuntimeException {
    private final boolean unknownOutcome;

    public EmailDeliveryException(String message, boolean unknownOutcome) {
        super(message);
        this.unknownOutcome = unknownOutcome;
    }

    public EmailDeliveryException(String message, boolean unknownOutcome, Throwable cause) {
        super(message, cause);
        this.unknownOutcome = unknownOutcome;
    }

    public boolean unknownOutcome() { return unknownOutcome; }
}
