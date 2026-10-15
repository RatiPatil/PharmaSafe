-- PharmaSafe Initial Schema Migration
-- Stage 1: Base System Metadata and Health Verification

CREATE TABLE IF NOT EXISTS system_metadata (
    id VARCHAR(64) PRIMARY KEY,
    property_name VARCHAR(128) NOT NULL UNIQUE,
    property_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_metadata (id, property_name, property_value, description)
VALUES 
    ('meta-001', 'SYSTEM_NAME', 'PharmaSafe Pharmacovigilance Case Management', 'Application name'),
    ('meta-002', 'SCHEMA_VERSION', '1.0.0', 'Initial database schema version'),
    ('meta-003', 'INSPIRED_BY', 'Oracle Argus Safety 7.0.3', 'Architecture baseline')
ON CONFLICT (property_name) DO NOTHING;
