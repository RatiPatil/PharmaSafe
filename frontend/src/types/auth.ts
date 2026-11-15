export interface User {
  id: number;
  staffCode?:string;
  administrativeLevel?:string;
  processingTeamId?:number|null;
  managedRole?:string|null;
  username: string;
  fullName?: string | null;
  department?: string | null;
  role: string;
  enabled: boolean;
  locked: boolean;
  lastSuccessfulLoginAt?: string | null;
  createdAt: string;
}

export interface AuthResponse {
  success: boolean;
  message: string;
  user?: User | null;
}

export interface AuditLogItem {
  id: number;
  eventType: string;
  username: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  status: string;
  details: string | null;
  createdAt: string;
}
