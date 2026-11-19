-- Persist educational management without removing clinical history or immutable evidence.
ALTER TABLE edu_students ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
INSERT INTO edu_student_domains(student_id,domain,status,requested_at)
 SELECT s.id,r.code,'PENDING',CURRENT_TIMESTAMP FROM edu_students s CROSS JOIN workspace_roles r
 WHERE r.code IN ('ROLE_CASE_PROCESSOR','ROLE_MEDICAL_REVIEWER','ROLE_QUALITY_REVIEWER','ROLE_SAFETY_MANAGER','ROLE_REPORTING_SPECIALIST','ROLE_AUDITOR','ROLE_PV_ANALYST')
 AND NOT EXISTS(SELECT 1 FROM edu_student_domains m WHERE m.student_id=s.id AND m.domain=r.code);
CREATE TABLE edu_work_orders(id BIGSERIAL PRIMARY KEY,college_id BIGINT NOT NULL REFERENCES edu_colleges(id),created_by BIGINT NOT NULL REFERENCES users(id),last_changed_by BIGINT REFERENCES users(id),domain VARCHAR(32) NOT NULL,stage VARCHAR(64) NOT NULL,title VARCHAR(160) NOT NULL CHECK(length(trim(title))>0),description VARCHAR(5000) NOT NULL,instructions VARCHAR(5000) NOT NULL,due_at TIMESTAMPTZ NOT NULL,require_document BOOLEAN NOT NULL DEFAULT FALSE,archived BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMPTZ NOT NULL,row_version BIGINT NOT NULL DEFAULT 0,FOREIGN KEY(domain,stage) REFERENCES edu_domain_stages(domain,stage),UNIQUE(id,college_id,domain));
ALTER TABLE edu_tasks ADD COLUMN source_order_id BIGINT;
ALTER TABLE edu_tasks ADD COLUMN last_changed_by BIGINT REFERENCES users(id);
ALTER TABLE edu_tasks ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE edu_tasks ADD CONSTRAINT fk_edu_order_task_scope FOREIGN KEY(source_order_id,college_id,domain) REFERENCES edu_work_orders(id,college_id,domain);
CREATE INDEX idx_edu_orders_college_domain ON edu_work_orders(college_id,domain,id DESC) WHERE NOT archived;
CREATE TABLE edu_management_changes(id BIGSERIAL PRIMARY KEY,actor_id BIGINT NOT NULL REFERENCES users(id),record_kind VARCHAR(16) NOT NULL CHECK(record_kind IN ('COLLEGE','STUDENT','TASK','ORDER')),record_id BIGINT NOT NULL,before_json JSONB NOT NULL,after_json JSONB NOT NULL,reason VARCHAR(2000) NOT NULL CHECK(length(trim(reason))>0),created_at TIMESTAMPTZ NOT NULL);
CREATE INDEX idx_edu_changes_record ON edu_management_changes(record_kind,record_id,id DESC);
CREATE TRIGGER trg_edu_changes_immutable BEFORE UPDATE OR DELETE ON edu_management_changes FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TABLE edu_preferences(user_id BIGINT PRIMARY KEY REFERENCES users(id),compact_view BOOLEAN NOT NULL DEFAULT FALSE,reduce_motion BOOLEAN NOT NULL DEFAULT FALSE);
CREATE FUNCTION verify_edu_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users u WHERE u.id=(CASE WHEN TG_OP='INSERT' THEN NEW.created_by ELSE NEW.last_changed_by END) AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (u.role='ROLE_ADMIN' OR u.role='ROLE_DOMAIN_ADMIN' AND EXISTS(SELECT 1 FROM edu_domain_admins d WHERE d.user_id=u.id AND d.active))) THEN RAISE EXCEPTION 'Active Domain Admin order author required' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF NEW.college_id<>OLD.college_id OR NEW.domain<>OLD.domain OR NEW.stage<>OLD.stage OR NEW.created_by<>OLD.created_by THEN RAISE EXCEPTION 'Order scope and creator are permanent' USING ERRCODE='23514'; END IF;
  IF (NEW.title<>OLD.title OR NEW.description<>OLD.description OR NEW.instructions<>OLD.instructions OR NEW.due_at<>OLD.due_at OR NEW.require_document<>OLD.require_document) AND EXISTS(SELECT 1 FROM edu_tasks WHERE source_order_id=OLD.id) THEN RAISE EXCEPTION 'Dispatched college instructions are permanent' USING ERRCODE='23514'; END IF;
 END IF;RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_order_scope BEFORE INSERT OR UPDATE ON edu_work_orders FOR EACH ROW EXECUTE FUNCTION verify_edu_order();
CREATE OR REPLACE FUNCTION verify_edu_assignment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  IF NOT EXISTS(SELECT 1 FROM edu_student_domains m JOIN edu_students s ON s.id=m.student_id JOIN users u ON u.id=s.user_id JOIN edu_colleges c ON c.id=s.college_id WHERE m.student_id=NEW.student_id AND m.domain=NEW.domain AND m.status='APPROVED' AND c.id=NEW.college_id AND c.active AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked) THEN RAISE EXCEPTION 'Active approved student-domain membership required' USING ERRCODE='23514'; END IF;
  IF NOT EXISTS(SELECT 1 FROM users u LEFT JOIN edu_college_admins a ON a.user_id=u.id WHERE u.id=NEW.assigned_by AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (u.role='ROLE_ADMIN' OR u.role='ROLE_DOMAIN_ADMIN' AND EXISTS(SELECT 1 FROM edu_domain_admins da WHERE da.user_id=u.id AND da.active) OR (u.role='ROLE_COLLEGE_ADMIN' AND a.active AND a.college_id=NEW.college_id))) THEN RAISE EXCEPTION 'Authorized college task assigner required' USING ERRCODE='23514'; END IF;
 ELSE
  IF NEW.college_id<>OLD.college_id OR NEW.student_id<>OLD.student_id OR NEW.domain<>OLD.domain OR NEW.assigned_by<>OLD.assigned_by OR NEW.stage<>OLD.stage OR NEW.source_order_id IS DISTINCT FROM OLD.source_order_id THEN RAISE EXCEPTION 'Task recipient, domain, source and original assigner are permanent' USING ERRCODE='23514'; END IF;
  IF NEW.title<>OLD.title OR NEW.description<>OLD.description OR NEW.instructions<>OLD.instructions OR NEW.due_at<>OLD.due_at OR NEW.require_document<>OLD.require_document THEN
   IF OLD.status<>'NOT_STARTED' OR OLD.progress<>0 OR EXISTS(SELECT 1 FROM edu_task_submissions WHERE task_id=OLD.id) THEN RAISE EXCEPTION 'Instructions with student evidence are permanent' USING ERRCODE='23514'; END IF;
  END IF;
  IF NEW.archived IS DISTINCT FROM OLD.archived OR NEW.title<>OLD.title OR NEW.description<>OLD.description OR NEW.instructions<>OLD.instructions OR NEW.due_at<>OLD.due_at OR NEW.require_document<>OLD.require_document THEN
   IF NOT EXISTS(SELECT 1 FROM users u LEFT JOIN edu_college_admins a ON a.user_id=u.id WHERE u.id=NEW.last_changed_by AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (u.role='ROLE_ADMIN' OR u.role='ROLE_DOMAIN_ADMIN' AND EXISTS(SELECT 1 FROM edu_domain_admins da WHERE da.user_id=u.id AND da.active) OR u.role='ROLE_COLLEGE_ADMIN' AND a.active AND a.college_id=NEW.college_id)) THEN RAISE EXCEPTION 'Authorized educational work editor required' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 IF NEW.source_order_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM edu_work_orders o WHERE o.id=NEW.source_order_id AND o.college_id=NEW.college_id AND o.domain=NEW.domain AND (TG_OP='UPDATE' OR NOT o.archived) AND o.title=NEW.title AND o.description=NEW.description AND o.instructions=NEW.instructions AND o.stage=NEW.stage AND o.due_at>=NEW.due_at AND o.require_document=NEW.require_document) THEN RAISE EXCEPTION 'College work must match the original domain order' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION verify_edu_decision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE college BIGINT;
BEGIN
 SELECT college_id INTO college FROM edu_students WHERE id=NEW.student_id;
 IF NEW.status<>'PENDING' AND NOT EXISTS(SELECT 1 FROM users u LEFT JOIN edu_college_admins a ON a.user_id=u.id WHERE u.id=NEW.reviewer_id AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (u.role='ROLE_ADMIN' OR u.role='ROLE_DOMAIN_ADMIN' AND EXISTS(SELECT 1 FROM edu_domain_admins da WHERE da.user_id=u.id AND da.active) OR u.role='ROLE_COLLEGE_ADMIN' AND a.active AND a.college_id=college)) THEN RAISE EXCEPTION 'Only authorized college administrators approve domain access' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (NEW.student_id<>OLD.student_id OR NEW.domain<>OLD.domain) THEN RAISE EXCEPTION 'Requested student and domain are permanent' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION verify_edu_feedback() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM edu_tasks t JOIN edu_task_submissions s ON s.id=NEW.submission_id AND s.task_id=t.id JOIN users u ON u.id=NEW.reviewer_id LEFT JOIN edu_college_admins a ON a.user_id=u.id WHERE NOT t.archived AND t.id=NEW.task_id AND t.status='SUBMITTED' AND s.id=(SELECT max(id) FROM edu_task_submissions WHERE task_id=t.id) AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (u.role='ROLE_ADMIN' OR u.role='ROLE_DOMAIN_ADMIN' AND EXISTS(SELECT 1 FROM edu_domain_admins da WHERE da.user_id=u.id AND da.active) OR u.role='ROLE_COLLEGE_ADMIN' AND a.active AND a.college_id=t.college_id)) THEN RAISE EXCEPTION 'Submitted work and authorized college reviewer required' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION verify_edu_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM edu_tasks t JOIN edu_student_domains m ON m.student_id=t.student_id AND m.domain=t.domain JOIN edu_students s ON s.id=t.student_id JOIN users u ON u.id=s.user_id JOIN edu_colleges c ON c.id=s.college_id WHERE NOT t.archived AND t.id=NEW.task_id AND t.student_id=NEW.student_id AND t.row_version=NEW.source_task_version AND t.status IN ('NOT_STARTED','IN_PROGRESS','RETURNED') AND m.status='APPROVED' AND c.active AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked) THEN RAISE EXCEPTION 'Current owned task and approved domain access required for submission' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE FUNCTION verify_edu_seven_domains() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE sid BIGINT;
BEGIN
 IF TG_TABLE_NAME='edu_students' THEN sid=NEW.id; ELSE sid=OLD.student_id; END IF;
 IF EXISTS(SELECT 1 FROM edu_students WHERE id=sid) AND (SELECT count(*) FROM edu_student_domains WHERE student_id=sid)<>7 THEN RAISE EXCEPTION 'Student enrollment must retain all seven domain associations' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_edu_student_seven_domains AFTER INSERT ON edu_students DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_edu_seven_domains();
CREATE CONSTRAINT TRIGGER trg_edu_membership_seven_domains AFTER DELETE ON edu_student_domains DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_edu_seven_domains();
CREATE TRIGGER trg_edu_orders_archive_only BEFORE DELETE ON edu_work_orders FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_edu_tasks_archive_only BEFORE DELETE ON edu_tasks FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_edu_students_archive_only BEFORE DELETE ON edu_students FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_edu_colleges_archive_only BEFORE DELETE ON edu_colleges FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
