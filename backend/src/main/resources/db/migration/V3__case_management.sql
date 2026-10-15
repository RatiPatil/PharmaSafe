-- PharmaSafe Stage 4: Safety Case Management Schema
-- Normalized relational schema for ICH E2B(R3) compliant pharmacovigilance cases

CREATE SEQUENCE IF NOT EXISTS case_number_seq START WITH 1001 INCREMENT BY 1;

-- 1. Primary Safety Cases Table
CREATE TABLE IF NOT EXISTS cases (
    id BIGSERIAL PRIMARY KEY,
    case_number VARCHAR(64) NOT NULL UNIQUE,
    country_code VARCHAR(3) NOT NULL,
    report_type VARCHAR(32) NOT NULL,
    initial_receipt_date DATE NOT NULL,
    latest_received_date DATE NOT NULL,
    workflow_state VARCHAR(32) NOT NULL DEFAULT 'Data Entry',
    priority VARCHAR(16) NOT NULL DEFAULT 'Routine',
    is_serious BOOLEAN NOT NULL DEFAULT FALSE,
    is_fatal BOOLEAN NOT NULL DEFAULT FALSE,
    narrative TEXT,
    company_comments TEXT,
    version_number INT NOT NULL DEFAULT 1,
    created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    assigned_to_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_cases_dates CHECK (latest_received_date >= initial_receipt_date),
    CONSTRAINT chk_cases_priority CHECK (priority IN ('Routine', 'Priority', 'Expedited')),
    CONSTRAINT chk_cases_workflow_state CHECK (workflow_state IN ('Draft', 'Data Entry', 'Quality Review', 'Medical Review', 'Closed'))
);

-- 2. Patient Demographics & Characteristics (ICH E2B Section B.1)
CREATE TABLE IF NOT EXISTS case_patients (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL UNIQUE REFERENCES cases(id) ON DELETE CASCADE,
    patient_identifier VARCHAR(64),
    age_value NUMERIC(5,2),
    age_unit VARCHAR(16) DEFAULT 'Years',
    date_of_birth DATE,
    gender VARCHAR(16) DEFAULT 'Unknown',
    weight_kg NUMERIC(6,2),
    height_cm NUMERIC(6,2),
    pregnant BOOLEAN DEFAULT FALSE,
    medical_history TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_patient_gender CHECK (gender IN ('Male', 'Female', 'Unknown', 'Undifferentiated')),
    CONSTRAINT chk_patient_age_unit CHECK (age_unit IN ('Years', 'Months', 'Weeks', 'Days', 'Decades'))
);

-- 3. Healthcare Professional & Consumer Reporters (ICH E2B Section A.2)
CREATE TABLE IF NOT EXISTS case_reporters (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    qualification VARCHAR(32) NOT NULL DEFAULT 'Physician',
    title VARCHAR(32),
    first_name VARCHAR(64),
    last_name VARCHAR(64),
    institution VARCHAR(128),
    department VARCHAR(128),
    city VARCHAR(64),
    state_province VARCHAR(64),
    country_code VARCHAR(3) NOT NULL DEFAULT 'USA',
    postal_code VARCHAR(20),
    email VARCHAR(128),
    phone VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_reporter_qualification CHECK (qualification IN ('Physician', 'Pharmacist', 'Other Health Professional', 'Lawyer', 'Consumer'))
);

-- 4. Medicinal Products & Dosages (ICH E2B Section B.4)
CREATE TABLE IF NOT EXISTS case_products (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    product_type VARCHAR(16) NOT NULL DEFAULT 'Suspect',
    product_name VARCHAR(128) NOT NULL,
    active_substance VARCHAR(128),
    indication VARCHAR(255),
    dosage_text VARCHAR(128),
    dosage_amount NUMERIC(10,3),
    dosage_unit VARCHAR(16),
    frequency VARCHAR(32),
    formulation VARCHAR(64),
    route_of_administration VARCHAR(64),
    batch_lot_number VARCHAR(64),
    start_date DATE,
    stop_date DATE,
    ongoing BOOLEAN DEFAULT FALSE,
    dechallenge_result VARCHAR(32) DEFAULT 'Not Applicable',
    rechallenge_result VARCHAR(32) DEFAULT 'Not Applicable',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_product_type CHECK (product_type IN ('Suspect', 'Concomitant', 'Interacting', 'Treatment')),
    CONSTRAINT chk_product_dates CHECK (stop_date IS NULL OR start_date IS NULL OR stop_date >= start_date)
);

-- 5. Adverse Events & Outcomes (ICH E2B Section B.2)
CREATE TABLE IF NOT EXISTS case_events (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    event_verbatim VARCHAR(255) NOT NULL,
    meddra_pt VARCHAR(128),
    meddra_llt VARCHAR(128),
    meddra_soc VARCHAR(128),
    onset_date DATE NOT NULL,
    stop_date DATE,
    outcome VARCHAR(32) NOT NULL DEFAULT 'Unknown',
    is_serious BOOLEAN NOT NULL DEFAULT FALSE,
    results_in_death BOOLEAN NOT NULL DEFAULT FALSE,
    life_threatening BOOLEAN NOT NULL DEFAULT FALSE,
    requires_hospitalization BOOLEAN NOT NULL DEFAULT FALSE,
    disabling_incapacitating BOOLEAN NOT NULL DEFAULT FALSE,
    congenital_anomaly BOOLEAN NOT NULL DEFAULT FALSE,
    other_medically_important BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_event_outcome CHECK (outcome IN ('Recovered/Resolved', 'Recovering/Resolving', 'Not Recovered', 'Fatal', 'Recovered with Sequelae', 'Unknown')),
    CONSTRAINT chk_event_dates CHECK (stop_date IS NULL OR stop_date >= onset_date)
);

-- 6. Case Assessments & Causality
CREATE TABLE IF NOT EXISTS case_assessments (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    product_name VARCHAR(128),
    event_verbatim VARCHAR(255),
    causality_reporter VARCHAR(32) DEFAULT 'Unassessable',
    causality_company VARCHAR(32) DEFAULT 'Reasonable Possibility',
    expectedness VARCHAR(16) DEFAULT 'Unexpected',
    assessment_comment TEXT,
    assessed_by VARCHAR(64),
    assessed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 7. Relevant Medical History
CREATE TABLE IF NOT EXISTS case_medical_history (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    condition_name VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date DATE,
    continuing BOOLEAN DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 8. Source References & Citations
CREATE TABLE IF NOT EXISTS case_references (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    reference_type VARCHAR(64) NOT NULL DEFAULT 'Source Reference',
    reference_id VARCHAR(128) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 9. Secure Attachment Metadata
CREATE TABLE IF NOT EXISTS case_attachments (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    classification VARCHAR(64) DEFAULT 'Source Document',
    uploaded_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Indexes for performance and referential integrity
CREATE INDEX IF NOT EXISTS idx_cases_case_number ON cases(case_number);
CREATE INDEX IF NOT EXISTS idx_cases_country_code ON cases(country_code);
CREATE INDEX IF NOT EXISTS idx_cases_workflow_state ON cases(workflow_state);
CREATE INDEX IF NOT EXISTS idx_cases_latest_received_date ON cases(latest_received_date);
CREATE INDEX IF NOT EXISTS idx_cases_assigned_user ON cases(assigned_to_user_id);
CREATE INDEX IF NOT EXISTS idx_case_reporters_case_id ON case_reporters(case_id);
CREATE INDEX IF NOT EXISTS idx_case_patients_case_id ON case_patients(case_id);
CREATE INDEX IF NOT EXISTS idx_case_products_case_id ON case_products(case_id);
CREATE INDEX IF NOT EXISTS idx_case_events_case_id ON case_events(case_id);
CREATE INDEX IF NOT EXISTS idx_case_assessments_case_id ON case_assessments(case_id);
CREATE INDEX IF NOT EXISTS idx_case_med_hist_case_id ON case_medical_history(case_id);
CREATE INDEX IF NOT EXISTS idx_case_references_case_id ON case_references(case_id);
CREATE INDEX IF NOT EXISTS idx_case_attachments_case_id ON case_attachments(case_id);
