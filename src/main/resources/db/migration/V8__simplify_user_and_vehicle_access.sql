-- MobilityOS MVP identity simplification:
--   * User is an authenticated person, not PASSENGER/OWNER/DRIVER.
--   * Vehicle is the transport/business account.
--   * vehicle_members only grants a user access to a specific vehicle.
-- OWNER/DRIVER relationships and credentials can be introduced later as a
-- separate vehicle-specific domain without changing login identity.

-- Remove global role infrastructure that is no longer used by authentication.
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS roles;

-- Vehicle membership is now pure access membership.
ALTER TABLE vehicle_members
    DROP COLUMN IF EXISTS member_role,
    DROP COLUMN IF EXISTS license_number,
    DROP COLUMN IF EXISTS license_photo_url,
    DROP COLUMN IF EXISTS verification_status;

-- Invitations grant vehicle access; they no longer assign OWNER/DRIVER labels.
ALTER TABLE vehicle_invitations
    DROP COLUMN IF EXISTS member_role;
