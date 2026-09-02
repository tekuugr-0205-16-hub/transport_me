-- Users table: one permanent identity per person (§26 — no separate
-- PassengerUser/DriverUser/OwnerUser tables). Roles determine capabilities.
CREATE TABLE users (
                       id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       phone_number    VARCHAR(20) NOT NULL UNIQUE,
                       password_hash   VARCHAR(255) NOT NULL,
                       status          VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
                       created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_phone_number ON users (phone_number);

-- Roles: PASSENGER, DRIVER, OWNER, OPERATOR_ADMIN, PLATFORM_ADMIN
CREATE TABLE roles (
                       id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       name            VARCHAR(50) NOT NULL UNIQUE,
                       description     VARCHAR(255)
);

-- Fine-grained capabilities a role can be granted
CREATE TABLE permissions (
                             id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             name            VARCHAR(100) NOT NULL UNIQUE,
                             description     VARCHAR(255)
);

-- A user can hold multiple roles (e.g. a passenger who later registers a vehicle)
CREATE TABLE user_roles (
                            user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            role_id         BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                            PRIMARY KEY (user_id, role_id)
);

-- A role can be granted many permissions
CREATE TABLE role_permissions (
                                  role_id         BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                                  permission_id   BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
                                  PRIMARY KEY (role_id, permission_id)
);

-- Seed the role set MVP actually needs — matches §3 of the grounded architecture doc
INSERT INTO roles (name, description) VALUES
                                          ('PASSENGER', 'Can search for and track public transport'),
                                          ('DRIVER', 'Can operate an assigned vehicle and share live location'),
                                          ('OWNER', 'Owns one or more vehicles'),
                                          ('OPERATOR_ADMIN', 'Manages an organization''s fleet, drivers and routes'),
                                          ('PLATFORM_ADMIN', 'Full platform administration access');