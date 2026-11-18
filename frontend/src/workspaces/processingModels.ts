export type ProcessingLevel = "ADMIN" | "TEAM_LEAD" | "EMPLOYEE";
export type TeamPerson = {
  userId: number;
  staffCode: string;
  username: string;
  fullName: string;
  accountStatus: string;
  enabled: boolean;
};
export type ProcessingTeam = {
  id: number;
  teamCode: string;
  name: string;
  lead: TeamPerson;
  active: boolean;
  registrationOpen: boolean;
  maxMembers: number;
  occupiedSeats: number;
  registrationPath: string;
  rowVersion: number;
};
export type ProcessingSummary = {
  level: ProcessingLevel;
  actor: TeamPerson;
  statusCounts: Record<string, number>;
  totalAssignments: number;
  overdue: number;
  pendingRequests: number;
  activeEmployees: number;
  teams: {
    teamId: number;
    teamCode: string;
    name: string;
    assigned: number;
    completed: number;
  }[];
  updatedAt: string;
};
export type WorkAssignment = {
  id: number;
  teamId: number;
  teamCode: string;
  teamName: string;
  teamActive: boolean;
  employee: TeamPerson;
  title: string;
  stage: string;
  clinicalRequired?: boolean;
  priority: string;
  dueAt: string;
  status: string;
  progress: number;
  rowVersion: number;
  updatedAt: string;
  overdue: boolean;
};
export type ProcessingPage<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
};
export const processingStages = {
  INTAKE: "Case receipt & intake",
  VALIDATION: "Four minimum criteria",
  DUPLICATE_REVIEW: "Duplicate review",
  CODING: "Medical & drug coding",
  NARRATIVE: "Narrative preparation",
  MEDICAL_HANDOFF: "Medical review handoff",
  QC_CORRECTION: "Quality control corrections",
  REPORTING_HANDOFF: "Reporting handoff",
};
export const workStatusLabels: Record<string, string> = {
  NOT_STARTED: "Not started",
  IN_PROGRESS: "In progress",
  BLOCKED: "Pending information",
  COMPLETED: "Completed",
};
export const checklistLabels: Record<string, string> = {
  patientVerified: "Identifiable patient confirmed",
  reporterVerified: "Identifiable reporter confirmed",
  drugVerified: "Suspected drug confirmed",
  eventVerified: "Adverse event confirmed",
  duplicateChecked: "Duplicate check performed",
  codingReviewed: "Coding verified against the documented dictionary",
  narrativeReviewed: "Narrative prepared and reviewed",
};
export const statusColors: Record<string, string> = {
  NOT_STARTED: "#4c86d2",
  IN_PROGRESS: "#8d6bd1",
  BLOCKED: "#d69a36",
  COMPLETED: "#2b9e81",
};
