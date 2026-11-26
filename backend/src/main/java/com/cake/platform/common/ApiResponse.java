package com.cake.platform.common;

/** The three response fields defined by requirement 5.1. */
public record ApiResponse<T>(int code, String msg, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> failure(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
