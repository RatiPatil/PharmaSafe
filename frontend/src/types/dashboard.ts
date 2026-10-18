export interface DashboardSummary {
  totalCases: number;
  assignedCases: number;
  awaitingReviewCases: number;
  userAwaitingReviewCases: number;
  seriousCases: number;
  expeditedCases: number;
  overdueActionItems: number | null;
  actionItemsStatus: string;
  actionItemsNote: string;
}

export interface RecentActivity {
  id: number;
  eventType: string;
  username: string;
  details: string;
  createdAt: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
