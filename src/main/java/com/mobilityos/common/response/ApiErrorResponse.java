package com.mobilityos.common.response;

import com.mobilityos.common.exception.ErrorCode;

import java.util.Map;

public record ApiErrorResponse(
        boolean success,
        ErrorCode code,
        String message,
        Map<String, String> errors,
        ResponseMetadata metadata
) {

    public static ApiErrorResponse of(
            ErrorCode code,
            String message,
            String path
    ) {
        return new ApiErrorResponse(
                false,
                code,
                message,
                null,
                ResponseMetadata.of(path)
        );
    }

    public static ApiErrorResponse validation(
            Map<String, String> errors,
            String path
    ) {
        return new ApiErrorResponse(
                false,
                ErrorCode.VALIDATION_ERROR,
                "Validation failed",
                errors,
                ResponseMetadata.of(path)
        );
    }
}