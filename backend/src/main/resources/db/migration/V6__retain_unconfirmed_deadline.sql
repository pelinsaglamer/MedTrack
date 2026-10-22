-- MISSED records a confirmation deadline, never proof of medication non-intake.
ALTER TABLE dose_occurrences ADD COLUMN missed_at TIMESTAMPTZ;
