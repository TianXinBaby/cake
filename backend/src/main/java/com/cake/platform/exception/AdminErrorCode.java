package com.cake.platform.exception;

import org.springframework.http.HttpStatus;

public enum AdminErrorCode implements ErrorCode {
    MERCHANT_NOT_FOUND(40404, "商家不存在或不可用"),
    USER_NOT_MANAGEABLE(40405, "用户不存在或不可管理");
    private final int code;
    private final String message;
    AdminErrorCode(int code, String message) { this.code = code; this.message = message; }
    public HttpStatus httpStatus() { return HttpStatus.NOT_FOUND; }
    public int code() { return code; }
    public String message() { return message; }
}
