package com.cake.platform.exception;

import org.springframework.http.HttpStatus;

/** Extension point for requirement-defined codes in later authorized phases. */
public interface ErrorCode {
    HttpStatus httpStatus();

    int code();

    String message();
}
