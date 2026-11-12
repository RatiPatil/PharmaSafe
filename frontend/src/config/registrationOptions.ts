/** Controlled choices for application forms; organization and identifiers remain applicant supplied. */
const departments = ['Drug Safety / Pharmacovigilance', 'Safety', 'Clinical Research', 'Medical Affairs', 'Quality Assurance', 'Regulatory Affairs', 'Clinical Operations', 'Other / not listed'];
const experience = ['Less than 1 year', '1–3 years', '4–7 years', '8–15 years', 'More than 15 years'];
export const registrationOptions: Record<string, string[]> = {
 department: departments,
 designation: ['Drug Safety Associate', 'Case Processing Specialist', 'Physician', 'Medical Reviewer', 'Quality Reviewer', 'Safety Manager', 'Regulatory Reporting Specialist', 'Auditor', 'Other / not listed'],
 processingDepartment: departments, reviewDepartment: departments, qualityDepartment: departments,
 managementDepartment: departments, reportingDepartment: departments,
 businessUnit: ['Global Drug Safety', 'Regional Safety Operations', 'Clinical Development', 'Post-marketing Safety', 'Contract Safety Services', 'Other / not listed'],
 countryRegion: ['India', 'United States', 'United Kingdom', 'European Union', 'Canada', 'Australia', 'Japan', 'Asia Pacific', 'Middle East and Africa', 'Latin America', 'Global', 'Other / not listed'],
 medicalQualification: ['MBBS', 'MD', 'DO', 'MBChB', 'PharmD', 'BDS / MDS', 'Other / not listed'],
 specialization: ['Clinical pharmacology', 'Internal medicine', 'General medicine', 'Oncology', 'Neurology', 'Pediatrics', 'Cardiology', 'Other / not listed'],
 processingExperience: experience, qualityExperience: experience, managementExperience: experience,
 reportingExperience: experience, auditExperience: experience, yearsExperience: experience,
 training: ['ICH E2B(R3) and case processing', 'GVP training completed', 'Company safety SOP training completed', 'Training in progress', 'Other / not listed'],
 certifications: ['GVP / Pharmacovigilance certification', 'ICH-GCP certification', 'Quality auditing certification', 'Internal SOP training', 'Other / not listed'],
 auditScope: ['Case processing and data integrity', 'Medical and quality review', 'Regulatory reporting', 'System access and security', 'Full safety workflow'],
};
