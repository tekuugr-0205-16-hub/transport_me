
-- Links a User to an Organization they manage (larger fleets only).
CREATE TABLE organization_members (
                                      id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                      organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
                                      user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                      member_role     VARCHAR(30) NOT NULL DEFAULT 'OWNER',
                                      created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                      updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                      UNIQUE (organization_id, user_id)
);

CREATE INDEX idx_org_members_org_id ON organization_members (organization_id);
CREATE INDEX idx_org_members_user_id ON organization_members (user_id);