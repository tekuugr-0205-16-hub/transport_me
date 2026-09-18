package com.mobilityos.location.event.redis;

final class LocationEventDecodingException
        extends RuntimeException {

    LocationEventDecodingException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}