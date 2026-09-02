CREATE TABLE vehicle_tracking_sessions (
                                           id UUID PRIMARY KEY,

                                           vehicle_id BIGINT NOT NULL,
                                           user_id BIGINT NOT NULL,

                                           status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

                                           started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           ended_at TIMESTAMPTZ,

                                           created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                           CONSTRAINT fk_tracking_sessions_vehicle
                                               FOREIGN KEY (vehicle_id)
                                                   REFERENCES vehicles(id),

                                           CONSTRAINT fk_tracking_sessions_user
                                               FOREIGN KEY (user_id)
                                                   REFERENCES users(id),

                                           CONSTRAINT chk_tracking_sessions_status
                                               CHECK (
                                                   status IN ('ACTIVE', 'ENDED')
                                                   ),

                                           CONSTRAINT chk_tracking_sessions_time_state
                                               CHECK (
                                                   (
                                                       status = 'ACTIVE'
                                                           AND ended_at IS NULL
                                                       )
                                                       OR
                                                   (
                                                       status = 'ENDED'
                                                           AND ended_at IS NOT NULL
                                                       )
                                                   )
);

CREATE UNIQUE INDEX uk_tracking_sessions_one_active_per_vehicle
    ON vehicle_tracking_sessions(vehicle_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uk_tracking_sessions_one_active_per_user
    ON vehicle_tracking_sessions(user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_tracking_sessions_vehicle_started_at
    ON vehicle_tracking_sessions(
                                 vehicle_id,
                                 started_at DESC
        );

CREATE INDEX idx_tracking_sessions_user_started_at
    ON vehicle_tracking_sessions(
                                 user_id,
                                 started_at DESC
        );