-- Preserve existing administrator identities, credentials and case relationships.
-- ROLE_ADMIN is the compatibility storage code for the Super Admin authority.
UPDATE workspace_roles SET display_name='Super Admin',assignable=FALSE WHERE code='ROLE_ADMIN';
INSERT INTO workspace_roles(code,display_name,workspace,assignable) VALUES('ROLE_SCOPED_ADMIN','Role Admin','role-administration',FALSE) ON CONFLICT(code) DO UPDATE SET display_name=EXCLUDED.display_name,workspace=EXCLUDED.workspace,assignable=FALSE;
ALTER TABLE users ADD COLUMN staff_code VARCHAR(32) GENERATED ALWAYS AS ('PS-' || id::text) STORED;
CREATE UNIQUE INDEX uq_users_staff_code ON users(staff_code);
CREATE TABLE role_admin_assignments (
 application_role VARCHAR(32) PRIMARY KEY REFERENCES workspace_roles(code),
 administrator_id BIGINT UNIQUE REFERENCES users(id), assigned_by BIGINT REFERENCES users(id),
 assigned_at TIMESTAMPTZ, row_version BIGINT NOT NULL DEFAULT 0,
 CHECK(application_role IN ('ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER','ROLE_SAFETY_MANAGER','ROLE_REPORTING_SPECIALIST','ROLE_AUDITOR')),
 CHECK(administrator_id IS NULL OR (assigned_by IS NOT NULL AND assigned_at IS NOT NULL))
);
INSERT INTO role_admin_assignments(application_role) VALUES('ROLE_CASE_PROCESSOR'),('ROLE_MEDICAL_REVIEWER'),('ROLE_QUALITY_REVIEWER'),('ROLE_SAFETY_MANAGER'),('ROLE_REPORTING_SPECIALIST'),('ROLE_AUDITOR');
ALTER TABLE registration_requests ADD CONSTRAINT chk_registration_public_role CHECK(requested_role IN ('ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER','ROLE_SAFETY_MANAGER','ROLE_REPORTING_SPECIALIST','ROLE_AUDITOR'));
CREATE TABLE administrative_events (
 id BIGSERIAL PRIMARY KEY,actor_id BIGINT,actor_username VARCHAR(64) NOT NULL,
 actor_level VARCHAR(20) NOT NULL CHECK(actor_level IN ('SUPER_ADMIN','ROLE_ADMIN','USER')),
 managed_role VARCHAR(32) REFERENCES workspace_roles(code),target_user_id BIGINT REFERENCES users(id),target_username VARCHAR(64),
 request_id BIGINT,previous_administrator_id BIGINT REFERENCES users(id),action VARCHAR(64) NOT NULL,
 previous_status VARCHAR(32),new_status VARCHAR(32),reason VARCHAR(2000) NOT NULL,
 outcome VARCHAR(16) NOT NULL CHECK(outcome IN ('SUCCESS','DENIED')),created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_admin_event_scope_date ON administrative_events(managed_role,created_at DESC,id DESC);
CREATE INDEX idx_admin_event_target ON administrative_events(target_user_id,created_at DESC);
CREATE TRIGGER trg_admin_event_immutable BEFORE UPDATE OR DELETE ON administrative_events FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
-- Reject assignment of a normal user or Super Admin through direct database writes.
CREATE FUNCTION verify_role_admin_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.assigned_by IS NOT NULL AND NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.assigned_by AND role='ROLE_ADMIN' AND account_status='APPROVED' AND enabled=TRUE) THEN
  RAISE EXCEPTION 'Only a current Super Admin may assign role administrators' USING ERRCODE='23514';
 END IF;
 IF NEW.administrator_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.administrator_id AND role='ROLE_SCOPED_ADMIN' AND account_status='APPROVED' AND enabled=TRUE) THEN
  RAISE EXCEPTION 'Role administrator must be an approved role-admin account' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_role_admin_identity BEFORE INSERT OR UPDATE ON role_admin_assignments FOR EACH ROW EXECUTE FUNCTION verify_role_admin_identity();
CREATE FUNCTION protect_assigned_admin_role() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.role <> 'ROLE_SCOPED_ADMIN' AND EXISTS(SELECT 1 FROM role_admin_assignments WHERE administrator_id=NEW.id) THEN
  RAISE EXCEPTION 'Release the role-admin assignment before changing administrative authority' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_assigned_admin_role BEFORE UPDATE OF role ON users FOR EACH ROW EXECUTE FUNCTION protect_assigned_admin_role();
CREATE INDEX idx_cases_creator_work ON cases(created_by_user_id,updated_at DESC,id);
CREATE INDEX idx_assessments_actor_case ON case_assessments(assessed_by,case_id);
CREATE INDEX idx_reviews_actor_case ON case_review_events(reviewer_user_id,case_id);
CREATE INDEX idx_reports_creator_work ON safety_reports(created_by_user_id,updated_at DESC,id);
