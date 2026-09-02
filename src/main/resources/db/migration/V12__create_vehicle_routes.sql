CREATE TABLE vehicle_routes (
                                id BIGSERIAL PRIMARY KEY,

                                vehicle_id BIGINT NOT NULL,
                                set_by_user_id BIGINT NOT NULL,

                                origin_name VARCHAR(255) NOT NULL,
                                destination_name VARCHAR(255) NOT NULL,

                                status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

                                started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                ended_at TIMESTAMPTZ,

                                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_vehicle_routes_vehicle
                                    FOREIGN KEY (vehicle_id)
                                        REFERENCES vehicles(id),

                                CONSTRAINT fk_vehicle_routes_set_by_user
                                    FOREIGN KEY (set_by_user_id)
                                        REFERENCES users(id),

                                CONSTRAINT chk_vehicle_routes_status
                                    CHECK (status IN ('ACTIVE', 'ENDED')),

                                CONSTRAINT chk_vehicle_routes_time_state
                                    CHECK (
                                        (status = 'ACTIVE' AND ended_at IS NULL)
                                            OR
                                        (status = 'ENDED' AND ended_at IS NOT NULL)
                                        )
);

CREATE UNIQUE INDEX uk_vehicle_routes_one_active_per_vehicle
    ON vehicle_routes(vehicle_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_vehicle_routes_vehicle_started_at
    ON vehicle_routes(vehicle_id, started_at DESC);