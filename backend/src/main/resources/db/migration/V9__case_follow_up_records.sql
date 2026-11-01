-- Append-only receipts/amendments linked to the existing case and original actor.
ALTER TABLE case_action_items ADD COLUMN action_code VARCHAR(32) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE case_action_items ADD CONSTRAINT chk_action_code CHECK (action_code IN ('GENERAL','FOLLOW_UP'));
ALTER TABLE case_action_items ADD CONSTRAINT uq_action_case_id UNIQUE (case_id,id);
ALTER TABLE cases ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE cases ADD COLUMN closed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE cases ADD CONSTRAINT chk_closed_case_unlocked CHECK (workflow_state <> 'Closed' OR locked = FALSE) NOT VALID;
CREATE TABLE case_follow_ups (
 id BIGSERIAL PRIMARY KEY,
 case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE RESTRICT,
 follow_up_number INTEGER NOT NULL CHECK (follow_up_number > 0),
 kind VARCHAR(16) NOT NULL CHECK (kind IN ('FOLLOW_UP','AMENDMENT')),
 received_date DATE NOT NULL,
 safety_received_date DATE,
 significant BOOLEAN NOT NULL DEFAULT FALSE,
 justification TEXT NOT NULL CHECK (length(trim(justification)) > 0),
 summary TEXT NOT NULL CHECK (length(trim(summary)) > 0),
 source_reference VARCHAR(255),
 previous_case_version INTEGER NOT NULL CHECK (previous_case_version > 0),
 case_version INTEGER NOT NULL,
 related_action_item_id BIGINT,
 created_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 created_by_username VARCHAR(64) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uq_follow_up_number UNIQUE (case_id,follow_up_number),
 CONSTRAINT chk_follow_up_version CHECK (case_version > previous_case_version),
 CONSTRAINT chk_follow_up_dates CHECK (safety_received_date IS NULL OR safety_received_date >= received_date),
 CONSTRAINT chk_amendment_fields CHECK (kind <> 'AMENDMENT' OR (safety_received_date IS NULL AND related_action_item_id IS NULL)),
 CONSTRAINT fk_follow_up_action_case FOREIGN KEY (case_id,related_action_item_id) REFERENCES case_action_items(case_id,id) ON DELETE RESTRICT
);
CREATE INDEX idx_follow_up_case_received ON case_follow_ups(case_id,received_date DESC,id DESC);
CREATE INDEX idx_follow_up_related_action ON case_follow_ups(related_action_item_id);
CREATE INDEX idx_patient_identifier_match ON case_patients(lower(trim(patient_identifier)),case_id);
CREATE INDEX idx_product_name_match ON case_products(lower(trim(product_name)),case_id);
CREATE INDEX idx_event_verbatim_match ON case_events(lower(trim(event_verbatim)),case_id);
