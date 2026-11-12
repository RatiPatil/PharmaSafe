-- Existing accounts remain approved; no case data or credentials are replaced.
ALTER TABLE users ADD COLUMN account_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED'
 CHECK (account_status IN ('PENDING','APPROVED','REJECTED','SUSPENDED'));
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD COLUMN employee_id VARCHAR(64);
ALTER TABLE users ADD COLUMN mobile_number VARCHAR(32);
ALTER TABLE users ADD COLUMN designation VARCHAR(128);
ALTER TABLE users ADD COLUMN organization VARCHAR(128);
ALTER TABLE users ADD COLUMN approved_by BIGINT REFERENCES users(id);
ALTER TABLE users ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX uq_user_email ON users(lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_user_employee ON users(lower(employee_id)) WHERE employee_id IS NOT NULL;
CREATE TABLE registration_requests (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
 requested_role VARCHAR(32) NOT NULL REFERENCES workspace_roles(code),
 reason VARCHAR(2000) NOT NULL, additional_information VARCHAR(2000),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, reviewed_by BIGINT REFERENCES users(id),
 reviewed_at TIMESTAMP WITH TIME ZONE, decision_reason VARCHAR(2000), row_version BIGINT NOT NULL DEFAULT 0,
 CHECK(requested_role NOT IN ('ROLE_ADMIN','ROLE_USER'))
);
CREATE TABLE registration_details (
 request_id BIGINT NOT NULL REFERENCES registration_requests(id),
 field_name VARCHAR(64) NOT NULL, field_value VARCHAR(1000) NOT NULL,
 PRIMARY KEY(request_id,field_name)
);
CREATE INDEX idx_registration_date ON registration_requests(created_at);
CREATE INDEX idx_users_access_filters ON users(account_status,role,department,organization);
CREATE TABLE password_reset_requests (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
 status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED','USED')),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, reviewed_by BIGINT REFERENCES users(id),
 reviewed_at TIMESTAMP WITH TIME ZONE, decision_reason VARCHAR(2000),
 token_hash VARCHAR(64) UNIQUE, issued_auth_version BIGINT, expires_at TIMESTAMP WITH TIME ZONE,
 used_at TIMESTAMP WITH TIME ZONE, row_version BIGINT NOT NULL DEFAULT 0,
 CHECK(status <> 'APPROVED' OR (token_hash IS NOT NULL AND expires_at IS NOT NULL AND issued_auth_version IS NOT NULL AND issued_auth_version >= 0)),
 CHECK(status <> 'USED' OR (used_at IS NOT NULL AND token_hash IS NULL))
);
CREATE INDEX idx_reset_user_date ON password_reset_requests(user_id,created_at);
CREATE TABLE access_rate_limits (
 bucket VARCHAR(128) PRIMARY KEY, window_start TIMESTAMP WITH TIME ZONE NOT NULL, attempts INT NOT NULL CHECK(attempts > 0)
);
CREATE FUNCTION protect_security_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Security audit records are append-only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER trg_security_audit BEFORE UPDATE OR DELETE ON audit_events
 FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
-- Preserve submitted identity, requested role and original role-specific evidence.
CREATE FUNCTION protect_registration_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Registration evidence is retained' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.user_id,NEW.requested_role,NEW.reason,NEW.additional_information,NEW.created_at)
    IS DISTINCT FROM ROW(OLD.user_id,OLD.requested_role,OLD.reason,OLD.additional_information,OLD.created_at)
 THEN RAISE EXCEPTION 'Original registration evidence cannot be changed' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_registration_evidence BEFORE UPDATE OR DELETE ON registration_requests
 FOR EACH ROW EXECUTE FUNCTION protect_registration_evidence();
CREATE TRIGGER trg_registration_details BEFORE UPDATE OR DELETE ON registration_details
 FOR EACH ROW EXECUTE FUNCTION protect_security_audit();
