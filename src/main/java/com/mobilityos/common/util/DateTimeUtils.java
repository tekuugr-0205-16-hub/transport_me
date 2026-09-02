package com.mobilityos.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public final class DateTimeUtils {

    private DateTimeUtils() {
    }

    public static Instant now() {
        return Instant.now();
    }

    public static ZonedDateTime utcNow() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    public static ZonedDateTime toZone(
            Instant instant,
            ZoneId zoneId
    ) {
        return instant.atZone(zoneId);
    }
}