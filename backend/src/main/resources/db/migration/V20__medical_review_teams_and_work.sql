-- Separate Medical Review hierarchy. Team leads do not acquire clinical review authority.
INSERT INTO workspace_roles(code,display_name,workspace,assignable) VALUES('ROLE_MEDICAL_TEAM_LEAD','Medical Review Team Lead','case-medical-teams',FALSE);
CREATE TABLE mr_teams (
 id BIGSERIAL PRIMARY KEY,
 team_code VARCHAR(40) GENERATED ALWAYS AS ('MR-TL-' || CASE WHEN length(id::text)<3 THEN lpad(id::text,3,'0') ELSE id::text END) STORED UNIQUE,
 name VARCHAR(100) NOT NULL CHECK(length(trim(name))>0),
 lead_user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
 registration_key VARCHAR(36) NOT NULL UNIQUE, registration_open BOOLEAN NOT NULL DEFAULT TRUE,
 active BOOLEAN NOT NULL DEFAULT TRUE, max_members INT NOT NULL DEFAULT 10 CHECK(max_members BETWEEN 1 AND 10),
 created_by_user_id BIGINT NOT NULL REFERENCES users(id),created_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE registration_requests ADD COLUMN mr_team_id BIGINT REFERENCES mr_teams(id);
CREATE INDEX idx_registration_mr_team ON registration_requests(mr_team_id,created_at DESC);
CREATE TABLE mr_team_members (
 id BIGSERIAL PRIMARY KEY,team_id BIGINT NOT NULL REFERENCES mr_teams(id),user_id BIGINT NOT NULL REFERENCES users(id),
 status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','ACTIVE','REJECTED','SUSPENDED','LEFT')),
 joined_at TIMESTAMPTZ NOT NULL,UNIQUE(team_id,user_id)
);
CREATE UNIQUE INDEX uq_mr_current_membership ON mr_team_members(user_id) WHERE status IN ('PENDING','ACTIVE');
CREATE INDEX idx_mr_members_team_status ON mr_team_members(team_id,status,user_id);
CREATE TABLE mr_work_orders (
 id BIGSERIAL PRIMARY KEY,team_id BIGINT NOT NULL REFERENCES mr_teams(id),
 title VARCHAR(160) NOT NULL CHECK(length(trim(title))>0),description VARCHAR(5000) NOT NULL CHECK(length(trim(description))>0),
 instructions VARCHAR(5000) NOT NULL,
 stage VARCHAR(32) NOT NULL CHECK(stage IN ('SOURCE_REVIEW','CLINICAL_ASSESSMENT','SERIOUSNESS_REVIEW','CAUSALITY','EXPECTEDNESS','MEDICAL_NARRATIVE','QC_RESPONSE','QUALITY_HANDOFF')),
 priority VARCHAR(16) NOT NULL CHECK(priority IN ('Routine','Priority','Expedited')),
 due_at TIMESTAMPTZ NOT NULL, case_id BIGINT REFERENCES cases(id),
 clinical_required BOOLEAN NOT NULL DEFAULT TRUE,
 recipient_mode VARCHAR(16) NOT NULL CHECK(recipient_mode IN ('INDIVIDUAL','ALL_TEAM')),
 created_by_user_id BIGINT NOT NULL REFERENCES users(id),created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(id,team_id),CHECK(recipient_mode<>'ALL_TEAM' OR case_id IS NULL)
);
CREATE INDEX idx_mr_work_orders_team ON mr_work_orders(team_id,created_at DESC);
CREATE TABLE mr_work_assignments (
 id BIGSERIAL PRIMARY KEY,work_order_id BIGINT NOT NULL,team_id BIGINT NOT NULL,employee_user_id BIGINT NOT NULL REFERENCES users(id),
 status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED' CHECK(status IN ('NOT_STARTED','IN_PROGRESS','BLOCKED','COMPLETED')),
 progress INT NOT NULL DEFAULT 0 CHECK(progress BETWEEN 0 AND 100),
 row_version BIGINT NOT NULL DEFAULT 0,supervisor_note VARCHAR(2000) NOT NULL DEFAULT '',reopened_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(work_order_id,employee_user_id),UNIQUE(id,employee_user_id),
 FOREIGN KEY(work_order_id,team_id) REFERENCES mr_work_orders(id,team_id),
 FOREIGN KEY(team_id,employee_user_id) REFERENCES mr_team_members(team_id,user_id),
 CHECK((status='COMPLETED')=(progress=100)),CHECK(status<>'NOT_STARTED' OR progress=0)
);
CREATE INDEX idx_mr_assignment_employee ON mr_work_assignments(employee_user_id,status,updated_at DESC);
CREATE INDEX idx_mr_assignment_team ON mr_work_assignments(team_id,status,updated_at DESC);
CREATE TABLE mr_work_updates (
 id BIGSERIAL PRIMARY KEY,assignment_id BIGINT NOT NULL,employee_user_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('IN_PROGRESS','BLOCKED','COMPLETED')),
 progress INT NOT NULL CHECK(progress BETWEEN 0 AND 100),
 work_done VARCHAR(5000) NOT NULL CHECK(length(trim(work_done))>0),pending_work VARCHAR(3000) NOT NULL,remaining_work VARCHAR(3000) NOT NULL,
 blockers VARCHAR(2000) NOT NULL,reason VARCHAR(2000) NOT NULL CHECK(length(trim(reason))>0),
 checklist_json JSONB NOT NULL CHECK(jsonb_typeof(checklist_json)='object'),evidence_reference VARCHAR(500) NOT NULL,
 source_assignment_version BIGINT NOT NULL,recorded_at TIMESTAMPTZ NOT NULL,
 UNIQUE(assignment_id,source_assignment_version),UNIQUE(id,assignment_id),
 FOREIGN KEY(assignment_id,employee_user_id) REFERENCES mr_work_assignments(id,employee_user_id),
 CHECK((status='COMPLETED')=(progress=100)),CHECK(status<>'COMPLETED' OR (length(trim(pending_work))=0 AND length(trim(remaining_work))=0))
);
CREATE INDEX idx_mr_update_assignment ON mr_work_updates(assignment_id,recorded_at DESC,id DESC);
CREATE TABLE mr_work_documents (
 id VARCHAR(36) PRIMARY KEY,assignment_id BIGINT NOT NULL REFERENCES mr_work_assignments(id),update_id BIGINT,
 document_kind VARCHAR(24) NOT NULL CHECK(document_kind IN ('ASSIGNMENT','EMPLOYEE_RESPONSE')),
 generated_by_user_id BIGINT NOT NULL REFERENCES users(id),generated_at TIMESTAMPTZ NOT NULL,
 snapshot_json JSONB NOT NULL,pdf_bytes BYTEA NOT NULL CHECK(octet_length(pdf_bytes)>0),
 pdf_sha256 VARCHAR(64) NOT NULL CHECK(pdf_sha256 ~ '^[a-f0-9]{64}$'),file_name VARCHAR(180) NOT NULL,
 FOREIGN KEY(update_id,assignment_id) REFERENCES mr_work_updates(id,assignment_id),
 CHECK((document_kind='ASSIGNMENT' AND update_id IS NULL) OR (document_kind='EMPLOYEE_RESPONSE' AND update_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_mr_assignment_document ON mr_work_documents(assignment_id) WHERE document_kind='ASSIGNMENT';
CREATE UNIQUE INDEX uq_mr_update_document ON mr_work_documents(update_id) WHERE update_id IS NOT NULL;
CREATE INDEX idx_mr_documents_assignment ON mr_work_documents(assignment_id,generated_at DESC,id);
CREATE TRIGGER trg_mr_order_immutable BEFORE UPDATE OR DELETE ON mr_work_orders FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_mr_update_immutable BEFORE UPDATE OR DELETE ON mr_work_updates FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_mr_document_immutable BEFORE UPDATE OR DELETE ON mr_work_documents FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE FUNCTION verify_mr_team() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM application_role FROM role_admin_assignments WHERE application_role='ROLE_MEDICAL_REVIEWER' FOR UPDATE;
 IF NEW.active AND NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.lead_user_id AND role='ROLE_MEDICAL_TEAM_LEAD' AND enabled AND account_status='APPROVED' AND NOT locked) THEN
  RAISE EXCEPTION 'An active Medical Review team requires an approved Team Lead' USING ERRCODE='23514';
 END IF;
 IF NEW.active AND (SELECT count(*) FROM mr_teams WHERE active AND id<>NEW.id)>=10 THEN
  RAISE EXCEPTION 'Medical Review supports up to ten active teams' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_mr_team_identity BEFORE INSERT OR UPDATE ON mr_teams FOR EACH ROW EXECUTE FUNCTION verify_mr_team();
CREATE FUNCTION verify_mr_member() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE seats INT;
BEGIN
 SELECT max_members INTO seats FROM mr_teams WHERE id=NEW.team_id FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND role='ROLE_MEDICAL_REVIEWER') THEN
  RAISE EXCEPTION 'Only Medical Review employees can join a medical team' USING ERRCODE='23514';
 END IF;
 IF NEW.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND account_status='APPROVED' AND enabled AND NOT locked) THEN
  RAISE EXCEPTION 'Active team membership requires an approved employee' USING ERRCODE='23514';
 END IF;
 IF NEW.status IN ('PENDING','ACTIVE') AND (SELECT count(*) FROM mr_team_members WHERE team_id=NEW.team_id AND id<>NEW.id AND status IN ('PENDING','ACTIVE'))>=seats THEN
  RAISE EXCEPTION 'This team has no remaining employee seats' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_mr_member_identity BEFORE INSERT OR UPDATE ON mr_team_members FOR EACH ROW EXECUTE FUNCTION verify_mr_member();
CREATE FUNCTION preserve_mr_user_role() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.role<>OLD.role AND (EXISTS(SELECT 1 FROM mr_teams WHERE lead_user_id=NEW.id AND active) OR EXISTS(SELECT 1 FROM mr_team_members WHERE user_id=NEW.id AND status IN ('PENDING','ACTIVE'))) THEN
  RAISE EXCEPTION 'Release the Medical Review team relationship before changing role' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_mr_user_role BEFORE UPDATE OF role ON users FOR EACH ROW EXECUTE FUNCTION preserve_mr_user_role();
-- Keep memberships and capacities consistent even when an existing account-status API is used.
CREATE FUNCTION sync_mr_member_account_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 UPDATE mr_team_members SET status=CASE WHEN NEW.account_status='PENDING' THEN 'PENDING' WHEN NEW.account_status='REJECTED' THEN 'REJECTED' WHEN NEW.account_status='APPROVED' AND NEW.enabled THEN 'ACTIVE' ELSE 'SUSPENDED' END
 WHERE user_id=NEW.id AND status IN ('PENDING','ACTIVE','SUSPENDED')
 AND status IS DISTINCT FROM CASE WHEN NEW.account_status='PENDING' THEN 'PENDING' WHEN NEW.account_status='REJECTED' THEN 'REJECTED' WHEN NEW.account_status='APPROVED' AND NEW.enabled THEN 'ACTIVE' ELSE 'SUSPENDED' END;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_mr_member_account_state AFTER UPDATE OF enabled,account_status ON users FOR EACH ROW EXECUTE FUNCTION sync_mr_member_account_state();
ALTER TABLE registration_requests ADD CONSTRAINT chk_mr_registration_role CHECK(mr_team_id IS NULL OR requested_role='ROLE_MEDICAL_REVIEWER');
ALTER TABLE registration_requests ADD CONSTRAINT fk_mr_registration_membership FOREIGN KEY(mr_team_id,user_id) REFERENCES mr_team_members(team_id,user_id) DEFERRABLE INITIALLY DEFERRED;
-- Recipients and work ownership are fixed after the original assignment PDF is delivered.
CREATE FUNCTION verify_mr_assignment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND (NEW.employee_user_id<>OLD.employee_user_id OR NEW.team_id<>OLD.team_id OR NEW.work_order_id<>OLD.work_order_id) THEN
  RAISE EXCEPTION 'Assignment recipients and team ownership are permanent' USING ERRCODE='23514';
 END IF;
 IF TG_OP='INSERT' AND NOT EXISTS(SELECT 1 FROM mr_team_members m JOIN users u ON u.id=m.user_id JOIN mr_teams t ON t.id=m.team_id WHERE m.team_id=NEW.team_id AND m.user_id=NEW.employee_user_id AND m.status='ACTIVE' AND t.active AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked) THEN
  RAISE EXCEPTION 'Only an active employee of the selected team can receive work' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_mr_assignment_identity BEFORE INSERT OR UPDATE ON mr_work_assignments FOR EACH ROW EXECUTE FUNCTION verify_mr_assignment();
ALTER TABLE registration_requests ADD CONSTRAINT chk_exclusive_department_team CHECK(cp_team_id IS NULL OR mr_team_id IS NULL);
CREATE TABLE mr_case_allocations (
 case_id BIGINT PRIMARY KEY REFERENCES cases(id),team_id BIGINT NOT NULL REFERENCES mr_teams(id),allocated_by BIGINT NOT NULL REFERENCES users(id),
 return_processor_id BIGINT REFERENCES users(id),reason VARCHAR(2000) NOT NULL CHECK(length(trim(reason))>0),allocated_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_mr_allocation_team ON mr_case_allocations(team_id,case_id);
CREATE TABLE mr_case_allocation_events (
 id BIGSERIAL PRIMARY KEY,case_id BIGINT NOT NULL REFERENCES cases(id),previous_team_id BIGINT REFERENCES mr_teams(id),team_id BIGINT NOT NULL REFERENCES mr_teams(id),actor_id BIGINT NOT NULL REFERENCES users(id),
 reason VARCHAR(2000) NOT NULL CHECK(length(trim(reason))>0),recorded_at TIMESTAMPTZ NOT NULL,source_case_version BIGINT NOT NULL CHECK(source_case_version>=0)
);
CREATE INDEX idx_mr_allocation_history ON mr_case_allocation_events(case_id,id DESC);
CREATE TABLE mr_review_conclusions (
 id BIGSERIAL PRIMARY KEY,assignment_id BIGINT NOT NULL REFERENCES mr_work_assignments(id),case_id BIGINT NOT NULL REFERENCES cases(id),employee_id BIGINT NOT NULL REFERENCES users(id),
 source_case_version BIGINT NOT NULL CHECK(source_case_version>=0),medical_narrative VARCHAR(5000) NOT NULL CHECK(length(trim(medical_narrative))>0),review_summary VARCHAR(3000) NOT NULL CHECK(length(trim(review_summary))>0),
 assessed_seriousness VARCHAR(20) NOT NULL CHECK(assessed_seriousness IN ('SERIOUS','NON_SERIOUS','UNDETERMINED')),severity_summary VARCHAR(1000) NOT NULL CHECK(length(trim(severity_summary))>0),
 source_reviewed BOOLEAN NOT NULL CHECK(source_reviewed),timeline_reviewed BOOLEAN NOT NULL CHECK(timeline_reviewed),recorded_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(assignment_id,employee_id) REFERENCES mr_work_assignments(id,employee_user_id)
);
CREATE INDEX idx_mr_conclusion_revision ON mr_review_conclusions(assignment_id,source_case_version,id DESC);
CREATE FUNCTION guard_mr_case_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users u LEFT JOIN role_admin_assignments a ON a.administrator_id=u.id WHERE u.id=NEW.allocated_by AND u.enabled AND NOT u.locked AND u.account_status='APPROVED' AND (u.role='ROLE_ADMIN' OR u.role='ROLE_SCOPED_ADMIN' AND a.application_role='ROLE_MEDICAL_REVIEWER')) THEN RAISE EXCEPTION 'Medical department administrator required for case allocation' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM cases WHERE id=NEW.case_id AND workflow_state='Medical Review' AND NOT locked) OR NOT EXISTS(SELECT 1 FROM mr_teams WHERE id=NEW.team_id AND active) THEN RAISE EXCEPTION 'Active medical team and unlocked Medical Review case required' USING ERRCODE='23514'; END IF;
 IF NEW.return_processor_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.return_processor_id AND role IN ('ROLE_CASE_PROCESSOR','ROLE_USER')) THEN RAISE EXCEPTION 'Correction recipient must be a Case Processor' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF NEW.case_id<>OLD.case_id THEN RAISE EXCEPTION 'Allocation case identity is fixed' USING ERRCODE='23514'; END IF;
  IF EXISTS(SELECT 1 FROM mr_work_assignments w JOIN mr_work_orders o ON o.id=w.work_order_id WHERE o.case_id=NEW.case_id AND w.status<>'COMPLETED') THEN RAISE EXCEPTION 'Outstanding medical work prevents reallocation' USING ERRCODE='23514'; END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_mr_allocation_scope BEFORE INSERT OR UPDATE ON mr_case_allocations FOR EACH ROW EXECUTE FUNCTION guard_mr_case_allocation();
CREATE FUNCTION guard_mr_clinical_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.clinical_required AND NEW.case_id IS NULL THEN RAISE EXCEPTION 'Clinical medical work needs an allocated case' USING ERRCODE='23514'; END IF;
 IF NEW.case_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM cases c JOIN mr_case_allocations a ON a.case_id=c.id WHERE c.id=NEW.case_id AND c.workflow_state='Medical Review' AND NOT c.locked AND a.team_id=NEW.team_id) THEN RAISE EXCEPTION 'Medical case must belong to the selected team allocation' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_mr_clinical_order BEFORE INSERT ON mr_work_orders FOR EACH ROW EXECUTE FUNCTION guard_mr_clinical_order();
CREATE FUNCTION guard_mr_conclusion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM mr_work_assignments w JOIN mr_work_orders o ON o.id=w.work_order_id JOIN cases c ON c.id=o.case_id JOIN mr_case_allocations a ON a.case_id=c.id JOIN mr_team_members m ON m.team_id=w.team_id AND m.user_id=w.employee_user_id JOIN mr_teams t ON t.id=m.team_id JOIN users u ON u.id=m.user_id
 WHERE w.id=NEW.assignment_id AND w.employee_user_id=NEW.employee_id AND c.id=NEW.case_id AND c.row_version=NEW.source_case_version AND c.workflow_state='Medical Review' AND NOT c.locked AND c.assigned_to_user_id=NEW.employee_id AND a.team_id=w.team_id AND m.status='ACTIVE' AND t.active AND u.role='ROLE_MEDICAL_REVIEWER' AND u.enabled AND NOT u.locked AND u.account_status='APPROVED' AND w.status<>'COMPLETED') THEN RAISE EXCEPTION 'Current owned medical case, revision and active employee required' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_mr_conclusion_scope BEFORE INSERT ON mr_review_conclusions FOR EACH ROW EXECUTE FUNCTION guard_mr_conclusion();
CREATE TRIGGER trg_mr_allocation_event_immutable BEFORE UPDATE OR DELETE ON mr_case_allocation_events FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_mr_conclusion_immutable BEFORE UPDATE OR DELETE ON mr_review_conclusions FOR EACH ROW EXECUTE FUNCTION protect_security_audit();

-- Preserve the allocation lineage; reassignment uses a checked update and a new history event.
CREATE TRIGGER trg_mr_allocation_no_delete BEFORE DELETE ON mr_case_allocations FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
