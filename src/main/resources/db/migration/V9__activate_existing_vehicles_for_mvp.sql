-- Vehicle status now gates live tracking and passenger discovery.
-- Until a verification/admin approval flow exists, newly created vehicles are ACTIVE
-- so the current MVP remains usable. Existing INACTIVE rows are activated because
-- INACTIVE previously had no behavioral effect.

UPDATE vehicles
SET status = 'ACTIVE'
WHERE status = 'INACTIVE';

ALTER TABLE vehicles
    ALTER COLUMN status SET DEFAULT 'ACTIVE';
