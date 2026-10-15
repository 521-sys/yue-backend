package com.yue.admin;
public class AdminApiException extends RuntimeException {
 public final int status;
 public AdminApiException(int status,String message){super(message);this.status=status;}
}
