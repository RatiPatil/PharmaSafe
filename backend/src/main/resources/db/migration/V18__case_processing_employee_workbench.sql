-- Additive only: original work orders and historical response PDFs remain unchanged.
ALTER TABLE cp_work_orders ADD COLUMN clinical_required BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE cp_assignment_cases (
 assignment_id BIGINT PRIMARY KEY REFERENCES cp_work_assignments(id),
 case_id BIGINT NOT NULL REFERENCES cases(id),
 employee_id BIGINT NOT NULL REFERENCES users(id),
 linked_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(assignment_id,employee_id) REFERENCES cp_work_assignments(id,employee_user_id)
);
CREATE INDEX idx_cp_assignment_case ON cp_assignment_cases(case_id);
CREATE TABLE cp_processing_evidence (
 id BIGSERIAL PRIMARY KEY,
 assignment_id BIGINT NOT NULL REFERENCES cp_work_assignments(id),
 case_id BIGINT NOT NULL REFERENCES cases(id),
 case_row_version BIGINT NOT NULL CHECK(case_row_version>=0),
 kind VARCHAR(32) NOT NULL CHECK(kind IN ('DUPLICATE_REVIEW','CODING')),
 outcome VARCHAR(32) NOT NULL,
 notes VARCHAR(5000) NOT NULL CHECK(length(trim(notes))>0),
 dictionary_reference VARCHAR(500) NOT NULL DEFAULT '',
 recorded_by BIGINT NOT NULL REFERENCES users(id), recorded_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(assignment_id,recorded_by) REFERENCES cp_work_assignments(id,employee_user_id),
 CHECK((kind='DUPLICATE_REVIEW' AND outcome IN ('NO_DUPLICATE','POTENTIAL_DUPLICATE','DISTINCT_CASE')) OR (kind='CODING' AND outcome='VERIFIED' AND length(trim(dictionary_reference))>0))
);
CREATE INDEX idx_cp_processing_evidence_revision ON cp_processing_evidence(assignment_id,case_id,case_row_version,kind,recorded_at DESC,id DESC);
CREATE FUNCTION guard_cp_clinical_evidence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE task cp_work_assignments%ROWTYPE; clinical_id BIGINT; actor_id BIGINT; clinical cases%ROWTYPE;
BEGIN
 SELECT * INTO task FROM cp_work_assignments WHERE id=NEW.assignment_id FOR UPDATE;
 IF TG_TABLE_NAME='cp_assignment_cases' THEN
  actor_id:=NEW.employee_id;
  IF EXISTS(SELECT 1 FROM cp_work_orders WHERE id=task.work_order_id AND case_id IS NOT NULL) THEN RAISE EXCEPTION 'Task already has a clinical case' USING ERRCODE='23514'; END IF;
 ELSE
  actor_id:=NEW.recorded_by;
  SELECT COALESCE(o.case_id,l.case_id) INTO clinical_id FROM cp_work_orders o LEFT JOIN cp_assignment_cases l ON l.assignment_id=task.id WHERE o.id=task.work_order_id;
  IF clinical_id IS DISTINCT FROM NEW.case_id THEN RAISE EXCEPTION 'Evidence must reference the task clinical case' USING ERRCODE='23514'; END IF;
 END IF;
 SELECT * INTO clinical FROM cases WHERE id=NEW.case_id FOR UPDATE;
 IF clinical.assigned_to_user_id IS DISTINCT FROM actor_id OR task.employee_user_id IS DISTINCT FROM actor_id
  OR clinical.locked OR clinical.workflow_state NOT IN ('Draft','Data Entry') OR task.status='COMPLETED'
  OR NOT EXISTS(SELECT 1 FROM users WHERE id=actor_id AND role IN ('ROLE_USER','ROLE_CASE_PROCESSOR') AND enabled AND NOT locked AND account_status='APPROVED')
  OR NOT EXISTS(SELECT 1 FROM cp_team_members m JOIN cp_teams t ON t.id=m.team_id WHERE m.team_id=task.team_id AND m.user_id=actor_id AND m.status='ACTIVE' AND t.active)
 THEN RAISE EXCEPTION 'Active assigned employee and editable case required' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='cp_processing_evidence' THEN
  IF NEW.case_row_version<>clinical.row_version THEN RAISE EXCEPTION 'Stale clinical revision' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_cp_assignment_case_guard BEFORE INSERT ON cp_assignment_cases FOR EACH ROW EXECUTE FUNCTION guard_cp_clinical_evidence();
CREATE TRIGGER trg_cp_processing_evidence_guard BEFORE INSERT ON cp_processing_evidence FOR EACH ROW EXECUTE FUNCTION guard_cp_clinical_evidence();
CREATE TRIGGER trg_cp_assignment_case_immutable BEFORE UPDATE OR DELETE ON cp_assignment_cases FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_cp_processing_evidence_immutable BEFORE UPDATE OR DELETE ON cp_processing_evidence FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
