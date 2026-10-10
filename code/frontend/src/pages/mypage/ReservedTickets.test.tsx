// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { AxiosError, type AxiosAdapter } from "axios";
import { api } from "../../api/axios";
import type { ReservationHistory } from "../../types/Payment";
import ReservedTickets from "./ReservedTickets";

const reservation: ReservationHistory = {
  orderId: "ORD/CUSTOMER 1", eventId: 1, eventName: "별빛 아래 우리",
  mainImageUrl: "/poster.png", location: "서울 공연장", ticketType: "R석",
  performanceAt: "2026-10-20T19:30:00", quantity: 2, amount: 100000,
  paidAt: "2026-10-09T10:00:00", paymentMethod: "카드", receiptUrl: null, status: "PAID",
};
const originalAdapter = api.defaults.adapter;
let currentReservations: ReservationHistory[];
let listFails = false;
let cancel: AxiosAdapter;
const calls: { url?: string; method?: string; data?: unknown }[] = [];

beforeEach(() => {
  currentReservations = [reservation];
  listFails = false;
  calls.length = 0;
  Object.defineProperties(HTMLDialogElement.prototype, {
    showModal: { configurable: true, value(this: HTMLDialogElement) { this.setAttribute("open", ""); } },
    close: { configurable: true, value(this: HTMLDialogElement) { this.removeAttribute("open"); } },
  });
  cancel = async config => {
    currentReservations = [];
    return { status: 200, statusText: "OK", headers: {}, config,
      data: { message: "취소 완료", data: { orderId: reservation.orderId, paymentKey: "payment-key",
        amount: 100000, method: "카드", status: "CANCELED", approvedAt: "2026-10-09T10:00:00" } } };
  };
  api.defaults.adapter = async config => {
    calls.push({ url: config.url, method: config.method, data: config.data });
    if (config.method === "get" && config.url === "/orders/me/reservations") {
      if (listFails) throw new Error("network unavailable");
      return { status: 200, statusText: "OK", headers: {}, config,
        data: { message: "예매 내역", data: { reservations: currentReservations } } };
    }
    if (config.method === "post" && config.url === "/payments/ORD%2FCUSTOMER%201/cancel") {
      return cancel(config);
    }
    throw new Error(`Unexpected request: ${config.method} ${config.url}`);
  };
});

afterEach(() => {
  cleanup();
  api.defaults.adapter = originalAdapter;
  vi.useRealTimers();
});

async function openConfirmation() {
  render(<MemoryRouter><ReservedTickets /></MemoryRouter>);
  const trigger = await screen.findByRole("button", { name: "예매 취소" });
  trigger.focus();
  fireEvent.click(trigger);
  return within(await screen.findByRole("dialog", { name: "예매 취소 확인" }));
}

test("확인 팝업에 선택한 공연과 전액 취소 정보를 표시하고 돌아가면 요청하지 않는다", async () => {
  const dialog = await openConfirmation();
  expect(dialog.getByText("별빛 아래 우리")).toBeTruthy();
  expect(dialog.getByText(/2026년 10월 20일.*07:30/)).toBeTruthy();
  expect(dialog.getByText("2매")).toBeTruthy();
  expect(dialog.getByText("100,000원")).toBeTruthy();
  expect(dialog.getByText(/되돌릴 수 없습니다/)).toBeTruthy();
  expect(document.activeElement).toBe(dialog.getByRole("button", { name: "아니요, 돌아가기" }));
  fireEvent.click(dialog.getByRole("button", { name: "아니요, 돌아가기" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "예매 취소" }));
  expect(calls.filter(call => call.method === "post")).toHaveLength(0);
});

test("명시적으로 동의한 뒤에만 취소 요청하고 완료된 예매를 목록에서 제거한다", async () => {
  const dialog = await openConfirmation();
  expect(calls.filter(call => call.method === "post")).toHaveLength(0);
  fireEvent.click(dialog.getByRole("button", { name: "예, 취소합니다" }));
  expect(await screen.findByRole("status")).toHaveProperty("textContent", expect.stringContaining("취소되었습니다"));
  expect(screen.queryByRole("heading", { name: "별빛 아래 우리" })).toBeNull();
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(calls.filter(call => call.method === "post")).toEqual([
    { url: "/payments/ORD%2FCUSTOMER%201/cancel", method: "post", data: undefined },
  ]);
});

test("취소 요청 중에는 중복 클릭과 팝업 닫기를 막는다", async () => {
  let complete!: () => void;
  const originalCancel = cancel;
  cancel = async config => {
    await new Promise<void>(resolve => { complete = resolve; });
    return originalCancel(config);
  };
  const dialog = await openConfirmation();
  const confirm = dialog.getByRole("button", { name: "예, 취소합니다" });
  fireEvent.click(confirm);
  fireEvent.click(confirm);
  expect((confirm as HTMLButtonElement).disabled).toBe(true);
  expect((dialog.getByRole("button", { name: "아니요, 돌아가기" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));
  expect(screen.getByRole("dialog")).toBeTruthy();
  await waitFor(() => expect(calls.filter(call => call.method === "post")).toHaveLength(1));
  complete();
  await screen.findByRole("status");
});

test("서버가 취소를 거절하면 사유와 예매를 유지하고 성공으로 표시하지 않는다", async () => {
  cancel = async config => {
    throw new AxiosError("blocked", "ERR_BAD_REQUEST", config, undefined, {
      status: 400, statusText: "Bad Request", headers: {}, config,
      data: { code: "CANCELLATION_NOT_ALLOWED", message: "공연이 시작되어 취소할 수 없습니다." },
    });
  };
  const dialog = await openConfirmation();
  fireEvent.click(dialog.getByRole("button", { name: "예, 취소합니다" }));
  expect(await screen.findByRole("alert")).toHaveProperty("textContent", expect.stringContaining("공연이 시작되어 취소할 수 없습니다."));
  expect(screen.getByRole("heading", { name: "별빛 아래 우리" })).toBeTruthy();
  expect(screen.queryByRole("status")).toBeNull();
});

test("응답을 잃으면 상태를 다시 조회해 처리 중인 예매의 재취소를 막는다", async () => {
  cancel = async () => {
    currentReservations = [{ ...reservation, status: "CANCELING" }];
    throw new Error("timeout");
  };
  const dialog = await openConfirmation();
  fireEvent.click(dialog.getByRole("button", { name: "예, 취소합니다" }));
  expect(await screen.findByRole("alert")).toHaveProperty("textContent", expect.stringContaining("취소 결과를 확인하지 못했습니다"));
  await waitFor(() => expect((screen.getByRole("button", { name: "예매 취소" }) as HTMLButtonElement).disabled).toBe(true));
  expect(screen.getByText("취소 처리 중")).toBeTruthy();
  expect(calls.filter(call => call.method === "get")).toHaveLength(2);
  currentReservations = [];
  fireEvent.click(screen.getByRole("button", { name: "목록 새로고침" }));
  await waitFor(() => expect(screen.queryByRole("heading", { name: "별빛 아래 우리" })).toBeNull());
});

test("결과 확인 조회도 실패하면 재취소를 막고 새로고침으로 회복한다", async () => {
  cancel = async () => { listFails = true; throw new Error("timeout"); };
  const dialog = await openConfirmation();
  fireEvent.click(dialog.getByRole("button", { name: "예, 취소합니다" }));
  await screen.findByRole("alert");
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  expect((screen.getByRole("button", { name: "예매 취소" }) as HTMLButtonElement).disabled).toBe(true);
  expect(screen.queryByRole("status")).toBeNull();
  listFails = false;
  currentReservations = [];
  fireEvent.click(screen.getByRole("button", { name: "목록 새로고침" }));
  await waitFor(() => expect(screen.queryByRole("heading", { name: "별빛 아래 우리" })).toBeNull());
});

test("처리 중인 예매는 처음 조회할 때도 취소 버튼을 비활성화한다", async () => {
  currentReservations = [{ ...reservation, status: "CANCELING" }];
  render(<MemoryRouter><ReservedTickets /></MemoryRouter>);
  expect((await screen.findByRole("button", { name: "예매 취소" }) as HTMLButtonElement).disabled).toBe(true);
  expect(screen.getByText("취소 처리 중")).toBeTruthy();
  expect(screen.getByText(/처리가 끝난 뒤 목록을 새로고침/)).toBeTruthy();
});

test("취소와 결과 조회가 모두 멈춰도 제한 시간 뒤 팝업을 닫고 새로고침할 수 있다", async () => {
  const dialog = await openConfirmation();
  vi.useFakeTimers();
  api.defaults.adapter = config => new Promise((_, reject) => {
    if (config.timeout && config.timeout > 0) {
      setTimeout(() => reject(new AxiosError("timeout", "ECONNABORTED", config)), config.timeout);
    }
  });

  fireEvent.click(dialog.getByRole("button", { name: "예, 취소합니다" }));
  await act(async () => { await vi.advanceTimersByTimeAsync(89_999); });
  expect(screen.getByRole("dialog")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();

  await act(async () => { await vi.advanceTimersByTimeAsync(1); });
  expect(screen.getByRole("alert").textContent).toContain("취소 결과를 확인하지 못했습니다");
  await act(async () => { await vi.advanceTimersByTimeAsync(9_999); });
  expect(screen.getByRole("dialog")).toBeTruthy();

  await act(async () => { await vi.advanceTimersByTimeAsync(1); });
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.getByRole("alert").textContent).toContain("예매 내역도 새로고침하지 못했습니다");
  expect((screen.getByRole("button", { name: "목록 새로고침" }) as HTMLButtonElement).disabled).toBe(false);
  expect((screen.getByRole("button", { name: "예매 취소" }) as HTMLButtonElement).disabled).toBe(true);
  expect(screen.queryByRole("status")).toBeNull();
});
