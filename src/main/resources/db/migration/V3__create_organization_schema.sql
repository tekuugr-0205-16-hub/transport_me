CREATE TABLE organizations (
                               id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               name                VARCHAR(255) NOT NULL,
                               type                VARCHAR(30) NOT NULL,
                               verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                               created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                               updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_organizations_type ON organizations (type);