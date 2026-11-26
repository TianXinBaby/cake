package com.cake.platform.exception;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.cake.platform.common.ApiResponse;
import com.cake.platform.config.RequestTraceFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper mapper;

    public GlobalExceptionHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.value() == 400) {
            trace(request, "INVALID_PARAMETER", false);
            String field = exception instanceof MissingServletRequestParameterException missing
                    ? missing.getParameterName() : exception instanceof MethodArgumentTypeMismatchException mismatch
                    ? ValidationErrorExtractor.parameterName(mismatch.getParameter()) : null;
            return parameterError(ParameterErrorData.safeField(field)
                    ? Map.of(field, List.of("参数错误")) : Map.of());
        }
        // Framework-only statuses such as an unimplemented route's 404 have no invented business code.
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var fields = ValidationErrorExtractor.extract(exception.getBindingResult(),
                exception.getParameter().getParameterType(), mapper);
        trace(request, "INVALID_PARAMETER fields=" + fields.size(), false);
        return parameterError(fields);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        if (exception.isForReturnValue()) {
            trace(request, "SYSTEM_ERROR", true);
            return error(CommonErrorCode.INTERNAL_ERROR, Map.of("requestId", requestId(request)));
        }
        trace(request, "INVALID_PARAMETER", false);
        Map<String, List<String>> fields = new LinkedHashMap<>();
        for (var result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                ValidationErrorExtractor.extract(errors, result.getMethodParameter().getParameterType(), mapper)
                        .forEach((field, messages) -> messages.forEach(message ->
                                ValidationErrorExtractor.add(fields, field, message)));
            } else {
                String field = ValidationErrorExtractor.parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(error -> {
                    String[] codes = error.getCodes();
                    ValidationErrorExtractor.add(fields, field, ValidationErrorExtractor.message(
                            error.getDefaultMessage(), codes == null || codes.length == 0 ? null : codes[codes.length - 1]));
                });
            }
        }
        return parameterError(fields);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        trace(request, "INVALID_JSON", false);
        // Unknown properties are not public API fields; syntax/missing-body errors have no field.
        if (exception.getCause() instanceof MismatchedInputException mismatch
                && !(mismatch instanceof UnrecognizedPropertyException) && !mismatch.getPath().isEmpty()) {
            var servlet = ((ServletWebRequest) request).getRequest();
            Object handler = servlet.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
            if (handler instanceof org.springframework.web.method.HandlerMethod method) {
                for (var parameter : method.getMethodParameters()) {
                    if (!parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)) {
                        continue;
                    }
                    StringBuilder raw = new StringBuilder();
                    for (var reference : mismatch.getPath()) {
                        if (reference.getFieldName() != null) {
                            if (!raw.isEmpty()) { raw.append('.'); }
                            raw.append(reference.getFieldName());
                        } else if (reference.getIndex() >= 0) { raw.append('[').append(reference.getIndex()).append(']'); }
                        else { return parameterError(Map.of()); }
                    }
                    String field = ValidationErrorExtractor.jsonPath(parameter.getParameterType(), raw.toString(), mapper);
                    if (field != null) { return parameterError(Map.of(field, List.of("参数类型不正确"))); }
                }
            }
        }
        return parameterError(Map.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception,
                                                    WebRequest request) {
        trace(request, "INVALID_PARAMETER", false);
        return parameterError(ValidationErrorExtractor.violations(exception.getConstraintViolations(), mapper));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> handleBusinessException(BusinessException exception, WebRequest request) {
        trace(request, "BUSINESS_ERROR code=" + exception.errorCode().code(), false);
        if (exception instanceof FieldValidationException validation) {
            return parameterError(validation.fieldErrors());
        }
        Object data = exception.errorCode().httpStatus().is5xxServerError()
                ? Map.of("requestId", requestId(request)) : null;
        return error(exception.errorCode(), data);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnknownException(Exception exception, WebRequest request) {
        // Exception messages, request bodies, query strings and headers can contain secrets.
        trace(request, "SYSTEM_ERROR", true);
        return error(CommonErrorCode.INTERNAL_ERROR, Map.of("requestId", requestId(request)));
    }

    private ResponseEntity<Object> error(ErrorCode errorCode, Object data) {
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode.code(), errorCode.message(), data));
    }

    private ResponseEntity<Object> parameterError(Map<String, List<String>> fields) {
        var data = new ParameterErrorData(fields);
        return error(CommonErrorCode.INVALID_PARAMETER, data.fieldErrors().isEmpty() ? null : data);
    }

    private String requestId(WebRequest request) {
        Object value = request.getAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE, WebRequest.SCOPE_REQUEST);
        if (value instanceof String id) {
            return id;
        }
        String id = UUID.randomUUID().toString();
        request.setAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE, id, WebRequest.SCOPE_REQUEST);
        return id;
    }

    private void trace(WebRequest request, String outcome, boolean systemError) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        Object route = servletRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String pattern = route == null ? "<unmapped>" : route.toString();
        // A route pattern excludes path-variable values and query strings.
        if (systemError) {
            LOG.error("requestId={} method={} route={} outcome={}", requestId(request),
                    servletRequest.getMethod(), pattern, outcome);
        } else {
            LOG.warn("requestId={} method={} route={} outcome={}", requestId(request),
                    servletRequest.getMethod(), pattern, outcome);
        }
    }
}
