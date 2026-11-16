-- PV analytics is a separate authority; existing processors keep their roles and case links.
UPDATE workspace_roles SET display_name='Case Processor' WHERE code='ROLE_CASE_PROCESSOR';
UPDATE workspace_roles SET display_name='Legacy Case Processor' WHERE code='ROLE_USER';
INSERT INTO workspace_roles(code,display_name,workspace,assignable) VALUES('ROLE_PV_ANALYST','PV Analyst','pv_analyst',TRUE);
ALTER TABLE role_admin_assignments DROP CONSTRAINT role_admin_assignments_application_role_check;
ALTER TABLE role_admin_assignments ADD CONSTRAINT role_admin_assignments_application_role_check CHECK(application_role IN ('ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER','ROLE_SAFETY_MANAGER','ROLE_REPORTING_SPECIALIST','ROLE_AUDITOR','ROLE_PV_ANALYST'));
INSERT INTO role_admin_assignments(application_role) VALUES('ROLE_PV_ANALYST');
ALTER TABLE registration_requests DROP CONSTRAINT chk_registration_public_role;
ALTER TABLE registration_requests ADD CONSTRAINT chk_registration_public_role CHECK(requested_role IN ('ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER','ROLE_SAFETY_MANAGER','ROLE_REPORTING_SPECIALIST','ROLE_AUDITOR','ROLE_PV_ANALYST'));
CREATE TABLE pv_analysis_records (
 id VARCHAR(36) PRIMARY KEY, analyst_user_id BIGINT NOT NULL REFERENCES users(id),
 title VARCHAR(160) NOT NULL CHECK(length(trim(title))>0),objective VARCHAR(3000) NOT NULL CHECK(length(trim(objective))>0),
 findings VARCHAR(5000) NOT NULL CHECK(length(trim(findings))>0),recommendation VARCHAR(3000) NOT NULL CHECK(length(trim(recommendation))>0),
 case_id BIGINT REFERENCES cases(id),created_at TIMESTAMPTZ NOT NULL,
 snapshot_json JSONB NOT NULL CHECK(jsonb_typeof(snapshot_json)='object'),pdf_bytes BYTEA NOT NULL CHECK(octet_length(pdf_bytes)>0),
 pdf_sha256 VARCHAR(64) NOT NULL CHECK(pdf_sha256 ~ '^[a-f0-9]{64}$')
);
CREATE INDEX idx_pv_analysis_author_time ON pv_analysis_records(analyst_user_id,created_at DESC,id);
CREATE INDEX idx_pv_analysis_created ON pv_analysis_records(created_at DESC,id);
CREATE TRIGGER trg_pv_analysis_immutable BEFORE UPDATE OR DELETE ON pv_analysis_records FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE FUNCTION verify_pv_analysis_author() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.analyst_user_id AND role='ROLE_PV_ANALYST' AND account_status='APPROVED' AND enabled AND NOT locked) THEN
  RAISE EXCEPTION 'An approved PV Analyst must author an analysis' USING ERRCODE='23514';
 END IF;
 IF NEW.case_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM cases WHERE id=NEW.case_id AND workflow_state IN ('Approved','Locked','Closed')) THEN
  RAISE EXCEPTION 'PV analysis can reference only reviewed cases' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_pv_analysis_author BEFORE INSERT ON pv_analysis_records FOR EACH ROW EXECUTE FUNCTION verify_pv_analysis_author();
