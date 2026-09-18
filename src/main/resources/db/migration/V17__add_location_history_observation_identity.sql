ALTER TABLE vehicle_location_history
    ADD COLUMN observation_id UUID,
    ADD COLUMN tracking_session_id UUID,
    ADD COLUMN sequence_number BIGINT;

ALTER TABLE vehicle_location_history
    ADD CONSTRAINT chk_location_history_canonical_identity
        CHECK (
            (
                observation_id IS NULL
                    AND tracking_session_id IS NULL
                    AND sequence_number IS NULL
                )
                OR
            (
                observation_id IS NOT NULL
                    AND tracking_session_id IS NOT NULL
                    AND sequence_number IS NOT NULL
                    AND sequence_number > 0
                )
            );

CREATE UNIQUE INDEX uq_location_history_observation_id
    ON vehicle_location_history (
                                 observation_id
        );

CREATE UNIQUE INDEX uq_location_history_session_sequence
    ON vehicle_location_history (
                                 tracking_session_id,
                                 sequence_number
        );