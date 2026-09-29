// @vitest-environment jsdom
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AxiosError, type AxiosAdapter } from "axios";
import { api } from "../../api/axios";
import AdminDashboard from "./AdminDashboard";

const auth = vi.hoisted(() => ({ user: { id: 1, role: "ADMIN" }, loading: false }));
vi.mock("../../context/AuthContext", () => ({ useAuth: () => auth }));

const originalAdapter = api.defaults.adapter;
const calls: { url?: string; params?: Record<string, unknown>; method?: string; data?: string }[] = [];
let failList = false;
let currentStatus = "CANCELED", checked = false, resolved = false, partial = false, failApply = false;
let history: Record<string, unknown>[] = [];
const issue = { id: 1, orderId: "ORD-FIRST", issueType: "PAID_PG_CANCELED", pgStatus: "CANCELED",
  dbOrderStatus: "PAID", dbPaymentStatus: "DONE", transactionAtUtc: "2026-09-29T00:20:00Z",
  detectedAtUtc: "2026-09-29T00:30:00Z" };

beforeEach(() => {
  calls.length = 0;
  auth.user.role = "ADMIN";
  failList = false;
  currentStatus = "CANCELED"; checked = false; resolved = false; partial = false; failApply = false; history = [];
  const adapter: AxiosAdapter = async config => {
    calls.push({ url: config.url, params: config.params, method: config.method, data: config.data });
    let data: unknown;
    if (config.url === "/admin/tables") data = [];
    else if (config.url === "/admin/payment-reconciliation/issues") {
      if (failList) throw new Error("network unavailable");
      const page = config.params.page ?? 0;
      const row = config.params.orderId === "ORD-FILTER"
        ? { ...issue, id: 3, orderId: "ORD-FILTER", issueType: "PAYMENT_IDENTITY_MISMATCH" }
        : page ? { ...issue, id: 2, orderId: "ORD-MISSING" } : issue;
      data = { items: [row], page, size: 20, totalElements: 21, totalPages: 2 };
    } else if (config.url?.startsWith("/admin/payment-reconciliation/issues/1")) {
      const operation = config.url.split("/").at(-1);
      if (config.method === "post") {
        if (operation === "apply-full-cancellation" && failApply) {
          throw new AxiosError("blocked", "ERR_BAD_REQUEST", config, undefined, {
            status: 409, statusText: "Conflict", headers: {}, config,
            data: { code: "RECONCILIATION_BLOCKED", message: "토스 상태가 변경되어 취소를 반영할 수 없습니다." },
          });
        }
        if (operation === "recheck") checked = true;
        if (operation === "apply-full-cancellation") { currentStatus = "CANCELED"; resolved = true; }
        history.unshift({ id: history.length + 1, actorId: 1,
          action: operation === "recheck" ? "RECHECK" : operation === "notes" ? "NOTE" : "APPLY_FULL_CANCELLATION",
          result: "SUCCESS", reason: JSON.parse(config.data).reason, errorCode: null,
          occurredAtUtc: "2026-09-29T00:41:00Z", beforeOrderStatus: "PAID", beforePaymentStatus: "DONE",
          afterOrderStatus: currentStatus, afterPaymentStatus: currentStatus === "PAID" ? "DONE" : "CANCELED",
          soldBefore: operation === "apply-full-cancellation" ? 5 : null,
          soldAfter: operation === "apply-full-cancellation" ? 3 : null });
      }
      data = { issue: { ...issue, resolvedAtUtc: resolved ? "2026-09-29T00:42:00Z" : null, resolvedBy: resolved ? 1 : null },
        currentOrder: { status: currentStatus, quantity: 2, totalAmount: 10000 },
        currentPayment: { status: currentStatus === "PAID" ? "DONE" : "CANCELED", amount: 10000 },
        checkedAtUtc: "2026-09-29T00:40:00Z", history,
        latestCheck: checked ? { status: partial ? "PARTIAL_CANCELED" : "CANCELED", totalAmount: 10000,
          balanceAmount: partial ? 9000 : 0, cancels: [{ amount: partial ? 1000 : 10000, status: "DONE", canceledAt: "2026-09-29T10:00:00+09:00" }],
          checkedAtUtc: "2026-09-29T00:41:00Z", identityMatches: true,
          canApplyFullCancellation: !partial && !resolved, blockedReason: partial ? "부분 취소는 별도 조사가 필요합니다." : null } : null };
    } else if (config.url === "/admin/payment-reconciliation/issues/2") {
      data = { issue: { ...issue, id: 2, orderId: "ORD-MISSING", issueType: "UNMATCHED_PG_CANCELLATION" },
        currentOrder: null, currentPayment: null, checkedAtUtc: "2026-09-29T00:40:00Z", latestCheck: null, history: [] };
    } else throw new Error(`Unexpected request: ${config.url}`);
    return { data: { data }, status: 200, statusText: "OK", headers: {}, config };
  };
  api.defaults.adapter = adapter;
});

afterEach(() => { cleanup(); api.defaults.adapter = originalAdapter; });

function mount() {
  render(<MemoryRouter initialEntries={["/mypage/admin"]}><Routes>
    <Route path="/mypage/admin" element={<AdminDashboard />} />
    <Route path="/mypage/tickets" element={<p>내 티켓</p>} />
  </Routes></MemoryRouter>);
}

async function openList() {
  mount();
  fireEvent.click(screen.getByRole("button", { name: "결제 불일치" }));
  await screen.findByText("ORD-FIRST");
}

test("admin sees detection records and distinct current state in detail", async () => {
  await openList();
  expect(within(screen.getByRole("table")).getByText("PG 전액 취소")).toBeTruthy();
  expect(screen.getByText(/09:30:00/)).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "상세 보기 ORD-FIRST" }));
  const detail = within(await screen.findByRole("region", { name: "결제 불일치 상세" }));
  expect(await detail.findByText("현재 내부 상태")).toBeTruthy();
  expect(detail.getByText("2매")).toBeTruthy();
  expect(detail.getAllByText("10,000원")).toHaveLength(2);
  expect(detail.getAllByText("취소 완료").length).toBe(2);
  expect(detail.getByText("결제 완료")).toBeTruthy();
  expect(detail.queryByRole("button", { name: /정정|해결/ })).toBeNull();
});

test("operator checks PG, confirms cancellation, and sees stock and actor history", async () => {
  currentStatus = "PAID";
  await openList();
  fireEvent.click(screen.getByRole("button", { name: "상세 보기 ORD-FIRST" }));
  fireEvent.change(await screen.findByLabelText("처리·조사 사유"), { target: { value: "고객 취소 확인" } });
  fireEvent.click(screen.getByRole("button", { name: "PG 상태 재확인" }));
  expect(await screen.findByText("취소 잔액")).toBeTruthy();
  expect(screen.getByText(/취소 1: 10,000원 · 취소 완료/)).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "전액 취소 반영" }));
  expect(calls.some(call => call.url?.endsWith("apply-full-cancellation"))).toBe(false);
  fireEvent.click(screen.getByRole("button", { name: "취소 반영 확정" }));
  expect(await screen.findByText("해결 완료")).toBeTruthy();
  expect(screen.getByText(/5매 → 3매/)).toBeTruthy();
  expect(screen.getAllByText(/관리자 #1/).length).toBeGreaterThan(0);
  expect(calls.find(call => call.url?.endsWith("apply-full-cancellation"))?.data).toBe('{"reason":"고객 취소 확인"}');
});

test("partial cancellation is blocked and investigation note keeps the issue open", async () => {
  currentStatus = "PAID"; partial = true;
  await openList();
  fireEvent.click(screen.getByRole("button", { name: "상세 보기 ORD-FIRST" }));
  fireEvent.change(await screen.findByLabelText("처리·조사 사유"), { target: { value: "부분 취소 수량 확인 필요" } });
  fireEvent.click(screen.getByRole("button", { name: "PG 상태 재확인" }));
  expect(await screen.findByText("부분 취소는 별도 조사가 필요합니다.")).toBeTruthy();
  expect((screen.getByRole("button", { name: "전액 취소 반영" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "조사 사유 기록" }));
  expect((await screen.findAllByText("부분 취소 수량 확인 필요")).length).toBeGreaterThan(0);
  expect(screen.queryByText("해결 완료")).toBeNull();
});

test("failed cancellation displays the server reason and allows a fresh check", async () => {
  currentStatus = "PAID"; failApply = true;
  await openList();
  fireEvent.click(screen.getByRole("button", { name: "상세 보기 ORD-FIRST" }));
  fireEvent.change(await screen.findByLabelText("처리·조사 사유"), { target: { value: "취소 확인" } });
  fireEvent.click(screen.getByRole("button", { name: "PG 상태 재확인" }));
  await screen.findByText("취소 잔액");
  fireEvent.click(screen.getByRole("button", { name: "전액 취소 반영" }));
  fireEvent.click(screen.getByRole("button", { name: "취소 반영 확정" }));
  expect(await screen.findByRole("alert")).toHaveProperty("textContent", "토스 상태가 변경되어 취소를 반영할 수 없습니다.");
  expect(screen.queryByText("해결 완료")).toBeNull();
  expect((screen.getByRole("button", { name: "PG 상태 재확인" }) as HTMLButtonElement).disabled).toBe(false);
});

test("search trims the order ID, resets the page, and sends a Korean calendar date range", async () => {
  await openList();
  fireEvent.click(screen.getByRole("button", { name: "다음" }));
  await screen.findByText("ORD-MISSING");
  fireEvent.change(screen.getByLabelText("주문번호"), { target: { value: " ORD-FILTER " } });
  fireEvent.change(screen.getByLabelText("불일치 유형"), { target: { value: "PAYMENT_IDENTITY_MISMATCH" } });
  fireEvent.change(screen.getByLabelText("발견일 시작"), { target: { value: "2026-09-29" } });
  fireEvent.change(screen.getByLabelText("발견일 종료"), { target: { value: "2026-09-29" } });
  fireEvent.click(screen.getByRole("button", { name: "검색" }));
  expect(await screen.findByText("ORD-FILTER")).toBeTruthy();
  const request = calls.filter(call => call.url === "/admin/payment-reconciliation/issues").at(-1)!;
  expect(request.params).toEqual({ orderId: "ORD-FILTER", issueType: "PAYMENT_IDENTITY_MISMATCH",
    from: "2026-09-28T15:00:00.000Z", to: "2026-09-29T15:00:00.000Z", page: 0, size: 20 });
  expect(screen.queryByText("ORD-MISSING")).toBeNull();
});

test("missing local order and payment remain readable in detail", async () => {
  await openList();
  fireEvent.click(screen.getByRole("button", { name: "다음" }));
  await screen.findByText("ORD-MISSING");
  fireEvent.click(screen.getByRole("button", { name: "상세 보기 ORD-MISSING" }));
  expect(await screen.findByText("주문을 찾을 수 없습니다.")).toBeTruthy();
  expect(screen.getByText("결제 기록을 찾을 수 없습니다.")).toBeTruthy();
});

test("failed list can be refreshed without requesting the PG", async () => {
  failList = true;
  mount();
  fireEvent.click(screen.getByRole("button", { name: "결제 불일치" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  failList = false;
  fireEvent.click(screen.getByRole("button", { name: "새로고침" }));
  expect(await screen.findByText("ORD-FIRST")).toBeTruthy();
  expect(calls.every(call => call.url?.startsWith("/admin/"))).toBe(true);
});

test("an inverted date range is rejected before a new query", async () => {
  await openList();
  const before = calls.length;
  fireEvent.change(screen.getByLabelText("발견일 시작"), { target: { value: "2026-09-30" } });
  fireEvent.change(screen.getByLabelText("발견일 종료"), { target: { value: "2026-09-29" } });
  fireEvent.click(screen.getByRole("button", { name: "검색" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(calls.length).toBe(before);
});

test("non-admin cannot open the dashboard or request reconciliation data", async () => {
  auth.user.role = "USER";
  mount();
  expect(await screen.findByText("내 티켓")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "결제 불일치" })).toBeNull();
  expect(calls).toHaveLength(0);
});
