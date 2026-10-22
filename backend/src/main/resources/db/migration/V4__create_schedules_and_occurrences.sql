CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE schedule_versions (
    id UUID PRIMARY KEY,
    medication_id UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version > 0),
    quantity_per_dose NUMERIC(19,6) NOT NULL CHECK (quantity_per_dose > 0),
    timezone VARCHAR(64) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(medication_id, version),
    CHECK (end_date IS NULL OR end_date >= start_date),
    CHECK (effective_until IS NULL OR effective_until >= effective_from),
    EXCLUDE USING gist (medication_id WITH =, tstzrange(effective_from,effective_until,'[)') WITH &&)
);
CREATE TABLE schedule_times (
    id UUID PRIMARY KEY,
    schedule_version_id UUID NOT NULL REFERENCES schedule_versions(id) ON DELETE CASCADE,
    local_time TIME NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    UNIQUE(schedule_version_id, local_time),
    UNIQUE(schedule_version_id, ordinal),
    UNIQUE(id,schedule_version_id),
    CHECK (extract(second from local_time) = 0)
);
CREATE TABLE dose_occurrences (
    id UUID PRIMARY KEY,
    schedule_version_id UUID NOT NULL REFERENCES schedule_versions(id) ON DELETE CASCADE,
    schedule_time_id UUID NOT NULL,
    due_at_utc TIMESTAMPTZ NOT NULL,
    local_date DATE NOT NULL,
    local_time TIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','DUE','TAKEN','SKIPPED','MISSED')),
    medication_name VARCHAR(200) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    FOREIGN KEY(schedule_time_id,schedule_version_id) REFERENCES schedule_times(id,schedule_version_id),
    UNIQUE(schedule_version_id,local_date,schedule_time_id),
    UNIQUE(schedule_version_id,due_at_utc,schedule_time_id)
);
CREATE INDEX dose_occurrences_due ON dose_occurrences(due_at_utc,id);
