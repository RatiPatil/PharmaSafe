-- Controlled training/demo workflow and case-specific review history.
ALTER TABLE cases DROP CONSTRAINT IF EXISTS chk_cases_workflow_state;
ALTER TABLE cases ADD CONSTRAINT chk_cases_workflow_state CHECK (
    workflow_state IN (
        'Draft', 'Intake', 'Triage', 'Data Entry', 'Medical Review',
        'Quality Review', 'QC Review', 'Approved', 'Locked', 'Closed', 'Submitted'
    )
);

ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS case_id BIGINT REFERENCES cases(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_audit_events_case_id ON audit_events(case_id);

CREATE TABLE IF NOT EXISTS case_review_events (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    reviewer_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reviewer_username VARCHAR(64) NOT NULL,
    from_state VARCHAR(32) NOT NULL,
    to_state VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    comments TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_case_review_events_case_id ON case_review_events(case_id);
