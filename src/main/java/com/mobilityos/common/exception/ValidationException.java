package com.mobilityos.common.exception;

import java.util.Map;

public class ValidationException extends ApiException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super(ErrorCode.VALIDATION_ERROR, "Validation failed");
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}