-- Additive migration; existing cases, accounts and review history are retained.
ALTER TABLE users ADD COLUMN account_row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE case_review_events ADD COLUMN patient_reviewed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE case_review_events ADD COLUMN products_events_reviewed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE case_review_events ADD COLUMN medical_reviewed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_actions_open_due ON case_action_items(status, due_at, assigned_user_id);

-- Enforce evidence on new approvals while retaining unverified historical decisions.
ALTER TABLE case_review_events ADD CONSTRAINT chk_review_approval_evidence
    CHECK (to_state <> 'Approved' OR (patient_reviewed AND products_events_reviewed AND medical_reviewed)) NOT VALID;
