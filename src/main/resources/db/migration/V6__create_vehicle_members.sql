
-- Both owner and driver members of a vehicle get equal access to its
-- account: view tracking, manage subscription, see reports. member_role
-- is descriptive only, not a permission tier. License fields are only
-- populated when member_role = DRIVER.
CREATE TABLE vehicle_members (
                                 id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 vehicle_id          BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
                                 user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                 member_role         VARCHAR(30) NOT NULL,
                                 license_number      VARCHAR(50) NULL UNIQUE,
                                 license_photo_url   VARCHAR(500) NULL,
                                 verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                                 created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                                 updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                                 UNIQUE (vehicle_id, user_id)
);

CREATE INDEX idx_vehicle_members_vehicle_id ON vehicle_members (vehicle_id);
CREATE INDEX idx_vehicle_members_user_id ON vehicle_members (user_id);