package com.cake.platform.exception;

import org.springframework.http.HttpStatus;

/** Public merchant errors; the same 40404 is used for missing, disabled shop and disabled account. */
public enum PublicMerchantErrorCode implements ErrorCode {
    MERCHANT_NOT_FOUND(HttpStatus.NOT_FOUND, 40404, "商家不存在或不可用");

    private final HttpStatus status;
    private final int code;
    private final String message;

    PublicMerchantErrorCode(HttpStatus status, int code, String message) {
        this.status = status; this.code = code; this.message = message;
    }

    @Override public HttpStatus httpStatus() { return status; }
    @Override public int code() { return code; }
    @Override public String message() { return message; }
}
