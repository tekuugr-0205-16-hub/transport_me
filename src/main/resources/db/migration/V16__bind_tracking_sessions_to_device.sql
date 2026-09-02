ALTER TABLE vehicle_tracking_sessions
    ADD COLUMN device_installation_id UUID;

-- Existing development/history rows were created before
-- device binding existed. Give each one a safe legacy value.
UPDATE vehicle_tracking_sessions
SET device_installation_id = gen_random_uuid()
WHERE device_installation_id IS NULL;

ALTER TABLE vehicle_tracking_sessions
    ALTER COLUMN device_installation_id SET NOT NULL;

-- One physical app installation may actively operate
-- only one tracking session at a time.
CREATE UNIQUE INDEX uk_tracking_sessions_one_active_per_device
    ON vehicle_tracking_sessions(device_installation_id)
    WHERE status = 'ACTIVE';