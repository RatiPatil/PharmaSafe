-- Keep every existing student identifier and its FK bindings unchanged.
ALTER TABLE edu_student_id_reservations ALTER COLUMN student_code DROP EXPRESSION;
ALTER TABLE edu_student_id_reservations ALTER COLUMN student_code DROP NOT NULL;
CREATE TABLE edu_college_student_sequences(college_id BIGINT PRIMARY KEY REFERENCES edu_colleges(id),last_value BIGINT NOT NULL DEFAULT 0 CHECK(last_value BETWEEN 0 AND 999999999999999));
CREATE OR REPLACE FUNCTION verify_edu_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='edu_students' THEN
  IF TG_OP='INSERT' AND NOT EXISTS(SELECT 1 FROM edu_colleges c WHERE c.id=NEW.college_id AND NEW.student_code ~ ('^'||c.code||'-[0-9]{4,15}$')) THEN RAISE EXCEPTION 'New Student ID must identify its college' USING ERRCODE='23514'; END IF;
  IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND role='ROLE_STUDENT') THEN RAISE EXCEPTION 'Student account authority required' USING ERRCODE='23514'; END IF;
  IF TG_OP='UPDATE' AND (NEW.college_id<>OLD.college_id OR NEW.user_id<>OLD.user_id OR NEW.student_code<>OLD.student_code OR NEW.reservation_id<>OLD.reservation_id) THEN RAISE EXCEPTION 'Student identity and college are permanent' USING ERRCODE='23514'; END IF;
  IF TG_OP='INSERT' AND NOT EXISTS(SELECT 1 FROM edu_student_id_reservations r JOIN edu_colleges c ON c.id=r.college_id WHERE r.id=NEW.reservation_id AND r.college_id=NEW.college_id AND r.student_code=NEW.student_code AND NOT r.consumed AND r.expires_at>CURRENT_TIMESTAMP AND c.active) THEN RAISE EXCEPTION 'Valid unconsumed Student ID reservation required' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='edu_college_admins' THEN
  IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND role='ROLE_COLLEGE_ADMIN') THEN RAISE EXCEPTION 'College administrator authority required' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='edu_domain_admins' THEN
  IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.user_id AND role='ROLE_DOMAIN_ADMIN') THEN RAISE EXCEPTION 'Domain administrator authority required' USING ERRCODE='23514'; END IF;
 END IF; RETURN NEW;
END $$;
CREATE FUNCTION preserve_edu_college_sequence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Student sequences cannot be reset' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (NEW.college_id<>OLD.college_id OR NEW.last_value<OLD.last_value) THEN RAISE EXCEPTION 'Student sequences are monotonic and college-bound' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
CREATE TRIGGER trg_edu_student_sequence BEFORE UPDATE OR DELETE ON edu_college_student_sequences FOR EACH ROW EXECUTE FUNCTION preserve_edu_college_sequence();

CREATE OR REPLACE FUNCTION verify_edu_decision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE college BIGINT;
BEGIN
 SELECT college_id INTO college FROM edu_students WHERE id=NEW.student_id;
 IF NEW.status<>'PENDING' AND NOT EXISTS(SELECT 1 FROM users u LEFT JOIN edu_college_admins a ON a.user_id=u.id WHERE u.id=NEW.reviewer_id AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (u.role='ROLE_ADMIN' OR u.role='ROLE_COLLEGE_ADMIN' AND a.active AND a.college_id=college)) THEN RAISE EXCEPTION 'Only authorized college administrators approve domain access' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (NEW.student_id<>OLD.student_id OR NEW.domain<>OLD.domain) THEN RAISE EXCEPTION 'Requested student and domain are permanent' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
