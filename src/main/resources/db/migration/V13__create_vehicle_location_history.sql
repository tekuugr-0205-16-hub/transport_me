CREATE TABLE vehicle_location_history (
                                          id BIGSERIAL PRIMARY KEY,

                                          vehicle_id BIGINT NOT NULL,
                                          submitted_by_user_id BIGINT NOT NULL,

                                          latitude DOUBLE PRECISION NOT NULL,
                                          longitude DOUBLE PRECISION NOT NULL,

                                          speed DOUBLE PRECISION,
                                          heading DOUBLE PRECISION,
                                          accuracy_meters DOUBLE PRECISION,

                                          recorded_at TIMESTAMPTZ NOT NULL,
                                          received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                          CONSTRAINT fk_location_history_vehicle
                                              FOREIGN KEY (vehicle_id)
                                                  REFERENCES vehicles(id),

                                          CONSTRAINT fk_location_history_submitted_by_user
                                              FOREIGN KEY (submitted_by_user_id)
                                                  REFERENCES users(id),

                                          CONSTRAINT chk_location_history_latitude
                                              CHECK (
                                                  latitude >= -90.0
                                                      AND latitude <= 90.0
                                                  ),

                                          CONSTRAINT chk_location_history_longitude
                                              CHECK (
                                                  longitude >= -180.0
                                                      AND longitude <= 180.0
                                                  ),

                                          CONSTRAINT chk_location_history_speed
                                              CHECK (
                                                  speed IS NULL
                                                      OR speed >= 0.0
                                                  ),

                                          CONSTRAINT chk_location_history_heading
                                              CHECK (
                                                  heading IS NULL
                                                      OR (
                                                      heading >= 0.0
                                                          AND heading < 360.0
                                                      )
                                                  ),

                                          CONSTRAINT chk_location_history_accuracy
                                              CHECK (
                                                  accuracy_meters IS NULL
                                                      OR accuracy_meters >= 0.0
                                                  )
);

CREATE INDEX idx_location_history_vehicle_recorded_at
    ON vehicle_location_history(
                                vehicle_id,
                                recorded_at DESC
        );

CREATE INDEX idx_location_history_recorded_at
    ON vehicle_location_history(
                                recorded_at
        );