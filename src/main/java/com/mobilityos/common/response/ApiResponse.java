package com.mobilityos.common.response;

public record ApiResponse<T>(
        boolean success,
        T data,
        ResponseMetadata metadata
) {

    public static <T> ApiResponse<T> success(
            T data,
            String path
    ) {
        return new ApiResponse<>(
                true,
                data,
                ResponseMetadata.of(path)
        );
    }
}