-- An invitation to co-manage a vehicle. The invited person must accept
-- before becoming a VehicleMember with equal access — prevents someone
-- falsely claiming co-ownership of a vehicle they have no real stake in.
CREATE TABLE vehicle_invitations (
                                     id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                     vehicle_id            BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
                                     invited_phone_number  VARCHAR(20) NOT NULL,
                                     member_role           VARCHAR(30) NOT NULL,
                                     invited_by_user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                     status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                                     created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
                                     responded_at          TIMESTAMPTZ NULL
);

CREATE INDEX idx_invitations_vehicle_id ON vehicle_invitations (vehicle_id);
CREATE INDEX idx_invitations_phone_number ON vehicle_invitations (invited_phone_number);
CREATE INDEX idx_invitations_status ON vehicle_invitations (status);