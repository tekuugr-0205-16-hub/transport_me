package com.mobilityos.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static String toJson(
            ObjectMapper objectMapper,
            Object object
    ) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize object to JSON",
                    exception
            );
        }
    }

    public static <T> T fromJson(
            ObjectMapper objectMapper,
            String json,
            Class<T> targetType
    ) {
        try {
            return objectMapper.readValue(
                    json,
                    targetType
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize JSON",
                    exception
            );
        }
    }
}