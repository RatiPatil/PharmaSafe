-- Frozen, reproducible analytical populations and a separate managerial review ledger.
CREATE TABLE pv_case_series (
 id VARCHAR(36) PRIMARY KEY,analyst_user_id BIGINT NOT NULL REFERENCES users(id),
 name VARCHAR(160) NOT NULL CHECK(length(trim(name))>0),purpose VARCHAR(3000) NOT NULL CHECK(length(trim(purpose))>0),
 case_count INT NOT NULL CHECK(case_count BETWEEN 1 AND 200),captured_at TIMESTAMPTZ NOT NULL,
 criteria_json JSONB NOT NULL CHECK(jsonb_typeof(criteria_json)='object'),
 snapshot_json JSONB NOT NULL CHECK(jsonb_typeof(snapshot_json)='object' AND jsonb_typeof(snapshot_json->'caseListing')='array' AND jsonb_array_length(snapshot_json->'caseListing')=case_count),
 pdf_bytes BYTEA NOT NULL CHECK(octet_length(pdf_bytes)>0),pdf_sha256 VARCHAR(64) NOT NULL CHECK(pdf_sha256 ~ '^[a-f0-9]{64}$')
);
CREATE INDEX idx_pv_series_author ON pv_case_series(analyst_user_id,captured_at DESC,id);
CREATE TABLE pv_series_cases (
 id BIGSERIAL PRIMARY KEY,series_id VARCHAR(36) NOT NULL REFERENCES pv_case_series(id),case_id BIGINT NOT NULL REFERENCES cases(id),
 source_row_version BIGINT NOT NULL CHECK(source_row_version>=0),case_number VARCHAR(64) NOT NULL,
 snapshot_json JSONB NOT NULL CHECK(jsonb_typeof(snapshot_json)='object'),UNIQUE(series_id,case_id)
);
CREATE INDEX idx_pv_series_source_case ON pv_series_cases(case_id,series_id);
ALTER TABLE pv_analysis_records ADD COLUMN series_id VARCHAR(36) REFERENCES pv_case_series(id);
ALTER TABLE pv_analysis_records ADD COLUMN supersedes_id VARCHAR(36) UNIQUE REFERENCES pv_analysis_records(id);
ALTER TABLE pv_analysis_records ADD CONSTRAINT chk_pv_no_self_revision CHECK(supersedes_id IS DISTINCT FROM id);
CREATE INDEX idx_pv_analysis_series ON pv_analysis_records(series_id);
CREATE TABLE pv_analysis_reviews (
 id BIGSERIAL PRIMARY KEY,analysis_id VARCHAR(36) NOT NULL REFERENCES pv_analysis_records(id),reviewer_user_id BIGINT NOT NULL REFERENCES users(id),
 previous_review_id BIGINT, outcome VARCHAR(32) NOT NULL CHECK(outcome IN ('ACKNOWLEDGED','RETURNED','FOLLOW_UP_REQUIRED')),
 comments VARCHAR(3000) NOT NULL CHECK(length(trim(comments))>0),follow_up_due_date DATE,reviewed_at TIMESTAMPTZ NOT NULL,
 snapshot_json JSONB NOT NULL CHECK(jsonb_typeof(snapshot_json)='object'),pdf_bytes BYTEA NOT NULL CHECK(octet_length(pdf_bytes)>0),pdf_sha256 VARCHAR(64) NOT NULL CHECK(pdf_sha256 ~ '^[a-f0-9]{64}$'),
 UNIQUE(id,analysis_id),UNIQUE(previous_review_id),FOREIGN KEY(previous_review_id,analysis_id) REFERENCES pv_analysis_reviews(id,analysis_id),
 CHECK((outcome='FOLLOW_UP_REQUIRED')=(follow_up_due_date IS NOT NULL))
);
CREATE UNIQUE INDEX uq_pv_first_review ON pv_analysis_reviews(analysis_id) WHERE previous_review_id IS NULL;
CREATE INDEX idx_pv_analysis_reviews_history ON pv_analysis_reviews(analysis_id,id DESC);
CREATE FUNCTION guard_pv_series_author() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.analyst_user_id AND role='ROLE_PV_ANALYST' AND enabled AND NOT locked AND account_status='APPROVED') THEN RAISE EXCEPTION 'Active PV Analyst required' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_pv_series_author BEFORE INSERT ON pv_case_series FOR EACH ROW EXECUTE FUNCTION guard_pv_series_author();
CREATE FUNCTION guard_pv_series_member() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM cases WHERE id=NEW.case_id AND case_number=NEW.case_number AND row_version=NEW.source_row_version AND workflow_state IN ('Approved','Locked','Closed')) THEN RAISE EXCEPTION 'Reviewed source case and current revision required at capture' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM pv_case_series s CROSS JOIN LATERAL jsonb_array_elements(s.snapshot_json->'caseListing') row_data WHERE s.id=NEW.series_id AND row_data->>'caseNumber'=NEW.case_number AND (row_data->>'caseId')::BIGINT=NEW.case_id AND (row_data->>'sourceRowVersion')::BIGINT=NEW.source_row_version AND row_data=NEW.snapshot_json) THEN RAISE EXCEPTION 'Series member must match its frozen evidence' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_pv_series_member BEFORE INSERT ON pv_series_cases FOR EACH ROW EXECUTE FUNCTION guard_pv_series_member();
CREATE FUNCTION verify_pv_series_population() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE series_key VARCHAR(36); expected_count INT; actual_count INT;
BEGIN
 IF TG_TABLE_NAME='pv_case_series' THEN series_key:=NEW.id; ELSE series_key:=NEW.series_id; END IF;
 SELECT case_count INTO expected_count FROM pv_case_series WHERE id=series_key;
 SELECT count(*) INTO actual_count FROM pv_series_cases WHERE series_id=series_key;
 IF expected_count IS DISTINCT FROM actual_count THEN RAISE EXCEPTION 'Frozen case-series membership must be complete' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_pv_series_population AFTER INSERT ON pv_case_series DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_pv_series_population();
CREATE CONSTRAINT TRIGGER trg_pv_member_population AFTER INSERT ON pv_series_cases DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_pv_series_population();
CREATE FUNCTION guard_pv_report_lineage() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE last_outcome VARCHAR(32);
BEGIN
 IF NEW.series_id IS NOT NULL THEN
  IF NOT EXISTS(SELECT 1 FROM pv_case_series WHERE id=NEW.series_id AND analyst_user_id=NEW.analyst_user_id) THEN RAISE EXCEPTION 'Report requires own analyst case series' USING ERRCODE='23514'; END IF;
  IF NEW.case_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM pv_series_cases WHERE series_id=NEW.series_id AND case_id=NEW.case_id) THEN RAISE EXCEPTION 'Linked case must belong to series' USING ERRCODE='23514'; END IF;
 END IF;
 IF NEW.supersedes_id IS NOT NULL THEN
  PERFORM 1 FROM pv_analysis_records WHERE id=NEW.supersedes_id FOR UPDATE;
  IF NOT EXISTS(SELECT 1 FROM pv_analysis_records WHERE id=NEW.supersedes_id AND analyst_user_id=NEW.analyst_user_id) THEN RAISE EXCEPTION 'Revision author must match original author' USING ERRCODE='23514'; END IF;
  SELECT outcome INTO last_outcome FROM pv_analysis_reviews WHERE analysis_id=NEW.supersedes_id ORDER BY id DESC LIMIT 1;
  IF last_outcome IS NULL OR last_outcome NOT IN ('RETURNED','FOLLOW_UP_REQUIRED') THEN RAISE EXCEPTION 'Revision requires a returned or follow-up review' USING ERRCODE='23514'; END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER trg_pv_report_lineage BEFORE INSERT ON pv_analysis_records FOR EACH ROW EXECUTE FUNCTION guard_pv_report_lineage();
CREATE FUNCTION guard_pv_analysis_review() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE latest BIGINT;
BEGIN
 PERFORM 1 FROM pv_analysis_records WHERE id=NEW.analysis_id FOR UPDATE;
 IF NOT EXISTS(SELECT 1 FROM users WHERE id=NEW.reviewer_user_id AND role IN ('ROLE_ADMIN','ROLE_SAFETY_MANAGER') AND enabled AND NOT locked AND account_status='APPROVED') THEN RAISE EXCEPTION 'Active Safety Manager or global Super Admin required' USING ERRCODE='23514'; END IF;
 SELECT id INTO latest FROM pv_analysis_reviews WHERE analysis_id=NEW.analysis_id ORDER BY id DESC LIMIT 1;
 IF NEW.previous_review_id IS DISTINCT FROM latest THEN RAISE EXCEPTION 'Stale analysis review' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM pv_analysis_records WHERE supersedes_id=NEW.analysis_id) THEN RAISE EXCEPTION 'Review the replacement report' USING ERRCODE='23514'; END IF;
 IF NEW.follow_up_due_date<CURRENT_DATE THEN RAISE EXCEPTION 'Follow-up due date cannot be in the past' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_pv_review_actor BEFORE INSERT ON pv_analysis_reviews FOR EACH ROW EXECUTE FUNCTION guard_pv_analysis_review();
CREATE TRIGGER trg_pv_series_immutable BEFORE UPDATE OR DELETE ON pv_case_series FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_pv_series_member_immutable BEFORE UPDATE OR DELETE ON pv_series_cases FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TRIGGER trg_pv_review_immutable BEFORE UPDATE OR DELETE ON pv_analysis_reviews FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
