-- Preserve historical rows; enforce valid counts/periods on new writes.
ALTER TABLE safety_reports ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE safety_reports ADD CONSTRAINT chk_report_counts CHECK(total_cases>=0 AND serious_cases>=0 AND fatal_cases>=0 AND fatal_cases<=serious_cases AND serious_cases<=total_cases) NOT VALID;
ALTER TABLE safety_reports ADD CONSTRAINT chk_report_period CHECK(period_start IS NULL OR period_end IS NULL OR period_end>=period_start) NOT VALID;
ALTER TABLE safety_reports ADD CONSTRAINT chk_report_lock_point CHECK(data_lock_point IS NULL OR period_end IS NULL OR data_lock_point>=period_end) NOT VALID;
ALTER TABLE cases ADD COLUMN synthetic_record BOOLEAN NOT NULL DEFAULT FALSE;
CREATE FUNCTION preserve_synthetic_identity() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.synthetic_record IS DISTINCT FROM OLD.synthetic_record THEN RAISE EXCEPTION 'Synthetic identity is permanent' USING ERRCODE='23514'; END IF; RETURN NEW; END $$;
CREATE TRIGGER trg_case_synthetic_identity BEFORE UPDATE OF synthetic_record ON cases FOR EACH ROW EXECUTE FUNCTION preserve_synthetic_identity();
CREATE TABLE report_acknowledgements (
 id BIGSERIAL PRIMARY KEY, report_id BIGINT NOT NULL REFERENCES safety_reports(id),
 outcome VARCHAR(16) NOT NULL CHECK(outcome IN ('ACCEPTED','REJECTED')),
 reference VARCHAR(128) NOT NULL CHECK(length(trim(reference))>0), received_at TIMESTAMPTZ NOT NULL,
 message VARCHAR(2000) NOT NULL, reason VARCHAR(2000) NOT NULL CHECK(length(trim(reason))>0),
 recorded_by_user_id BIGINT NOT NULL REFERENCES users(id), recorded_by_username VARCHAR(64) NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL, source_report_version BIGINT NOT NULL CHECK(source_report_version>=0),
 CHECK(received_at<=recorded_at)
);
CREATE INDEX idx_report_ack_history ON report_acknowledgements(report_id,recorded_at DESC,id DESC);
CREATE TRIGGER trg_report_ack_immutable BEFORE UPDATE OR DELETE ON report_acknowledgements FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
CREATE TABLE report_document_exports (
 id VARCHAR(36) PRIMARY KEY,report_id BIGINT NOT NULL REFERENCES safety_reports(id),
 source_report_version BIGINT NOT NULL CHECK(source_report_version>=0),
 generated_by_user_id BIGINT NOT NULL REFERENCES users(id), generated_by_username VARCHAR(64) NOT NULL,
 generated_at TIMESTAMPTZ NOT NULL,snapshot_json JSONB NOT NULL,pdf_bytes BYTEA NOT NULL CHECK(octet_length(pdf_bytes)>0),
 pdf_sha256 VARCHAR(64) NOT NULL CHECK(pdf_sha256 ~ '^[a-f0-9]{64}$'),file_name VARCHAR(180) NOT NULL
);
CREATE INDEX idx_report_document_history ON report_document_exports(report_id,generated_at DESC,id);
CREATE TRIGGER trg_report_document_immutable BEFORE UPDATE OR DELETE ON report_document_exports FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
-- Permanent dataset flags and a report lock prevent mixed real/training case links.
CREATE FUNCTION verify_report_case_dataset() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE training BOOLEAN;
BEGIN
 PERFORM id FROM safety_reports WHERE id=NEW.report_id FOR UPDATE;
 SELECT synthetic_record INTO training FROM cases WHERE id=NEW.case_id;
 IF EXISTS(SELECT 1 FROM safety_report_cases link JOIN cases c ON c.id=link.case_id WHERE link.report_id=NEW.report_id AND c.synthetic_record IS DISTINCT FROM training) THEN
  RAISE EXCEPTION 'Keep synthetic and real cases in separate report records' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_report_case_dataset BEFORE INSERT OR UPDATE ON safety_report_cases FOR EACH ROW EXECUTE FUNCTION verify_report_case_dataset();
