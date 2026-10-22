ALTER TABLE dose_occurrences ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE operation_registry (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    medication_id UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    request_hash CHAR(64) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE dose_actions (
    id UUID PRIMARY KEY,
    medication_id UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    occurrence_id UUID NOT NULL REFERENCES dose_occurrences(id) ON DELETE CASCADE,
    client_operation_id UUID NOT NULL UNIQUE REFERENCES operation_registry(id) ON DELETE CASCADE,
    action VARCHAR(16) NOT NULL CHECK (action IN ('TAKEN','SKIPPED','UNDO')),
    undo_of UUID UNIQUE REFERENCES dose_actions(id),
    status_before VARCHAR(16) NOT NULL,
    status_after VARCHAR(16) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    occurrence_version BIGINT NOT NULL,
    CHECK ((action='UNDO') = (undo_of IS NOT NULL))
);
CREATE INDEX dose_actions_occurrence ON dose_actions(occurrence_id,occurrence_version DESC);
CREATE TABLE inventory_snapshot (
    medication_id UUID PRIMARY KEY REFERENCES medications(id) ON DELETE CASCADE,
    quantity NUMERIC(30,6),
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE inventory_ledger (
    id UUID PRIMARY KEY,
    medication_id UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    change_quantity NUMERIC(30,6) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    reason VARCHAR(16) NOT NULL CHECK (reason IN ('OPENING','RESTOCK','CORRECTION','CONSUMPTION','UNDO')),
    dose_action_id UUID UNIQUE REFERENCES dose_actions(id) ON DELETE CASCADE,
    operation_id UUID UNIQUE REFERENCES operation_registry(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (reason<>'OPENING' OR change_quantity>=0),
    CHECK (reason<>'RESTOCK' OR change_quantity>0),
    CHECK (reason<>'CONSUMPTION' OR change_quantity<0),
    CHECK (reason<>'UNDO' OR change_quantity>0),
    CHECK ((reason IN ('CONSUMPTION','UNDO')) = (dose_action_id IS NOT NULL))
);
CREATE UNIQUE INDEX inventory_one_opening ON inventory_ledger(medication_id) WHERE reason='OPENING';
CREATE INDEX inventory_ledger_medication ON inventory_ledger(medication_id,created_at DESC,id);
INSERT INTO inventory_snapshot(medication_id,quantity,updated_at)
SELECT id,initial_stock,created_at FROM medications;
INSERT INTO inventory_ledger(id,medication_id,change_quantity,unit,reason,created_at)
SELECT id,id,initial_stock,unit,'OPENING',created_at FROM medications WHERE initial_stock IS NOT NULL;
-- Append-only audit; allow parent account/medication erasure, never direct audit edits.
CREATE FUNCTION protect_medtrack_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' OR EXISTS (
        SELECT 1 FROM medications m JOIN users u ON u.id=m.user_id WHERE m.id=OLD.medication_id
    ) THEN
        RAISE EXCEPTION 'Audit entries are append-only' USING ERRCODE='23514';
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER immutable_inventory BEFORE UPDATE OR DELETE ON inventory_ledger
FOR EACH ROW EXECUTE FUNCTION protect_medtrack_audit();
CREATE TRIGGER immutable_dose_actions BEFORE UPDATE OR DELETE ON dose_actions
FOR EACH ROW EXECUTE FUNCTION protect_medtrack_audit();
