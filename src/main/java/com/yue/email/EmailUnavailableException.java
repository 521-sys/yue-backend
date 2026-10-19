package com.yue.email;

public class EmailUnavailableException extends RuntimeException {
    public EmailUnavailableException() { super("Email login is unavailable"); }
    public EmailUnavailableException(String message) { super(message); }
}
