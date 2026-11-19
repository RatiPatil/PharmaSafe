-- One educational Domain Admin oversees all seven domains. Historical grants remain stored.
ALTER TABLE edu_domain_admins DROP CONSTRAINT chk_edu_admin_domain;
ALTER TABLE edu_domain_admins ALTER COLUMN domain DROP NOT NULL;
-- Retain the earliest active account as the unified administrator; preserve all history.
UPDATE edu_domain_admins SET active=FALSE,row_version=row_version+1
WHERE active AND id<>(SELECT MIN(id) FROM edu_domain_admins WHERE active);
UPDATE edu_domain_admins SET domain=NULL,row_version=row_version+1;
ALTER TABLE edu_domain_admins ADD CONSTRAINT chk_edu_admin_domain CHECK(domain IS NULL);
CREATE UNIQUE INDEX uq_edu_single_active_domain_admin ON edu_domain_admins(active) WHERE active;
CREATE OR REPLACE FUNCTION verify_edu_report() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users u WHERE u.id=NEW.generated_by AND u.enabled AND u.account_status='APPROVED' AND NOT u.locked AND (
 u.role='ROLE_ADMIN' OR
 u.role='ROLE_DOMAIN_ADMIN' AND EXISTS(SELECT 1 FROM edu_domain_admins a WHERE a.user_id=u.id AND a.active AND a.domain IS NULL) OR
 u.role='ROLE_COLLEGE_ADMIN' AND EXISTS(SELECT 1 FROM edu_college_admins a JOIN edu_colleges c ON c.id=a.college_id WHERE a.user_id=u.id AND a.active AND c.active AND a.college_id=NEW.college_id) OR
 u.role='ROLE_STUDENT' AND EXISTS(SELECT 1 FROM edu_students s JOIN edu_student_domains m ON m.student_id=s.id JOIN edu_colleges c ON c.id=s.college_id WHERE s.user_id=u.id AND s.id=NEW.student_id AND c.id=NEW.college_id AND c.active AND m.domain=NEW.domain AND m.status='APPROVED')
 )) THEN RAISE EXCEPTION 'Progress report author must match the college, student and domain scope' USING ERRCODE='23514'; END IF;RETURN NEW;
END $$;
