-- Keep only one PENDING invitation for the same vehicle + phone number.
-- If old development data already contains duplicates, keep the newest one
-- pending and expire the older duplicates before creating the unique index.
WITH ranked_pending AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY vehicle_id, invited_phone_number
               ORDER BY created_at DESC, id DESC
           ) AS row_number
    FROM vehicle_invitations
    WHERE status = 'PENDING'
)
UPDATE vehicle_invitations invitation
SET status = 'EXPIRED',
    responded_at = COALESCE(invitation.responded_at, now())
FROM ranked_pending ranked
WHERE invitation.id = ranked.id
  AND ranked.row_number > 1;

CREATE UNIQUE INDEX uq_vehicle_invitations_pending_vehicle_phone
    ON vehicle_invitations (vehicle_id, invited_phone_number)
    WHERE status = 'PENDING';

CREATE INDEX idx_vehicle_invitations_phone_status_created
    ON vehicle_invitations (invited_phone_number, status, created_at DESC);
