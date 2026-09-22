import { api } from "./axios";

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
