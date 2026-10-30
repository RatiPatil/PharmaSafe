CREATE TABLE IF NOT EXISTS case_contact_logs (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE RESTRICT,
    contact_at TIMESTAMP WITH TIME ZONE NOT NULL,
    contact_type VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    responsible_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_contact_log_type CHECK (contact_type IN ('Telephone', 'Email', 'Portal', 'Letter', 'Other'))
);

CREATE INDEX IF NOT EXISTS idx_contact_logs_case ON case_contact_logs(case_id, contact_at DESC);
CREATE INDEX IF NOT EXISTS idx_contact_logs_responsible_user ON case_contact_logs(responsible_user_id, contact_at DESC);

CREATE TABLE IF NOT EXISTS case_action_items (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE RESTRICT,
    description TEXT NOT NULL,
    assigned_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version_number INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_action_item_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_action_item_completion CHECK (
        (status IN ('COMPLETED', 'CANCELLED') AND completed_by_user_id IS NOT NULL AND completed_at IS NOT NULL)
        OR (status IN ('OPEN', 'IN_PROGRESS') AND completed_by_user_id IS NULL AND completed_at IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_action_items_case ON case_action_items(case_id);
CREATE INDEX IF NOT EXISTS idx_action_items_assignee_status_due ON case_action_items(assigned_user_id, status, due_at);
