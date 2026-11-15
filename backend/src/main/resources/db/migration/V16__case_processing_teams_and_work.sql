-- Separate Case Processing hierarchy. Team leads do not acquire clinical review authority.
INSERT INTO workspace_roles(code,display_name,workspace,assignable) VALUES('ROLE_CASE_TEAM_LEAD','Case Processing Team Lead','case-processing-teams',FALSE);
CREATE TABLE cp_teams (
 id BIGSERIAL PRIMARY KEY,
 team_code VARCHAR(40) GENERATED ALWAYS AS ('CP-TL-' || CASE WHEN length(id::text)<3 THEN lpad(id::text,3,'0') ELSE id::text END) STORED UNIQUE,
 name VARCHAR(100) NOT NULL CHECK(length(trim(name))>0),
 lead_user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
 registration_key VARCHAR(36) NOT NULL UNIQUE, registration_open BOOLEAN NOT NULL DEFAULT TRUE,
 active BOOLEAN NOT NULL DEFAULT TRUE, max_members INT NOT NULL DEFAULT 10 CHECK(max_members BETWEEN 1 AND 10),
 created_by_user_id BIGINT NOT NULL REFERENCES users(id),created_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE registration_requests ADD COLUMN cp_team_id BIGINT REFERENCES cp_teams(id);
CREATE INDEX idx_registration_cp_team ON registration_requests(cp_team_id,created_at DESC);
CREATE TABLE cp_team_members (
 id BIGSERIAL PRIMARY KEY,team_id BIGINT NOT NULL REFERENCES cp_teams(id),user_id BIGINT NOT NULL REFERENCES users(id),
 status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','ACTIVE','REJECTED','SUSPENDED','LEFT')),
 joined_at TIMESTAMPTZ NOT NULL,UNIQUE(team_id,user_id)
);
CREATE UNIQUE INDEX uq_cp_current_membership ON cp_team_members(user_id) WHERE status IN ('PENDING','ACTIVE');
CREATE INDEX idx_cp_members_team_status ON cp_team_members(team_id,status,user_id);
CREATE TABLE cp_work_orders (
 id BIGSERIAL PRIMARY KEY,team_id BIGINT NOT NULL REFERENCES cp_teams(id),
 title VARCHAR(160) NOT NULL CHECK(length(trim(title))>0),description VARCHAR(5000) NOT NULL CHECK(length(trim(description))>0),
 instructions VARCHAR(5000) NOT NULL,
 stage VARCHAR(32) NOT NULL CHECK(stage IN ('INTAKE','VALIDATION','DUPLICATE_REVIEW','CODING','NARRATIVE','MEDICAL_HANDOFF','QC_CORRECTION','REPORTING_HANDOFF')),
 priority VARCHAR(16) NOT NULL CHECK(priority IN ('Routine','Priority','Expedited')),
 due_at TIMESTAMPTZ NOT NULL, case_id BIGINT REFERENCES cases(id),
 recipient_mode VARCHAR(16) NOT NULL CHECK(recipient_mode IN ('INDIVIDUAL','ALL_TEAM')),
 created_by_user_id BIGINT NOT NULL REFERENCES users(id),created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(id,team_id),CHECK(recipient_mode<>'ALL_TEAM' OR case_id IS NULL)
);
CREATE INDEX idx_cp_work_orders_team ON cp_work_orders(team_id,created_at DESC);
CREATE TABLE cp_work_assignments (
 id BIGSERIAL PRIMARY KEY,work_order_id BIGINT NOT NULL,team_id BIGINT NOT NULL,employee_user_id BIGINT NOT NULL REFERENCES users(id),
 status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED' CHECK(status IN ('NOT_STARTED','IN_PROGRESS','BLOCKED','COMPLETED')),
 progress INT NOT NULL DEFAULT 0 CHECK(progress BETWEEN 0 AND 100),
 row_version BIGINT NOT NULL DEFAULT 0,supervisor_note VARCHAR(2000) NOT NULL DEFAULT '',reopened_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(work_order_id,employee_user_id),UNIQUE(id,employee_user_id),
 FOREIGN KEY(work_order_id,team_id) REFERENCES cp_work_orders(id,team_id),
 FOREIGN KEY(team_id,employee_user_id) REFERENCES cp_team_members(team_id,user_id),
 CHECK((status='COMPLETED')=(progress=100)),CHECK(status<>'NOT_STARTED' OR progress=0)
);
CREATE INDEX idx_cp_assignment_employee ON cp_work_assignments(employee_user_id,status,updated_at DESC);
CREATE INDEX idx_cp_assignment_team ON cp_work_assignments(team_id,status,updated_at DESC);
CREATE TABLE cp_work_updates (
 id BIGSERIAL PRIMARY KEY,assignment_id BIGINT NOT NULL,employee_user_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('IN_PROGRESS','BLOCKED','COMPLETED')),
 progress INT NOT NULL CHECK(progress BETWEEN 0 AND 100),
 work_done VARCHAR(5000) NOT NULL CHECK(length(trim(work_done))>0),pending_work VARCHAR(3000) NOT NULL,remaining_work VARCHAR(3000) NOT NULL,
 blockers VARCHAR(2000) NOT NULL,reason VARCHAR(2000) NOT NULL CHECK(length(trim(reason))>0),
 checklist_json JSONB NOT NULL CHECK(jsonb_typeof(checklist_json)='object'),coding_reference VARCHAR(500) NOT NULL,
 source_assignment_version BIGINT NOT NULL,recorded_at TIMESTAMPTZ NOT NULL,
 UNIQUE(assignment_id,source_assignment_version),UNIQUE(id,assignment_id),
 FOREIGN KEY(assignment_id,employee_user_id) REFERENCES cp_work_assignments(id,employee_user_id),
 CHECK((status='COMPLETED')=(progress=100)),CHECK(status<>'COMPLETED' OR (length(trim(pending_work))=0 AND length(trim(remaining_work))=0))
);
CREATE INDEX idx_cp_update_assignment ON cp_work_updates(assignment_id,recorded_at DESC,id DESC);
CREATE TABLE cp_work_documents (
 id VARCHAR(36) PRIMARY KEY,assignment_id BIGINT NOT NULL REFERENCES cp_work_assignments(id),update_id BIGINT,
 document_kind VARCHAR(24) NOT NULL CHECK(document_kind IN ('ASSIGNMENT','EMPLOYEE_RESPONSE')),
 generated_by_user_id BIGINT NOT NULL REFERENCES users(id),generated_at TIMESTAMPTZ NOT NULL,
 snapshot_json JSONB NOT NULL,pdf_bytes BYTEA NOT NULL CHECK(octet_length(pdf_bytes)>0),
 pdf_sha256 VARCHAR(64) NOT NULL CHECK(pdf_sha256 ~ '^[a-f0-9]{64}$'),file_name VARCHAR(180) NOT NULL,
 FOREIGN KEY(update_id,assignment_id) REFERENCES cp_work_updates(id,assignment_id),
 CHECK((document_kind='ASSIGNMENT' AND update_id IS NULL) OR (document_kind='EMPLOYEE_RESPONSE' AND update_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_cp_assignment_document ON cp_work_documents(assignment_id) WHERE document_kind='ASSIGNMENT';
CREATE UNIQUE INDEX uq_cp_update_document ON cp_work_documents(update_id) WHERE update_id IS NOT NULL;
CREATE INDEX idx_cp_documents_assignment ON cp_work_documents(assignment_id,generated_at DESC,id);
CREATE TRIGGER trg_cp_order_immutable BEFORE UPDATE OR DELETE ON cp_work_orders FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_cp_update_immutable BEFORE UPDATE OR DELETE ON cp_work_updates FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_cp_document_immutable BEFORE UPDATE OR DELETE ON cp_work_documents FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE FUNCTION verify_cp_team() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM application_role FROM role_admin_assignments WHERE application_role='ROLE_CASE_PROCESSOR' FOR UPDATE;
 IF NEW.active AND NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.lead_user_id AND role='ROLE_CASE_TEAM_LEAD' AND enabled AND account_status='APPROVED' AND NOT locked) THEN
  RAISE EXCEPTION 'An active Case Processing team requires an approved Team Lead' USING ERRCODE='23514';
 END IF;
 IF NEW.active AND (SELECT count(*) FROM cp_teams WHERE active AND id<>NEW.id)>=10 THEN
  RAISE EXCEPTION 'Case Processing supports up to ten active teams' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_cp_team_identity BEFORE INSERT OR UPDATE ON cp_teams FOR EACH ROW EXECUTE FUNCTION verify_cp_team();
CREATE FUNCTION verify_cp_member() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE seats INT;
BEGIN
 SELECT max_members INTO seats FROM cp_teams WHERE id=NEW.team_id FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND role IN ('ROLE_CASE_PROCESSOR','ROLE_USER')) THEN
  RAISE EXCEPTION 'Only Case Processing employees can join a processing team' USING ERRCODE='23514';
 END IF;
 IF NEW.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND account_status='APPROVED' AND enabled AND NOT locked) THEN
  RAISE EXCEPTION 'Active team membership requires an approved employee' USING ERRCODE='23514';
 END IF;
 IF NEW.status IN ('PENDING','ACTIVE') AND (SELECT count(*) FROM cp_team_members WHERE team_id=NEW.team_id AND id<>NEW.id AND status IN ('PENDING','ACTIVE'))>=seats THEN
  RAISE EXCEPTION 'This team has no remaining employee seats' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_cp_member_identity BEFORE INSERT OR UPDATE ON cp_team_members FOR EACH ROW EXECUTE FUNCTION verify_cp_member();
CREATE FUNCTION preserve_cp_user_role() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.role<>OLD.role AND (EXISTS(SELECT 1 FROM cp_teams WHERE lead_user_id=NEW.id AND active) OR EXISTS(SELECT 1 FROM cp_team_members WHERE user_id=NEW.id AND status IN ('PENDING','ACTIVE'))) THEN
  RAISE EXCEPTION 'Release the Case Processing team relationship before changing role' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_cp_user_role BEFORE UPDATE OF role ON users FOR EACH ROW EXECUTE FUNCTION preserve_cp_user_role();
ALTER TABLE administrative_events DROP CONSTRAINT IF EXISTS administrative_events_actor_level_check;
ALTER TABLE administrative_events ADD CONSTRAINT chk_admin_event_actor_level CHECK(actor_level IN ('SUPER_ADMIN','ROLE_ADMIN','TEAM_LEAD','USER'));
-- Keep memberships and capacities consistent even when an existing account-status API is used.
CREATE FUNCTION sync_cp_member_account_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 UPDATE cp_team_members SET status=CASE WHEN NEW.account_status='PENDING' THEN 'PENDING' WHEN NEW.account_status='REJECTED' THEN 'REJECTED' WHEN NEW.account_status='APPROVED' AND NEW.enabled THEN 'ACTIVE' ELSE 'SUSPENDED' END
 WHERE user_id=NEW.id AND status IN ('PENDING','ACTIVE','SUSPENDED')
 AND status IS DISTINCT FROM CASE WHEN NEW.account_status='PENDING' THEN 'PENDING' WHEN NEW.account_status='REJECTED' THEN 'REJECTED' WHEN NEW.account_status='APPROVED' AND NEW.enabled THEN 'ACTIVE' ELSE 'SUSPENDED' END;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_cp_member_account_state AFTER UPDATE OF enabled,account_status ON users FOR EACH ROW EXECUTE FUNCTION sync_cp_member_account_state();
ALTER TABLE registration_requests ADD CONSTRAINT chk_cp_registration_role CHECK(cp_team_id IS NULL OR requested_role='ROLE_CASE_PROCESSOR');
ALTER TABLE registration_requests ADD CONSTRAINT fk_cp_registration_membership FOREIGN KEY(cp_team_id,user_id) REFERENCES cp_team_members(team_id,user_id) DEFERRABLE INITIALLY DEFERRED;
-- Recipients and work ownership are fixed after the original assignment PDF is delivered.
CREATE FUNCTION verify_cp_assignment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND (NEW.employee_user_id<>OLD.employee_user_id OR NEW.team_id<>OLD.team_id OR NEW.work_order_id<>OLD.work_order_id) THEN
  RAISE EXCEPTION 'Assignment recipients and team ownership are permanent' USING ERRCODE='23514';
 END IF;
 IF TG_OP='INSERT' AND NOT EXISTS(SELECT 1 FROM cp_team_members m JOIN users u ON u.id=m.user_id JOIN cp_teams t ON t.id=m.team_id WHERE m.team_id=NEW.team_id AND m.user_id=NEW.employee_user_id AND m.status='ACTIVE' AND t.active AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked) THEN
  RAISE EXCEPTION 'Only an active employee of the selected team can receive work' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_cp_assignment_identity BEFORE INSERT OR UPDATE ON cp_work_assignments FOR EACH ROW EXECUTE FUNCTION verify_cp_assignment();
