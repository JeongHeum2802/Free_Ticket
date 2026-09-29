import { api } from "./axios";

export function isAdminTableReadOnly(table: string) {
  return table === "orders" || table === "payments";
}

export function isAdminColumnReadOnly(table: string, column: string) {
  return isAdminTableReadOnly(table) || column === "id"
    || (table === "users" && column === "customer_key")
    || (table === "tickets" && column === "sold_ticket");
}

export function userChoices(table: string, column: string): string[] | undefined {
  if (table !== "users") return undefined;
  if (column === "role") return ["ADMIN", "USER"];
  if (column === "status") return ["ACTIVE", "INACTIVE"];
  return undefined;
}

export type AdminTablePage = {
  table: string;
  columns: string[];
  rows: (string | null)[][];
  totalRows: number;
  page: number;
  size: number;
  createFields?: { name: string; type: string; required: boolean }[];
};

export type AdminRowChange = {
  id: string;
  originalValues: Record<string, string | null>;
  values: Record<string, string | null>;
};

export type AdminRowDeletion = { id: string; originalValues: Record<string, string | null> };

export async function updateAdminTable(table: string, changes: AdminRowChange[], page: number,
  creations: Record<string, string | null>[] = [], deletions: AdminRowDeletion[] = []) {
  const response = await api.patch<{ data: AdminTablePage }>(
    `/admin/tables/${encodeURIComponent(table)}`,
    { changes, creations, deletions },
    { params: { page, size: 100 } },
  );
  return response.data.data;
}

export async function getAdminTables(signal?: AbortSignal) {
  const response = await api.get<{ data: string[] }>("/admin/tables", { signal });
  return response.data.data;
}

export async function getAdminTable(table: string, page: number, signal?: AbortSignal) {
  const response = await api.get<{ data: AdminTablePage }>(
    `/admin/tables/${encodeURIComponent(table)}`,
    { params: { page, size: 100 }, signal },
  );
  return response.data.data;
}

export type ReconciliationIssue = {
  id: number;
  orderId: string;
  issueType: string;
  pgStatus: string;
  dbOrderStatus: string | null;
  dbPaymentStatus: string | null;
  transactionAtUtc: string;
  detectedAtUtc: string;
  resolvedAtUtc: string | null;
  resolvedBy: number | null;
};

export type ReconciliationFilters = {
  orderId?: string;
  issueType?: string;
  from?: string;
  to?: string;
  resolution?: string;
};

export type ReconciliationPage = {
  items: ReconciliationIssue[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
};

export type ReconciliationPg = {
  status: string;
  totalAmount: number | null;
  balanceAmount: number | null;
  cancels: { amount: number; status: string; canceledAt: string | null }[];
  identityMatches: boolean;
  canApplyFullCancellation: boolean;
  blockedReason: string | null;
  checkedAtUtc: string;
};
export type ReconciliationAction = {
  id: number; actorId: number; action: string; result: string; reason: string; errorCode: string | null;
  occurredAtUtc: string; pg: ReconciliationPg | null;
  beforeOrderStatus: string | null; beforePaymentStatus: string | null;
  afterOrderStatus: string | null; afterPaymentStatus: string | null;
  soldBefore: number | null; soldAfter: number | null;
};
export type ReconciliationDetail = {
  issue: ReconciliationIssue;
  currentOrder: { status: string | null; quantity: number; totalAmount: number } | null;
  currentPayment: { status: string; amount: number } | null;
  checkedAtUtc: string;
  latestCheck: ReconciliationPg | null;
  history: ReconciliationAction[];
};

export async function getAdminReconciliationIssues(filters: ReconciliationFilters, page: number, signal?: AbortSignal) {
  const response = await api.get<{ data: ReconciliationPage }>("/admin/payment-reconciliation/issues", {
    params: { ...filters, page, size: 20 }, signal,
  });
  return response.data.data;
}

export async function performAdminReconciliationAction(id: number,
  operation: "recheck" | "apply-full-cancellation" | "notes", reason: string) {
  const response = await api.post<{ data: ReconciliationDetail }>(
    `/admin/payment-reconciliation/issues/${id}/${operation}`, { reason });
  return response.data.data;
}

export async function getAdminReconciliationIssue(id: number, signal?: AbortSignal) {
  const response = await api.get<{ data: ReconciliationDetail }>(`/admin/payment-reconciliation/issues/${id}`, { signal });
  return response.data.data;
}
