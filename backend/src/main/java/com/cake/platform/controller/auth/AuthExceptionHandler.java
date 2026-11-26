package com.cake.platform.controller.auth;

import com.cake.platform.common.ApiResponse;
import com.cake.platform.security.AuthErrorResponses;
import com.cake.platform.security.AuthFailure;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
@Order(-1)
public class AuthExceptionHandler {
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Object> accessDenied(org.springframework.security.access.AccessDeniedException ignored,
                                        HttpServletRequest request) {
        return authFailure(new AuthFailure(com.cake.platform.security.AuthErrorCode.FORBIDDEN), request);
    }
    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<Object> authFailure(AuthFailure failure, HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("requestId={} method={} outcome=AUTH_ERROR code={}",
                request.getAttribute(com.cake.platform.config.RequestTraceFilter.REQUEST_ID_ATTRIBUTE),
                request.getMethod(), failure.errorCode().code());
        return ResponseEntity.status(failure.errorCode().httpStatus()).body(ApiResponse.failure(
                failure.errorCode().code(), failure.errorCode().message(), AuthErrorResponses.data(failure, request)));
    }
}
