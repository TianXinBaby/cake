package com.cake.platform.security;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import com.cake.platform.common.ApiResponse;
import com.cake.platform.config.RequestTraceFilter;
import com.cake.platform.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Existing authentication envelope; fieldErrors is reserved for located 40001 parameter failures. */
public final class AuthErrorResponses {
    private AuthErrorResponses() { }
    public static Object data(BusinessException error, HttpServletRequest request) {
        if (error instanceof AuthFailure failure && failure.lockedUntil() != null) {
            return Map.of("lockedUntil", failure.lockedUntil());
        }
        if (error.errorCode().httpStatus().is5xxServerError()) {
            return Map.of("requestId", requestId(request));
        }
        return null;
    }
    public static void write(ObjectMapper json, HttpServletRequest request, HttpServletResponse response,
                             BusinessException error) throws IOException {
        String id = requestId(request);
        var logger = org.slf4j.LoggerFactory.getLogger(AuthErrorResponses.class);
        if (error.errorCode().httpStatus().is5xxServerError()) {
            logger.error("requestId={} method={} outcome=AUTH_ERROR code={}", id, request.getMethod(), error.errorCode().code());
        } else {
            logger.warn("requestId={} method={} outcome=AUTH_ERROR code={}", id, request.getMethod(), error.errorCode().code());
        }
        response.setStatus(error.errorCode().httpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ApiResponse.failure(error.errorCode().code(),
                error.errorCode().message(), data(error, request)));
    }
    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE);
        if (value != null) { return value.toString(); }
        String id = UUID.randomUUID().toString();
        request.setAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE, id);
        return id;
    }
}
