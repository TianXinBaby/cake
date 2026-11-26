package com.cake.platform.exception;

import java.util.List;
import java.util.Map;

/** A parameter failure with public field locations and controlled messages, never input values. */
public final class FieldValidationException extends BusinessException {
    private final Map<String, List<String>> fieldErrors;

    public FieldValidationException(Map<String, List<String>> fieldErrors) {
        super(CommonErrorCode.INVALID_PARAMETER);
        this.fieldErrors = ParameterErrorData.copyErrors(fieldErrors);
    }

    public static FieldValidationException forField(String field, String message) {
        return new FieldValidationException(Map.of(field, List.of(message)));
    }

    public Map<String, List<String>> fieldErrors() { return fieldErrors; }
}
