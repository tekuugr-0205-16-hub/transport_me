package com.mobilityos.common.response;

import java.time.Instant;

public record ResponseMetadata(
        Instant timestamp,
        String path
) {

    public static ResponseMetadata of(String path) {
        return new ResponseMetadata(
                Instant.now(),
                path
        );
    }
}