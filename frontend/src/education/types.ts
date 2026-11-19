export type Domain = {
  code: string;
  name: string;
  description: string;
  stages: string[];
  evidenceFields: {
    key: string;
    label: string;
    choices: string[];
    max: number;
  }[];
};
export type Stats = {
  total: number;
  students?: number;
  colleges?: number;
  averageProgress: number;
  overdue: number;
  statusCounts: Record<string, number>;
};
export type College = {
  id: number;
  code: string;
  name: string;
  active: boolean;
  rowVersion: number;
  students?: number;
};
export type Catalog = { colleges: College[]; domains: Domain[] };
export type Card = {
  code: string;
  name: string;
  description: string;
  authorized: boolean;
  status: string;
  stats: Stats | null;
};
export type Overview = {
  role: string;
  fullName: string;
  username: string;
  collegeId: number | null;
  assignedDomain: string | null;
  pendingStudentApprovals?:number;
  studentCode: string | null;
  domains: Card[];
  updatedAt: string;
};
export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
};
export type Student = {
  id: number;
  studentCode: string;
  collegeId: number;
  collegeName: string;
  fullName: string;
  username: string;
  email: string;
  createdAt: string;
  active?: boolean;
  rowVersion: number;
  domains?: { domain: string; status: string }[];
  work?: Stats;
};
export type Request = {
  id: number;
  domain: string;
  status: string;
  rowVersion: number;
  reason: string;
  student: Student;
};
export type Admin = {
  id: number;
  kind: string;
  username: string;
  fullName: string;
  collegeId: number | null;
  domain: string | null;
  active: boolean;
  rowVersion: number;
};
export type Report = {
  id: number;
  domain: string;
  collegeId: number | null;
  studentCode: string | null;
  status: string | null;
  fileName: string;
  sha256: string;
  generatedBy: string;
  createdAt: string;
};
export type Task = {
  id: number;
  collegeId: number;
  collegeName: string;
  studentCode: string;
  studentName: string;
  domain: string;
  stage: string;
  title: string;
  description: string;
  instructions: string;
  dueAt: string;
  status: string;
  progress: number;
  requireDocument: boolean;
  rowVersion: number;
  assignedBy: string;
  createdAt: string;
  updatedAt: string;
  archived?: boolean;
  sourceOrderId?: number | null;
  submissions?: Page<Submission>;
};
export type Submission = {
  id: number;
  status: string;
  progress: number;
  workDone: string;
  pendingWork: string;
  remainingWork: string;
  evidence: Record<string, string>;
  createdAt: string;
  sourceVersion: number;
  studentCode: string;
  documents: { id: number; fileName: string; sha256: string }[];
  feedback: {
    id: number;
    outcome: string;
    feedback: string;
    reviewer: string;
    createdAt: string;
  }[];
};
export const statuses: Record<string, string> = {
  NOT_STARTED: "Not started",
  IN_PROGRESS: "In progress",
  SUBMITTED: "Submitted for review",
  RETURNED: "Returned for correction",
  COMPLETED: "Completed",
};
export const emptyPage = <T>(): Page<T> => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  page: 0,
});
