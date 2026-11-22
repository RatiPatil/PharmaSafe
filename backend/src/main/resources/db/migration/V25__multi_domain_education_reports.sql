-- Preserve existing PDF/snapshot bytes; attach relational domain scope to each export.
ALTER TABLE edu_progress_reports ALTER COLUMN domain DROP NOT NULL;
CREATE TABLE edu_report_domains(
 report_id BIGINT NOT NULL REFERENCES edu_progress_reports(id),
 domain VARCHAR(32) NOT NULL REFERENCES workspace_roles(code),
 PRIMARY KEY(report_id,domain),
 CONSTRAINT chk_edu_report_selected_domain CHECK(domain IN ('ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER','ROLE_SAFETY_MANAGER','ROLE_REPORTING_SPECIALIST','ROLE_AUDITOR','ROLE_PV_ANALYST'))
);
INSERT INTO edu_report_domains(report_id,domain) SELECT id,domain FROM edu_progress_reports;
CREATE INDEX idx_edu_report_domains_lookup ON edu_report_domains(domain,report_id);
CREATE TRIGGER trg_edu_report_domains_immutable BEFORE UPDATE OR DELETE ON edu_report_domains FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE OR REPLACE FUNCTION verify_edu_report() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users u WHERE u.id=NEW.generated_by AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (
 u.role='ROLE_ADMIN' OR
 u.role='ROLE_DOMAIN_ADMIN' AND EXISTS(SELECT 1 FROM edu_domain_admins a WHERE a.user_id=u.id AND a.active) OR
 u.role='ROLE_COLLEGE_ADMIN' AND EXISTS(SELECT 1 FROM edu_college_admins a JOIN edu_colleges c ON c.id=a.college_id WHERE a.user_id=u.id AND a.active AND c.active AND a.college_id=NEW.college_id) OR
 u.role='ROLE_STUDENT' AND EXISTS(SELECT 1 FROM edu_students s JOIN edu_colleges c ON c.id=s.college_id WHERE s.user_id=u.id AND s.id=NEW.student_id AND c.id=NEW.college_id AND c.active)
 )) THEN RAISE EXCEPTION 'Progress report author must match the college, student and domain scope' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE FUNCTION verify_edu_report_domains() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE report edu_progress_reports%ROWTYPE; report_key BIGINT; domain_count BIGINT;
BEGIN
 IF TG_TABLE_NAME='edu_progress_reports' THEN report_key:=NEW.id; ELSE report_key:=NEW.report_id; END IF;
 SELECT * INTO report FROM edu_progress_reports WHERE id=report_key;
 SELECT count(*) INTO domain_count FROM edu_report_domains WHERE report_id=report_key;
 -- Older backend instances still write one explicit domain during a rolling deployment.
 IF domain_count=0 AND report.domain IS NOT NULL THEN
  INSERT INTO edu_report_domains(report_id,domain) VALUES(report_key,report.domain);
  domain_count:=1;
 END IF;
 IF domain_count<1 OR domain_count>7 THEN RAISE EXCEPTION 'A report must have one to seven domains' USING ERRCODE='23514'; END IF;
 IF (report.domain IS NOT NULL AND (domain_count<>1 OR NOT EXISTS(SELECT 1 FROM edu_report_domains WHERE report_id=report_key AND domain=report.domain))) OR (report.domain IS NULL AND domain_count<2) THEN RAISE EXCEPTION 'Report domain metadata must match its complete domain selection' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM edu_report_domains d JOIN users u ON u.id=report.generated_by WHERE d.report_id=report_key AND NOT (
 u.role='ROLE_ADMIN' OR
 u.role='ROLE_DOMAIN_ADMIN' AND EXISTS(SELECT 1 FROM edu_domain_admins a WHERE a.user_id=u.id AND a.active AND (a.domain IS NULL OR a.domain=d.domain)) OR
 u.role='ROLE_COLLEGE_ADMIN' AND EXISTS(SELECT 1 FROM edu_college_admins a WHERE a.user_id=u.id AND a.active AND a.college_id=report.college_id) OR
 u.role='ROLE_STUDENT' AND domain_count=1 AND EXISTS(SELECT 1 FROM edu_students s JOIN edu_student_domains m ON m.student_id=s.id WHERE s.user_id=u.id AND s.id=report.student_id AND s.college_id=report.college_id AND m.domain=d.domain AND m.status='APPROVED')
 )) THEN RAISE EXCEPTION 'Every exported domain requires author authorization' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER trg_edu_report_complete AFTER INSERT ON edu_progress_reports DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_edu_report_domains();
CREATE CONSTRAINT TRIGGER trg_edu_report_domain_scope AFTER INSERT ON edu_report_domains DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_edu_report_domains();
-- New domain rows cannot be appended to an already committed export.
CREATE FUNCTION prevent_edu_report_scope_extension() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM edu_progress_reports r WHERE r.id=NEW.report_id AND r.xmin::text::numeric=mod(pg_current_xact_id()::text::numeric,4294967296)) THEN RAISE EXCEPTION 'Report scope is fixed when the export is created' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_report_domain_insert BEFORE INSERT ON edu_report_domains FOR EACH ROW EXECUTE FUNCTION prevent_edu_report_scope_extension();
