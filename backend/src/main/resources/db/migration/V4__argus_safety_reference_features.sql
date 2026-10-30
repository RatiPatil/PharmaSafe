-- PharmaSafe Stage 4: Argus Safety Reference Feature Enhancements
-- 1. User enhancements (Full Name, Department, Additional Roles)
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(128);
ALTER TABLE users ADD COLUMN IF NOT EXISTS department VARCHAR(64) DEFAULT 'Safety';

-- Update existing administrator user with sensible default name and department
UPDATE users SET full_name = 'System Administrator', department = 'Safety Operations' WHERE username = 'admin' AND full_name IS NULL;

-- 2. Case Locking and Extended Workflow support
ALTER TABLE cases ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cases ADD COLUMN IF NOT EXISTS locked_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE cases ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP WITH TIME ZONE;

-- Drop old check constraint if exists and re-add extended workflow states
ALTER TABLE cases DROP CONSTRAINT IF EXISTS chk_cases_workflow_state;
ALTER TABLE cases ADD CONSTRAINT chk_cases_workflow_state CHECK (
    workflow_state IN (
        'Draft', 
        'Intake', 
        'Triage', 
        'Data Entry', 
        'Quality Review', 
        'QC Review', 
        'Medical Review', 
        'Locked', 
        'Closed', 
        'Submitted'
    )
);

CREATE INDEX IF NOT EXISTS idx_cases_locked ON cases(locked);
CREATE INDEX IF NOT EXISTS idx_cases_locked_by ON cases(locked_by_user_id);

-- 3. MedDRA Terminology Table
CREATE TABLE IF NOT EXISTS meddra_terms (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    term VARCHAR(255) NOT NULL,
    soc VARCHAR(255) NOT NULL,
    hlgt VARCHAR(255),
    hlt VARCHAR(255),
    llt VARCHAR(255),
    level VARCHAR(16) NOT NULL DEFAULT 'PT',
    status VARCHAR(16) NOT NULL DEFAULT 'Active',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_meddra_code ON meddra_terms(code);
CREATE INDEX IF NOT EXISTS idx_meddra_term ON meddra_terms(term);
CREATE INDEX IF NOT EXISTS idx_meddra_soc ON meddra_terms(soc);
CREATE INDEX IF NOT EXISTS idx_meddra_level ON meddra_terms(level);

-- Seed MedDRA terms from reference dataset
INSERT INTO meddra_terms (code, term, soc, hlgt, hlt, llt, level, status) VALUES
('PT100001', 'Hepatitis', 'Hepatobiliary disorders', 'Hepatic and hepatobiliary disorders', 'Hepatitis non-infectious', 'Hepatitis NEC', 'PT', 'Active'),
('PT100002', 'Liver injury', 'Hepatobiliary disorders', 'Hepatic and hepatobiliary disorders', 'Hepatocellular damage and hepatitis NEC', 'Liver injury', 'PT', 'Active'),
('PT100003', 'Jaundice', 'Hepatobiliary disorders', 'Bile duct disorders', 'Jaundice and hyperbilirubinaemia', 'Jaundice', 'PT', 'Active'),
('PT100004', 'Elevated liver enzymes', 'Hepatobiliary disorders', 'Hepatobiliary investigations', 'Liver function analyses', 'Transaminases increased', 'PT', 'Active'),
('PT100005', 'Cirrhosis', 'Hepatobiliary disorders', 'Hepatic and hepatobiliary disorders', 'Hepatic fibrosis and cirrhosis', 'Cirrhosis', 'PT', 'Active'),
('PT100006', 'Cholestasis', 'Hepatobiliary disorders', 'Bile duct disorders', 'Cholestasis and jaundice', 'Cholestasis', 'PT', 'Active'),
('PT200001', 'Rash', 'Skin and subcutaneous tissue disorders', 'Epidermal and dermal conditions', 'Rashes, eruptions and exanthems NEC', 'Rash NEC', 'PT', 'Active'),
('PT200002', 'Dermatitis', 'Skin and subcutaneous tissue disorders', 'Epidermal and dermal conditions', 'Dermatitis and eczema', 'Dermatitis', 'PT', 'Active'),
('PT200003', 'Stevens-Johnson syndrome', 'Skin and subcutaneous tissue disorders', 'Bullous conditions', 'Erythema multiforme and Stevens-Johnson syndrome', 'Stevens-Johnson syndrome', 'PT', 'Active'),
('PT200004', 'Urticaria', 'Skin and subcutaneous tissue disorders', 'Angioedema and urticaria', 'Urticarias', 'Urticaria', 'PT', 'Active'),
('PT200005', 'Pruritus', 'Skin and subcutaneous tissue disorders', 'Epidermal and dermal conditions', 'Pruritus NEC', 'Pruritus', 'PT', 'Active'),
('PT200006', 'Alopecia', 'Skin and subcutaneous tissue disorders', 'Cornification and dystrophic skin disorders', 'Alopecias', 'Alopecia', 'PT', 'Active'),
('PT300001', 'Arthralgia', 'Musculoskeletal and connective tissue disorders', 'Joint disorders', 'Joint related signs and symptoms', 'Arthralgia', 'PT', 'Active'),
('PT300002', 'Myalgia', 'Musculoskeletal and connective tissue disorders', 'Muscle disorders', 'Myalgias', 'Myalgia', 'PT', 'Active'),
('PT300003', 'Back pain', 'Musculoskeletal and connective tissue disorders', 'Musculoskeletal and connective tissue disorders NEC', 'Musculoskeletal and connective tissue signs and symptoms NEC', 'Back pain', 'PT', 'Active'),
('PT300004', 'Joint swelling', 'Musculoskeletal and connective tissue disorders', 'Joint disorders', 'Joint related signs and symptoms', 'Swelling joint', 'PT', 'Active'),
('PT300005', 'Muscle weakness', 'Musculoskeletal and connective tissue disorders', 'Muscle disorders', 'Muscle related signs and symptoms NEC', 'Muscle weakness', 'PT', 'Active'),
('PT400001', 'Myocardial infarction', 'Cardiac disorders', 'Coronary artery disorders', 'Ischaemic coronary artery disorders', 'Myocardial infarction', 'PT', 'Active'),
('PT400002', 'Arrhythmia', 'Cardiac disorders', 'Cardiac arrhythmias', 'Cardiac arrhythmias NEC', 'Arrhythmia', 'PT', 'Active'),
('PT400003', 'Heart failure', 'Cardiac disorders', 'Heart failures', 'Heart failures NEC', 'Cardiac failure congestive', 'PT', 'Active'),
('PT400004', 'Hypertension', 'Cardiac disorders', 'Vascular hypertensive disorders', 'Hypertension NEC', 'Hypertension', 'PT', 'Active'),
('PT400005', 'Hypotension', 'Cardiac disorders', 'Vascular hypotensive disorders', 'Hypotension NEC', 'Hypotension', 'PT', 'Active'),
('PT400006', 'Tachycardia', 'Cardiac disorders', 'Cardiac arrhythmias', 'Rate and rhythm disorders NEC', 'Tachycardia', 'PT', 'Active'),
('PT500001', 'Headache', 'Nervous system disorders', 'Headaches', 'Headaches NEC', 'Headache', 'PT', 'Active'),
('PT500002', 'Dizziness', 'Nervous system disorders', 'Neurological disorders NEC', 'Neurological signs and symptoms NEC', 'Dizziness', 'PT', 'Active'),
('PT500003', 'Seizure', 'Nervous system disorders', 'Seizures (incl subtypes)', 'Seizures NEC', 'Seizure', 'PT', 'Active'),
('PT500004', 'Neuropathy', 'Nervous system disorders', 'Peripheral neuropathies', 'Peripheral neuropathies NEC', 'Neuropathy peripheral', 'PT', 'Active'),
('PT500005', 'Tremor', 'Nervous system disorders', 'Movement disorders (incl parkinsonism)', 'Tremor (excl parkinsonian)', 'Tremor', 'PT', 'Active'),
('PT500006', 'Paresthesia', 'Nervous system disorders', 'Neurological disorders NEC', 'Paraesthesias and dysaesthesias', 'Paresthesia', 'PT', 'Active'),
('PT600001', 'Anaphylaxis', 'Immune system disorders', 'Allergic conditions', 'Anaphylactic responses', 'Anaphylaxis', 'PT', 'Active'),
('PT600002', 'Angioedema', 'Immune system disorders', 'Allergic conditions', 'Angioneurotic oedemas', 'Angioedema', 'PT', 'Active'),
('PT600003', 'Drug reaction', 'Immune system disorders', 'Allergic conditions', 'Allergic conditions NEC', 'Drug reaction', 'PT', 'Active')
ON CONFLICT (code) DO NOTHING;

-- 4. Safety Reports & Periodic / Expedited Regulatory Reporting
CREATE TABLE IF NOT EXISTS safety_reports (
    id BIGSERIAL PRIMARY KEY,
    report_id VARCHAR(64) NOT NULL UNIQUE,
    report_type VARCHAR(32) NOT NULL, -- '7-day', '15-day', 'PSUR', 'DSUR', 'PBRER', 'PADER'
    product VARCHAR(128) NOT NULL,
    authority VARCHAR(32) NOT NULL DEFAULT 'FDA', -- 'FDA', 'EMA', 'CDSCO', 'PMDA', 'TGA'
    data_lock_point DATE,
    period_start DATE,
    period_end DATE,
    due_date DATE NOT NULL,
    submitted_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL DEFAULT 'Pending', -- 'Pending', 'Submitted', 'Overdue'
    total_cases INT NOT NULL DEFAULT 0,
    serious_cases INT NOT NULL DEFAULT 0,
    fatal_cases INT NOT NULL DEFAULT 0,
    summary TEXT,
    conclusions TEXT,
    recommendations TEXT,
    created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_report_status CHECK (status IN ('Pending', 'Submitted', 'Overdue')),
    CONSTRAINT chk_report_type CHECK (report_type IN ('7-day', '15-day', 'PSUR', 'DSUR', 'PBRER', 'PADER'))
);

CREATE INDEX IF NOT EXISTS idx_safety_reports_report_id ON safety_reports(report_id);
CREATE INDEX IF NOT EXISTS idx_safety_reports_due_date ON safety_reports(due_date);
CREATE INDEX IF NOT EXISTS idx_safety_reports_status ON safety_reports(status);
CREATE INDEX IF NOT EXISTS idx_safety_reports_product ON safety_reports(product);
CREATE INDEX IF NOT EXISTS idx_safety_reports_type ON safety_reports(report_type);

-- 5. Safety Report to Case Links
CREATE TABLE IF NOT EXISTS safety_report_cases (
    report_id BIGINT NOT NULL REFERENCES safety_reports(id) ON DELETE CASCADE,
    case_id BIGINT NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    PRIMARY KEY (report_id, case_id)
);

CREATE INDEX IF NOT EXISTS idx_safety_report_cases_report ON safety_report_cases(report_id);
CREATE INDEX IF NOT EXISTS idx_safety_report_cases_case ON safety_report_cases(case_id);
