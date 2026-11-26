package com.cake.platform.security;

import com.cake.platform.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, 40101, "账号或密码错误"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, 40102, "登录已失效，请重新登录"),
    DISABLED(HttpStatus.FORBIDDEN, 40301, "账号已被禁用，请联系管理员"),
    FORBIDDEN(HttpStatus.FORBIDDEN, 40302, "无权执行此操作"),
    DUPLICATE(HttpStatus.CONFLICT, 40901, "该手机号已注册"),
    LOCKED(HttpStatus.LOCKED, 42301, "账号已被临时锁定，请稍后重试");

    private final HttpStatus status;
    private final int code;
    private final String message;
    AuthErrorCode(HttpStatus status, int code, String message) {
        this.status = status; this.code = code; this.message = message;
    }
    public HttpStatus httpStatus() { return status; }
    public int code() { return code; }
    public String message() { return message; }
}
