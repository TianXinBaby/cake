package com.cake.platform.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, 40001, "参数错误"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 50001, "操作失败，请稍后重试");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;

    CommonErrorCode(HttpStatus httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
