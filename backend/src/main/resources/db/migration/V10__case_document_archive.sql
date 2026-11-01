-- Immutable generated case documents. Existing clinical records are not rewritten.
CREATE TABLE case_document_exports (
 id VARCHAR(36) PRIMARY KEY,
 case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE RESTRICT,
 document_kind VARCHAR(24) NOT NULL CHECK (document_kind IN ('CASE_FORM','MEDICAL_SUMMARY')),
 final_document BOOLEAN NOT NULL DEFAULT FALSE,
 case_version INTEGER NOT NULL CHECK (case_version > 0),
 source_row_version BIGINT NOT NULL CHECK (source_row_version >= 0),
 workflow_state VARCHAR(32) NOT NULL,
 template_version VARCHAR(24) NOT NULL,
 generated_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 generated_by_username VARCHAR(64) NOT NULL,
 generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 snapshot_json JSONB NOT NULL,
 pdf_bytes BYTEA NOT NULL CHECK (octet_length(pdf_bytes) > 0),
 pdf_sha256 VARCHAR(64) NOT NULL CHECK (pdf_sha256 ~ '^[0-9a-f]{64}$'),
 file_name VARCHAR(180) NOT NULL,
 CONSTRAINT chk_final_document_state CHECK (NOT final_document OR workflow_state = 'Closed')
);
CREATE INDEX idx_case_documents_case_date ON case_document_exports(case_id,generated_at DESC,id);
CREATE INDEX idx_case_documents_final ON case_document_exports(final_document,generated_at DESC);
CREATE FUNCTION protect_case_document_archive() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Generated case documents are immutable' USING ERRCODE='23514';
END; $$;
CREATE TRIGGER case_documents_immutable BEFORE UPDATE OR DELETE ON case_document_exports
 FOR EACH ROW EXECUTE FUNCTION protect_case_document_archive();
