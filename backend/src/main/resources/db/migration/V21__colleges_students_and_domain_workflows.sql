-- Additive college/education workflows. Clinical authorities and existing records are preserved.
INSERT INTO workspace_roles(code,display_name,workspace,assignable) VALUES
 ('ROLE_DOMAIN_ADMIN','Education Domain Administrator','education',FALSE),
 ('ROLE_COLLEGE_ADMIN','College Administrator','education',FALSE),
 ('ROLE_STUDENT','College Student','education',FALSE);
CREATE TABLE edu_colleges(id BIGSERIAL PRIMARY KEY,code VARCHAR(16) NOT NULL UNIQUE CHECK(code ~ '^[A-Z0-9][A-Z0-9-]{2,15}$'),name VARCHAR(180) NOT NULL CHECK(length(trim(name))>0),active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0);
CREATE TABLE edu_student_id_reservations(id BIGSERIAL PRIMARY KEY,college_id BIGINT NOT NULL REFERENCES edu_colleges(id),token_hash VARCHAR(64) NOT NULL UNIQUE,expires_at TIMESTAMPTZ NOT NULL,consumed BOOLEAN NOT NULL DEFAULT FALSE,
 student_code VARCHAR(32) GENERATED ALWAYS AS ('ST-' || CASE WHEN length(id::text)<6 THEN lpad(id::text,6,'0') ELSE id::text END) STORED UNIQUE,UNIQUE(id,college_id,student_code));
CREATE INDEX idx_edu_reservation_expiry ON edu_student_id_reservations(expires_at) WHERE NOT consumed;
CREATE TABLE edu_college_admins(id BIGSERIAL PRIMARY KEY,user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),college_id BIGINT NOT NULL REFERENCES edu_colleges(id),active BOOLEAN NOT NULL DEFAULT TRUE,assigned_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0);
CREATE INDEX idx_edu_college_admins_scope ON edu_college_admins(college_id,user_id);
CREATE TABLE edu_domain_admins(id BIGSERIAL PRIMARY KEY,user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),domain VARCHAR(32) NOT NULL REFERENCES workspace_roles(code),active BOOLEAN NOT NULL DEFAULT TRUE,assigned_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0);
CREATE TABLE edu_students(id BIGSERIAL PRIMARY KEY,user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),college_id BIGINT NOT NULL REFERENCES edu_colleges(id),reservation_id BIGINT NOT NULL UNIQUE REFERENCES edu_student_id_reservations(id),student_code VARCHAR(32) NOT NULL UNIQUE,created_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(id,college_id),FOREIGN KEY(reservation_id,college_id,student_code) REFERENCES edu_student_id_reservations(id,college_id,student_code));
CREATE INDEX idx_edu_students_college ON edu_students(college_id,id);
CREATE TABLE edu_student_domains(id BIGSERIAL PRIMARY KEY,student_id BIGINT NOT NULL REFERENCES edu_students(id),domain VARCHAR(32) NOT NULL REFERENCES workspace_roles(code),status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED')),reviewer_id BIGINT REFERENCES users(id),reason VARCHAR(2000),reviewed_at TIMESTAMPTZ,requested_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0,UNIQUE(student_id,domain),CHECK(status='PENDING' OR (reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL AND length(trim(reason))>0)));
CREATE INDEX idx_edu_membership_approvals ON edu_student_domains(domain,status,student_id);
ALTER TABLE edu_student_domains ADD CONSTRAINT chk_edu_student_domain CHECK(domain IN ('ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER','ROLE_SAFETY_MANAGER','ROLE_REPORTING_SPECIALIST','ROLE_AUDITOR','ROLE_PV_ANALYST'));
ALTER TABLE edu_domain_admins ADD CONSTRAINT chk_edu_admin_domain CHECK(domain IN ('ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER','ROLE_SAFETY_MANAGER','ROLE_REPORTING_SPECIALIST','ROLE_AUDITOR','ROLE_PV_ANALYST'));
CREATE TABLE edu_tasks(id BIGSERIAL PRIMARY KEY,college_id BIGINT NOT NULL REFERENCES edu_colleges(id),student_id BIGINT NOT NULL REFERENCES edu_students(id),assigned_by BIGINT NOT NULL REFERENCES users(id),domain VARCHAR(32) NOT NULL REFERENCES workspace_roles(code),stage VARCHAR(64) NOT NULL,title VARCHAR(160) NOT NULL CHECK(length(trim(title))>0),description VARCHAR(5000) NOT NULL CHECK(length(trim(description))>0),instructions VARCHAR(5000) NOT NULL CHECK(length(trim(instructions))>0),due_at TIMESTAMPTZ NOT NULL,require_document BOOLEAN NOT NULL DEFAULT FALSE,status VARCHAR(16) NOT NULL CHECK(status IN ('NOT_STARTED','IN_PROGRESS','SUBMITTED','RETURNED','COMPLETED')),progress INT NOT NULL DEFAULT 0 CHECK(progress BETWEEN 0 AND 100),created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(id,student_id),FOREIGN KEY(student_id,college_id) REFERENCES edu_students(id,college_id),FOREIGN KEY(student_id,domain) REFERENCES edu_student_domains(student_id,domain),CHECK((status IN ('SUBMITTED','COMPLETED'))=(progress=100)));
CREATE INDEX idx_edu_task_scope ON edu_tasks(college_id,domain,status,updated_at DESC);
CREATE INDEX idx_edu_task_student ON edu_tasks(student_id,domain,updated_at DESC);
CREATE TABLE edu_task_submissions(id BIGSERIAL PRIMARY KEY,task_id BIGINT NOT NULL REFERENCES edu_tasks(id),student_id BIGINT NOT NULL REFERENCES edu_students(id),source_task_version BIGINT NOT NULL CHECK(source_task_version>=0),status VARCHAR(16) NOT NULL CHECK(status IN ('IN_PROGRESS','SUBMITTED')),progress INT NOT NULL CHECK(progress BETWEEN 0 AND 100),work_done VARCHAR(5000) NOT NULL CHECK(length(trim(work_done))>0),pending_work VARCHAR(3000) NOT NULL,remaining_work VARCHAR(3000) NOT NULL,evidence_json JSONB NOT NULL,created_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(task_id,student_id) REFERENCES edu_tasks(id,student_id),UNIQUE(task_id,source_task_version),UNIQUE(id,task_id),CHECK((status='SUBMITTED')=(progress=100)),CHECK(status<>'SUBMITTED' OR (length(trim(pending_work))=0 AND length(trim(remaining_work))=0)));
CREATE INDEX idx_edu_submission_history ON edu_task_submissions(task_id,id DESC);
CREATE TABLE edu_task_feedback(id BIGSERIAL PRIMARY KEY,task_id BIGINT NOT NULL REFERENCES edu_tasks(id),submission_id BIGINT NOT NULL UNIQUE REFERENCES edu_task_submissions(id),reviewer_id BIGINT NOT NULL REFERENCES users(id),outcome VARCHAR(16) NOT NULL CHECK(outcome IN ('RETURNED','COMPLETED')),feedback VARCHAR(3000) NOT NULL CHECK(length(trim(feedback))>0),created_at TIMESTAMPTZ NOT NULL,FOREIGN KEY(submission_id,task_id) REFERENCES edu_task_submissions(id,task_id));
CREATE INDEX idx_edu_feedback_history ON edu_task_feedback(task_id,id DESC);
CREATE TABLE edu_submission_documents(id BIGSERIAL PRIMARY KEY,submission_id BIGINT NOT NULL UNIQUE REFERENCES edu_task_submissions(id),file_name VARCHAR(180) NOT NULL,file_bytes BYTEA NOT NULL CHECK(octet_length(file_bytes) BETWEEN 5 AND 5242880),sha256 VARCHAR(64) NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),created_at TIMESTAMPTZ NOT NULL);
CREATE TABLE edu_progress_reports(id BIGSERIAL PRIMARY KEY,generated_by BIGINT NOT NULL REFERENCES users(id),college_id BIGINT REFERENCES edu_colleges(id),student_id BIGINT REFERENCES edu_students(id),domain VARCHAR(32) NOT NULL REFERENCES workspace_roles(code),filter_status VARCHAR(16),snapshot_json JSONB NOT NULL,pdf_bytes BYTEA NOT NULL CHECK(octet_length(pdf_bytes)>0),sha256 VARCHAR(64) NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),file_name VARCHAR(180) NOT NULL,created_at TIMESTAMPTZ NOT NULL);
CREATE INDEX idx_edu_report_owner ON edu_progress_reports(generated_by,domain,id DESC);
CREATE FUNCTION verify_edu_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='edu_students' THEN
  IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND role='ROLE_STUDENT') THEN RAISE EXCEPTION 'Student account authority required' USING ERRCODE='23514'; END IF;
  IF TG_OP='UPDATE' AND (NEW.college_id<>OLD.college_id OR NEW.user_id<>OLD.user_id OR NEW.student_code<>OLD.student_code OR NEW.reservation_id<>OLD.reservation_id) THEN RAISE EXCEPTION 'Student identity and college are permanent' USING ERRCODE='23514'; END IF;
  IF TG_OP='INSERT' AND NOT EXISTS(SELECT 1 FROM edu_student_id_reservations r JOIN edu_colleges c ON c.id=r.college_id WHERE r.id=NEW.reservation_id AND r.college_id=NEW.college_id AND r.student_code=NEW.student_code AND NOT r.consumed AND r.expires_at>CURRENT_TIMESTAMP AND c.active) THEN RAISE EXCEPTION 'Valid unconsumed Student ID reservation required' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='edu_college_admins' THEN
  IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND role='ROLE_COLLEGE_ADMIN') THEN RAISE EXCEPTION 'College administrator authority required' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='edu_domain_admins' THEN
  IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND role='ROLE_DOMAIN_ADMIN') THEN RAISE EXCEPTION 'Domain administrator authority required' USING ERRCODE='23514'; END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_student_identity BEFORE INSERT OR UPDATE ON edu_students FOR EACH ROW EXECUTE FUNCTION verify_edu_identity();
CREATE TRIGGER trg_edu_college_admin_identity BEFORE INSERT OR UPDATE ON edu_college_admins FOR EACH ROW EXECUTE FUNCTION verify_edu_identity();
CREATE TRIGGER trg_edu_domain_admin_identity BEFORE INSERT OR UPDATE ON edu_domain_admins FOR EACH ROW EXECUTE FUNCTION verify_edu_identity();
CREATE FUNCTION verify_edu_assignment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  IF NOT EXISTS(SELECT 1 FROM edu_student_domains m JOIN edu_students s ON s.id=m.student_id JOIN users u ON u.id=s.user_id JOIN edu_colleges c ON c.id=s.college_id WHERE m.student_id=NEW.student_id AND m.domain=NEW.domain AND m.status='APPROVED' AND c.id=NEW.college_id AND c.active AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked) THEN RAISE EXCEPTION 'Active approved student-domain membership required' USING ERRCODE='23514'; END IF;
  IF NOT EXISTS(SELECT 1 FROM users u LEFT JOIN edu_college_admins a ON a.user_id=u.id WHERE u.id=NEW.assigned_by AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (u.role='ROLE_ADMIN' OR (u.role='ROLE_COLLEGE_ADMIN' AND a.active AND a.college_id=NEW.college_id))) THEN RAISE EXCEPTION 'Authorized college task assigner required' USING ERRCODE='23514'; END IF;
 ELSIF NEW.college_id<>OLD.college_id OR NEW.student_id<>OLD.student_id OR NEW.domain<>OLD.domain OR NEW.assigned_by<>OLD.assigned_by OR NEW.stage<>OLD.stage OR NEW.title<>OLD.title OR NEW.description<>OLD.description OR NEW.instructions<>OLD.instructions OR NEW.due_at<>OLD.due_at OR NEW.require_document<>OLD.require_document THEN RAISE EXCEPTION 'Task recipient, domain and original instructions are permanent' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_assignment_scope BEFORE INSERT OR UPDATE ON edu_tasks FOR EACH ROW EXECUTE FUNCTION verify_edu_assignment();
CREATE FUNCTION verify_edu_decision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE college BIGINT;
BEGIN
 SELECT college_id INTO college FROM edu_students WHERE id=NEW.student_id;
 IF NEW.status<>'PENDING' AND NOT EXISTS(SELECT 1 FROM users u LEFT JOIN edu_college_admins a ON a.user_id=u.id WHERE u.id=NEW.reviewer_id AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (u.role='ROLE_ADMIN' OR u.role='ROLE_COLLEGE_ADMIN' AND a.active AND a.college_id=college)) THEN RAISE EXCEPTION 'Only authorized college administrators approve domain access' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (NEW.student_id<>OLD.student_id OR NEW.domain<>OLD.domain) THEN RAISE EXCEPTION 'Requested student and domain are permanent' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_membership_decision BEFORE INSERT OR UPDATE ON edu_student_domains FOR EACH ROW EXECUTE FUNCTION verify_edu_decision();
CREATE FUNCTION verify_edu_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM edu_tasks t JOIN edu_student_domains m ON m.student_id=t.student_id AND m.domain=t.domain JOIN edu_students s ON s.id=t.student_id JOIN users u ON u.id=s.user_id JOIN edu_colleges c ON c.id=s.college_id WHERE t.id=NEW.task_id AND t.student_id=NEW.student_id AND t.row_version=NEW.source_task_version AND t.status IN ('NOT_STARTED','IN_PROGRESS','RETURNED') AND m.status='APPROVED' AND c.active AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked) THEN RAISE EXCEPTION 'Current owned task and approved domain access required for submission' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_submission_scope BEFORE INSERT ON edu_task_submissions FOR EACH ROW EXECUTE FUNCTION verify_edu_evidence();
CREATE FUNCTION verify_edu_feedback() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM edu_tasks t JOIN edu_task_submissions s ON s.id=NEW.submission_id AND s.task_id=t.id JOIN users u ON u.id=NEW.reviewer_id LEFT JOIN edu_college_admins a ON a.user_id=u.id WHERE t.id=NEW.task_id AND t.status='SUBMITTED' AND s.id=(SELECT max(id) FROM edu_task_submissions WHERE task_id=t.id) AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (u.role='ROLE_ADMIN' OR u.role='ROLE_COLLEGE_ADMIN' AND a.active AND a.college_id=t.college_id)) THEN RAISE EXCEPTION 'Submitted work and authorized college reviewer required' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_feedback_scope BEFORE INSERT ON edu_task_feedback FOR EACH ROW EXECUTE FUNCTION verify_edu_feedback();
CREATE TRIGGER trg_edu_submissions_immutable BEFORE UPDATE OR DELETE ON edu_task_submissions FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_edu_feedback_immutable BEFORE UPDATE OR DELETE ON edu_task_feedback FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_edu_documents_immutable BEFORE UPDATE OR DELETE ON edu_submission_documents FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_edu_reports_immutable BEFORE UPDATE OR DELETE ON edu_progress_reports FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE FUNCTION preserve_edu_user_role() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.role<>OLD.role AND (EXISTS(SELECT 1 FROM edu_students WHERE user_id=NEW.id) OR EXISTS(SELECT 1 FROM edu_college_admins WHERE user_id=NEW.id) OR EXISTS(SELECT 1 FROM edu_domain_admins WHERE user_id=NEW.id)) THEN RAISE EXCEPTION 'Educational account authority cannot be changed through a clinical role operation' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_user_role BEFORE UPDATE OF role ON users FOR EACH ROW EXECUTE FUNCTION preserve_edu_user_role();

ALTER TABLE edu_progress_reports ADD CONSTRAINT chk_edu_report_domain CHECK(domain IN ('ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER','ROLE_SAFETY_MANAGER','ROLE_REPORTING_SPECIALIST','ROLE_AUDITOR','ROLE_PV_ANALYST'));
ALTER TABLE edu_progress_reports ADD CONSTRAINT fk_edu_report_college_student FOREIGN KEY(student_id,college_id) REFERENCES edu_students(id,college_id);
CREATE INDEX idx_edu_task_deadline ON edu_tasks(due_at,college_id,domain) WHERE status<>'COMPLETED';
CREATE FUNCTION verify_edu_report() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users u WHERE u.id=NEW.generated_by AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (
 u.role='ROLE_ADMIN' OR
 u.role='ROLE_DOMAIN_ADMIN' AND EXISTS(SELECT 1 FROM edu_domain_admins a WHERE a.user_id=u.id AND a.active AND a.domain=NEW.domain) OR
 u.role='ROLE_COLLEGE_ADMIN' AND EXISTS(SELECT 1 FROM edu_college_admins a JOIN edu_colleges c ON c.id=a.college_id WHERE a.user_id=u.id AND a.active AND c.active AND a.college_id=NEW.college_id) OR
 u.role='ROLE_STUDENT' AND EXISTS(SELECT 1 FROM edu_students s JOIN edu_student_domains m ON m.student_id=s.id JOIN edu_colleges c ON c.id=s.college_id WHERE s.user_id=u.id AND s.id=NEW.student_id AND c.id=NEW.college_id AND c.active AND m.domain=NEW.domain AND m.status='APPROVED')
 )) THEN RAISE EXCEPTION 'Progress report author must match the college, student and domain scope' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_report_scope BEFORE INSERT ON edu_progress_reports FOR EACH ROW EXECUTE FUNCTION verify_edu_report();

CREATE TABLE edu_domain_stages(domain VARCHAR(32) NOT NULL REFERENCES workspace_roles(code),stage VARCHAR(64) NOT NULL,PRIMARY KEY(domain,stage));
INSERT INTO edu_domain_stages(domain,stage) VALUES
('ROLE_CASE_PROCESSOR','Case intake'),
('ROLE_CASE_PROCESSOR','Minimum criteria validation'),
('ROLE_CASE_PROCESSOR','Duplicate review'),
('ROLE_CASE_PROCESSOR','Medical coding'),
('ROLE_CASE_PROCESSOR','Case narrative'),
('ROLE_MEDICAL_REVIEWER','Clinical source review'),
('ROLE_MEDICAL_REVIEWER','Causality assessment'),
('ROLE_MEDICAL_REVIEWER','Expectedness review'),
('ROLE_MEDICAL_REVIEWER','Medical conclusion'),
('ROLE_QUALITY_REVIEWER','Completeness review'),
('ROLE_QUALITY_REVIEWER','Source verification'),
('ROLE_QUALITY_REVIEWER','Quality findings'),
('ROLE_QUALITY_REVIEWER','Quality conclusion'),
('ROLE_SAFETY_MANAGER','Workload planning'),
('ROLE_SAFETY_MANAGER','Risk review'),
('ROLE_SAFETY_MANAGER','Escalation planning'),
('ROLE_SAFETY_MANAGER','Operations summary'),
('ROLE_REPORTING_SPECIALIST','Reporting eligibility'),
('ROLE_REPORTING_SPECIALIST','Timeline assessment'),
('ROLE_REPORTING_SPECIALIST','Report preparation'),
('ROLE_REPORTING_SPECIALIST','Submission checklist'),
('ROLE_AUDITOR','Audit planning'),
('ROLE_AUDITOR','Evidence tracing'),
('ROLE_AUDITOR','Control evaluation'),
('ROLE_AUDITOR','Audit findings'),
('ROLE_PV_ANALYST','Analysis planning'),
('ROLE_PV_ANALYST','Case-series review'),
('ROLE_PV_ANALYST','Trend analysis'),
('ROLE_PV_ANALYST','Analysis conclusion');
ALTER TABLE edu_tasks ADD CONSTRAINT fk_edu_task_domain_stage FOREIGN KEY(domain,stage) REFERENCES edu_domain_stages(domain,stage);
