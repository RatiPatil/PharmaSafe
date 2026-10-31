export interface SafetyCaseSummary {
  id: number;
  caseNumber: string;
  countryCode: string;
  reportType: string;
  workflowState: string;
  priority: string;
  serious: boolean;
  fatal: boolean;
  primaryProduct: string;
  primaryEvent: string;
  initialReceiptDate: string;
  latestReceivedDate: string;
  versionNumber: number;
  assignedToUsername?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PatientData {
  patientIdentifier?: string;
  ageValue?: number;
  ageUnit?: string;
  dateOfBirth?: string;
  gender?: string;
  weightKg?: number;
  heightCm?: number;
  pregnant?: boolean;
  medicalHistory?: string;
}

export interface ReporterData {
  id?: number;
  primary: boolean;
  qualification: string;
  title?: string;
  firstName?: string;
  lastName?: string;
  institution?: string;
  department?: string;
  city?: string;
  stateProvince?: string;
  countryCode: string;
  postalCode?: string;
  email?: string;
  phone?: string;
}

export interface ProductData {
  id?: number;
  productType: string;
  productName: string;
  activeSubstance?: string;
  indication?: string;
  dosageText?: string;
  dosageAmount?: number;
  dosageUnit?: string;
  frequency?: string;
  formulation?: string;
  routeOfAdministration?: string;
  batchLotNumber?: string;
  startDate?: string;
  stopDate?: string;
  ongoing?: boolean;
  dechallengeResult?: string;
  rechallengeResult?: string;
}

export interface EventData {
  id?: number;
  eventVerbatim: string;
  meddraPt?: string;
  meddraLlt?: string;
  meddraSoc?: string;
  onsetDate: string;
  stopDate?: string;
  outcome: string;
  serious: boolean;
  resultsInDeath: boolean;
  lifeThreatening: boolean;
  requiresHospitalization: boolean;
  disablingIncapacitating: boolean;
  congenitalAnomaly: boolean;
  otherMedicallyImportant: boolean;
}

export interface AssessmentData {
  id?: number;
  productName?: string;
  eventVerbatim?: string;
  causalityReporter?: string;
  causalityCompany?: string;
  expectedness?: string;
  assessmentComment?: string;
  assessedBy?: string;
}

export interface MedicalHistoryData {
  id?: number;
  conditionName: string;
  startDate?: string;
  endDate?: string;
  continuing?: boolean;
  notes?: string;
}

export interface ReferenceData {
  id?: number;
  referenceType: string;
  referenceId: string;
  description?: string;
}

export interface AttachmentData {
  id?: number;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  classification?: string;
  uploadedAt?: string;
}

export interface SafetyCaseDetails {
  locked?: boolean;
  lockedByUsername?: string;
  lockedAt?: string;
  id: number;
  caseNumber: string;
  countryCode: string;
  reportType: string;
  initialReceiptDate: string;
  latestReceivedDate: string;
  workflowState: string;
  priority: string;
  serious: boolean;
  fatal: boolean;
  narrative?: string;
  companyComments?: string;
  versionNumber: number;
  createdByUsername?: string;
  updatedByUsername?: string;
  assignedToUsername?: string;
  createdAt: string;
  updatedAt: string;
  patient?: PatientData;
  reporters: ReporterData[];
  products: ProductData[];
  events: EventData[];
  assessments: AssessmentData[];
  medicalHistoryList: MedicalHistoryData[];
  references: ReferenceData[];
  attachments: AttachmentData[];
}

export interface CaseFormInput {
  countryCode: string;
  reportType: string;
  initialReceiptDate: string;
  latestReceivedDate: string;
  workflowState: string;
  priority: string;
  draft: boolean;
  narrative: string;
  companyComments: string;
  assignedToUsername?: string;
  patient: PatientData;
  reporters: ReporterData[];
  products: ProductData[];
  events: EventData[];
  assessments: AssessmentData[];
  medicalHistoryList: MedicalHistoryData[];
  references: ReferenceData[];
  attachments: AttachmentData[];
}
