package com.mobilityos.location.event.redis;

import com.mobilityos.location.observation.LocationObservation;
import com.mobilityos.location.observation.LocationSource;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class RedisLocationEventDecoder {

    public LocationObservation decode(
            MapRecord<String, String, String> record
    ) {
        Objects.requireNonNull(
                record,
                "record must not be null"
        );

        Map<String, String> fields =
                record.getValue();

        return new LocationObservation(
                UUID.fromString(
                        required(
                                record,
                                fields,
                                "observationId"
                        )
                ),
                UUID.fromString(
                        required(
                                record,
                                fields,
                                "trackingSessionId"
                        )
                ),
                Long.parseLong(
                        required(
                                record,
                                fields,
                                "sequenceNumber"
                        )
                ),
                Long.valueOf(
                        required(
                                record,
                                fields,
                                "vehicleId"
                        )
                ),
                Long.valueOf(
                        required(
                                record,
                                fields,
                                "submittedByUserId"
                        )
                ),
                UUID.fromString(
                        required(
                                record,
                                fields,
                                "deviceInstallationId"
                        )
                ),
                Double.parseDouble(
                        required(
                                record,
                                fields,
                                "latitude"
                        )
                ),
                Double.parseDouble(
                        required(
                                record,
                                fields,
                                "longitude"
                        )
                ),
                optionalDouble(
                        fields,
                        "speedMetersPerSecond"
                ),
                optionalDouble(
                        fields,
                        "headingDegrees"
                ),
                Double.parseDouble(
                        required(
                                record,
                                fields,
                                "accuracyMeters"
                        )
                ),
                Instant.parse(
                        required(
                                record,
                                fields,
                                "recordedAt"
                        )
                ),
                Instant.parse(
                        required(
                                record,
                                fields,
                                "receivedAt"
                        )
                ),
                LocationSource.valueOf(
                        required(
                                record,
                                fields,
                                "source"
                        )
                )
        );
    }

    private String required(
            MapRecord<String, String, String> record,
            Map<String, String> fields,
            String field
    ) {
        String value =
                fields.get(field);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required location event field '"
                            + field
                            + "' in Redis stream record "
                            + record.getId()
            );
        }

        return value;
    }

    private Double optionalDouble(
            Map<String, String> fields,
            String field
    ) {
        String value =
                fields.get(field);

        if (value == null || value.isBlank()) {
            return null;
        }

        return Double.valueOf(value);
    }
}