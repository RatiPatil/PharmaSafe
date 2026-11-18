export type MedicalLevel = "ADMIN" | "TEAM_LEAD" | "EMPLOYEE";
export type TeamPerson = {
  userId: number;
  staffCode: string;
  username: string;
  fullName: string;
  accountStatus: string;
  enabled: boolean;
};
export type MedicalTeam = {
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
export type MedicalSummary = {
  level: MedicalLevel;
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
  clinicalQueue?: { total: number; serious: number; assessed: number };
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
export type MedicalPage<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
};
export const medicalStages = {
  SOURCE_REVIEW: "Clinical source & timeline review",
  CLINICAL_ASSESSMENT: "Product / event medical assessment",
  SERIOUSNESS_REVIEW: "Seriousness evaluation",
  CAUSALITY: "Causality assessment",
  EXPECTEDNESS: "Expectedness / reference review",
  MEDICAL_NARRATIVE: "Medical narrative & conclusion",
  QC_RESPONSE: "Response to QC medical queries",
  QUALITY_HANDOFF: "Quality review handoff",
};
export const workStatusLabels: Record<string, string> = {
  NOT_STARTED: "Not started",
  IN_PROGRESS: "In progress",
  BLOCKED: "Pending information",
  COMPLETED: "Completed",
};
export const checklistLabels: Record<string, string> = {
  sourceReviewed: "Source clinical evidence reviewed",
  seriousnessReviewed: "Seriousness criteria reviewed",
  causalityReviewed: "Causality assessments recorded",
  expectednessReviewed: "Expectedness reviewed against source",
  timelineReviewed: "Clinical timeline reviewed",
  narrativeReviewed: "Medical narrative completed",
  handoffReady: "Medical handoff evidence checked",
};
export const statusColors: Record<string, string> = {
  NOT_STARTED: "#4c86d2",
  IN_PROGRESS: "#8d6bd1",
  BLOCKED: "#d69a36",
  COMPLETED: "#2b9e81",
};
