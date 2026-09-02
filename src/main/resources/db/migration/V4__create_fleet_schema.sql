
-- Vehicle is the public identity and the paying entity. operator_id is
-- nullable: a solo owner-driver vehicle has no Organization at all, while
-- a larger fleet vehicle belongs to one.
CREATE TABLE vehicles (
                          id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          operator_id             BIGINT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
                          plate_number            VARCHAR(20) NOT NULL UNIQUE,
                          vehicle_type            VARCHAR(30) NOT NULL,
                          capacity                INTEGER NOT NULL,
                          status                  VARCHAR(30) NOT NULL DEFAULT 'INACTIVE',
                          current_subscription_id BIGINT NULL,
                          created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vehicles_operator_id ON vehicles (operator_id);
CREATE INDEX idx_vehicles_plate_number ON vehicles (plate_number);
CREATE INDEX idx_vehicles_status ON vehicles (status);