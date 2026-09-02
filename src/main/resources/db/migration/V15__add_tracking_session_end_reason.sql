ALTER TABLE vehicle_tracking_sessions
    ADD COLUMN end_reason VARCHAR(40);

-- Defensive backfill in case an ENDED session already exists
-- before this migration is applied.
UPDATE vehicle_tracking_sessions
SET end_reason = 'UNSPECIFIED'
WHERE status = 'ENDED'
  AND end_reason IS NULL;

ALTER TABLE vehicle_tracking_sessions
    ADD CONSTRAINT chk_tracking_sessions_end_reason
        CHECK (
            (
                status = 'ACTIVE'
                    AND end_reason IS NULL
                )
                OR
            (
                status = 'ENDED'
                    AND end_reason IN (
                                       'USER_ENDED',
                                       'HANDOVER',
                                       'MEMBERSHIP_REVOKED',
                                       'VEHICLE_DEACTIVATED',
                                       'SESSION_TIMEOUT',
                                       'ADMIN_TERMINATED',
                                       'UNSPECIFIED'
                    )
                )
            );