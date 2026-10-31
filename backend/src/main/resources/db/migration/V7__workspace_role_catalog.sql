-- Additive catalog: keep existing users.role and all existing case foreign keys.
CREATE TABLE workspace_roles (
    code VARCHAR(32) PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    workspace VARCHAR(32) NOT NULL,
    assignable BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO workspace_roles (code, display_name, workspace, assignable) VALUES
('ROLE_ADMIN', 'Administrator', 'administration', TRUE),
('ROLE_CASE_PROCESSOR', 'PV Analyst', 'processing', TRUE),
('ROLE_MEDICAL_REVIEWER', 'Medical Reviewer', 'medical-review', TRUE),
('ROLE_QUALITY_REVIEWER', 'Quality Reviewer', 'quality-review', TRUE),
('ROLE_SAFETY_MANAGER', 'Safety Manager', 'workflow', TRUE),
('ROLE_REPORTING_SPECIALIST', 'Reporting Specialist', 'reporting', TRUE),
('ROLE_AUDITOR', 'Auditor / Viewer', 'audit', TRUE),
('ROLE_USER', 'Legacy PV Analyst', 'processing', FALSE);
-- Preserve custom historical roles without granting them a new workspace.
INSERT INTO workspace_roles (code, display_name, workspace, assignable)
SELECT DISTINCT role, role, 'restricted', FALSE FROM users
WHERE role NOT IN (SELECT code FROM workspace_roles);
ALTER TABLE users ADD CONSTRAINT fk_users_workspace_role
    FOREIGN KEY (role) REFERENCES workspace_roles(code);
-- Support workflow queues without duplicating cases into role-specific tables.
CREATE INDEX idx_cases_workflow_updated ON cases(workflow_state, updated_at, id);

-- Optimistic concurrency protects edits/routing from silently overwriting each other.
ALTER TABLE cases ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
