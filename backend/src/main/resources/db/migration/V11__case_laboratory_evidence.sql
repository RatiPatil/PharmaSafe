-- Additive clinical evidence: existing case, patient and receipt records are preserved.
CREATE TABLE case_lab_results (
 id BIGSERIAL PRIMARY KEY,
 case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE RESTRICT,
 test_name VARCHAR(200) NOT NULL CHECK(length(trim(test_name))>0),
 test_date DATE NOT NULL,
 result_text VARCHAR(1000) NOT NULL CHECK(length(trim(result_text))>0),
 unit VARCHAR(80), normal_low NUMERIC(18,6), normal_high NUMERIC(18,6),
 interpretation VARCHAR(20) NOT NULL CHECK(interpretation IN ('UNKNOWN','NORMAL','ABNORMAL','HIGH','LOW')),
 notes VARCHAR(4000),
 recorded_by VARCHAR(64) NOT NULL REFERENCES users(username) ON DELETE RESTRICT,
 recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
 void_reason VARCHAR(1000), voided_by VARCHAR(64) REFERENCES users(username) ON DELETE RESTRICT,
 voided_at TIMESTAMP WITH TIME ZONE,
 CONSTRAINT chk_lab_range CHECK(normal_low IS NULL OR normal_high IS NULL OR normal_low<=normal_high),
 CONSTRAINT chk_lab_void CHECK((voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL) OR (voided_at IS NOT NULL AND voided_by IS NOT NULL AND length(trim(void_reason))>0 AND voided_at>=recorded_at))
);
CREATE INDEX idx_lab_case_chronology ON case_lab_results(case_id,test_date DESC,id DESC);
CREATE FUNCTION protect_lab_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Laboratory evidence cannot be deleted; void with reason' USING ERRCODE='23514'; END IF;
 IF OLD.voided_at IS NOT NULL OR (to_jsonb(NEW)-ARRAY['void_reason','voided_by','voided_at']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['void_reason','voided_by','voided_at']) THEN
  RAISE EXCEPTION 'Original laboratory evidence is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER trg_lab_evidence BEFORE UPDATE OR DELETE ON case_lab_results FOR EACH ROW EXECUTE FUNCTION protect_lab_evidence();
