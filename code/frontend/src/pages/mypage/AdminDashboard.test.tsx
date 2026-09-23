// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import AdminDashboard from "./AdminDashboard";
import { getAdminTable, getAdminTables, updateAdminTable } from "../../api/admin";

const auth = vi.hoisted(() => ({ user: { role: "ADMIN" }, loading: false }));
vi.mock("../../context/AuthContext", () => ({ useAuth: () => auth }));
vi.mock("../../api/admin", async importOriginal => ({ ...await importOriginal<typeof import("../../api/admin")>(), getAdminTables: vi.fn(), getAdminTable: vi.fn(), updateAdminTable: vi.fn() }));

beforeEach(() => {
  vi.resetAllMocks();
  auth.user.role = "ADMIN";
  vi.mocked(getAdminTables).mockResolvedValue(["users", "events"]);
  vi.mocked(getAdminTable).mockImplementation(async (table, page) => ({
    table, columns: ["id", "name"], rows: table === "events" ? [] : [["1", page ? "second page" : null]],
    totalRows: table === "events" ? 0 : 101, page, size: 100,
    createFields: [{ name: "name", type: "text", required: true }, ...(table === "users" ? [
      { name: "password", type: "password", required: true }, { name: "role", type: "text", required: true },
      { name: "status", type: "text", required: true }] : [])],
  }));
});
afterEach(cleanup);

function mount() {
  render(<MemoryRouter initialEntries={["/mypage/admin"]}><Routes>
    <Route path="/mypage/admin" element={<AdminDashboard />} />
    <Route path="/mypage/tickets" element={<p>내 티켓</p>} />
  </Routes></MemoryRouter>);
}

test.each(["orders", "payments"])("%s has no creation form but keeps editing controls", async table => {
  vi.mocked(getAdminTables).mockResolvedValue([table]);
  mount();
  expect(await screen.findByRole("textbox", { name: "name (ID: 1)" })).toBeTruthy();
  expect(screen.queryByRole("form", { name: "새 데이터 추가" })).toBeNull();
  expect(screen.queryByRole("button", { name: "추가" })).toBeNull();
  expect(screen.getByRole("button", { name: "수정" })).toBeTruthy();
  expect(screen.getByRole("button", { name: "삭제 (ID: 1)" })).toBeTruthy();
});

test("shows DB columns and nulls, paginates, and resets page when selecting another table", async () => {
  mount();
  expect(await screen.findByRole("columnheader", { name: "name" })).toBeTruthy();
  expect(screen.queryByText("NULL")).toBeNull();
  expect(screen.queryByRole("checkbox")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "다음" }));
  expect(await screen.findByText("second page")).toBeTruthy();
  fireEvent.change(screen.getByLabelText("테이블 선택"), { target: { value: "events" } });
  expect(await screen.findByText("데이터가 없습니다.")).toBeTruthy();
  expect(screen.getByRole("columnheader", { name: "name" })).toBeTruthy();
  expect((screen.getByRole("button", { name: "이전" }) as HTMLButtonElement).disabled).toBe(true);
});

test("shows a retryable error when reading rows fails", async () => {
  vi.mocked(getAdminTable).mockRejectedValueOnce(new Error("network"));
  mount();
  expect(await screen.findByRole("alert")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "새로고침" }));
  expect(await screen.findByRole("textbox", { name: "name (ID: 1)" })).toBeTruthy();
});

test("non-admin is redirected without requesting table data", async () => {
  auth.user.role = "USER";
  mount();
  expect(await screen.findByText("내 티켓")).toBeTruthy();
  expect(getAdminTables).not.toHaveBeenCalled();
  expect(getAdminTable).not.toHaveBeenCalled();
});

test("enables save only for changed values and renders the persisted response after saving", async () => {
  mount();
  const input = await screen.findByRole("textbox", { name: "name (ID: 1)" });
  const save = screen.getByRole("button", { name: "수정" }) as HTMLButtonElement;
  expect(save.disabled).toBe(true);
  fireEvent.change(input, { target: { value: "edited" } });
  expect(save.disabled).toBe(false);
  expect((screen.getByLabelText("테이블 선택") as HTMLSelectElement).disabled).toBe(true);
  fireEvent.change(input, { target: { value: "" } });
  expect(save.disabled).toBe(true);
  fireEvent.change(input, { target: { value: "edited" } });
  vi.mocked(updateAdminTable).mockResolvedValue({ table: "users", columns: ["id", "name"],
    rows: [["1", "persisted"]], totalRows: 101, page: 0, size: 100 });
  fireEvent.click(save);
  await waitFor(() => expect((screen.getByRole("textbox", { name: "name (ID: 1)" }) as HTMLTextAreaElement).value).toBe("persisted"));
  expect(updateAdminTable).toHaveBeenCalledWith("users", [{ id: "1", originalValues: { name: null }, values: { name: "edited" } }], 0, [], []);
  expect(save.disabled).toBe(true);
  expect((screen.getByLabelText("테이블 선택") as HTMLSelectElement).disabled).toBe(false);
});

test("retains edits after a failed save and permits cancellation", async () => {
  vi.mocked(updateAdminTable).mockRejectedValue(new Error("failed"));
  mount();
  const input = await screen.findByRole("textbox", { name: "name (ID: 1)" });
  fireEvent.change(input, { target: { value: "keep me" } });
  fireEvent.click(screen.getByRole("button", { name: "수정" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect((input as HTMLTextAreaElement).value).toBe("keep me");
  expect((screen.getByRole("button", { name: "수정" }) as HTMLButtonElement).disabled).toBe(false);
  fireEvent.click(screen.getByRole("button", { name: "변경 취소" }));
  expect((input as HTMLTextAreaElement).value).toBe("");
  expect((screen.getByRole("button", { name: "수정" }) as HTMLButtonElement).disabled).toBe(true);
});

test("adds a completed form directly to the persisted table", async () => {
  mount();
  await screen.findByRole("textbox", { name: "name (ID: 1)" });
  const form = within(screen.getByRole("form", { name: "새 데이터 추가" }));
  const add = form.getByRole("button", { name: "추가" }) as HTMLButtonElement;
  expect(add.disabled).toBe(true);
  fireEvent.change(form.getByLabelText("name *"), { target: { value: "new user" } });
  fireEvent.change(form.getByLabelText("초기 비밀번호 *"), { target: { value: "password123" } });
  expect(add.disabled).toBe(false);
  expect(within(form.getByLabelText("role *")).getAllByRole("option").map(option => option.textContent)).toEqual(["ADMIN", "USER"]);
  expect(within(form.getByLabelText("status *")).getAllByRole("option").map(option => option.textContent)).toEqual(["ACTIVE", "INACTIVE"]);
  expect(updateAdminTable).not.toHaveBeenCalled();
  vi.mocked(updateAdminTable).mockResolvedValue({ table: "users", columns: ["id", "name"],
    rows: [["2", "new user"]], totalRows: 1, page: 0, size: 100 });
  fireEvent.click(add);
  expect(await screen.findByRole("textbox", { name: "name (ID: 2)" })).toBeTruthy();
  expect(screen.queryByRole("textbox", { name: "name (ID: 1)" })).toBeNull();
  expect(updateAdminTable).toHaveBeenCalledWith("users", [], 0, [{ name: "new user", password: "password123", role: "USER", status: "ACTIVE" }], []);
});

test("can undo a staged deletion without saving", async () => {
  mount();
  await screen.findByRole("textbox", { name: "name (ID: 1)" });
  fireEvent.click(screen.getByRole("button", { name: "삭제 (ID: 1)" }));
  fireEvent.click(screen.getByRole("button", { name: "삭제 취소 (ID: 1)" }));
  expect((screen.getByRole("button", { name: "수정" }) as HTMLButtonElement).disabled).toBe(true);
  expect(updateAdminTable).not.toHaveBeenCalled();
});

test("uses user role and status dropdowns in the existing table", async () => {
  vi.mocked(getAdminTable).mockResolvedValue({ table: "users", columns: ["id", "role", "status"],
    rows: [["1", "USER", "ACTIVE"]], page: 0, size: 100, totalRows: 1 });
  mount();
  const role = await screen.findByRole("combobox", { name: "role (ID: 1)" });
  expect(within(role).getAllByRole("option").map(option => option.textContent)).toEqual(["ADMIN", "USER"]);
  const status = screen.getByRole("combobox", { name: "status (ID: 1)" });
  expect(within(status).getAllByRole("option").map(option => option.textContent)).toEqual(["ACTIVE", "INACTIVE"]);
  fireEvent.change(status, { target: { value: "INACTIVE" } });
  expect((screen.getByRole("button", { name: "수정" }) as HTMLButtonElement).disabled).toBe(false);
});

test("retains a failed creation form and adapts fields when the table changes", async () => {
  vi.mocked(updateAdminTable).mockRejectedValue(new Error("failed"));
  mount();
  await screen.findByRole("textbox", { name: "name (ID: 1)" });
  fireEvent.change(screen.getByLabelText("테이블 선택"), { target: { value: "events" } });
  await screen.findByText("데이터가 없습니다.");
  const form = within(screen.getByRole("form", { name: "새 데이터 추가" }));
  expect(form.queryByLabelText("초기 비밀번호 *")).toBeNull();
  fireEvent.change(form.getByLabelText("name *"), { target: { value: "new event" } });
  fireEvent.click(form.getByRole("button", { name: "추가" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect((form.getByLabelText("name *") as HTMLInputElement).value).toBe("new event");
});
