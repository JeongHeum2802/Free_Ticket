// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import BuyerTicketPriceHistory from "./BuyerTicketPriceHistory";
import { getPublicTicketPriceHistory } from "../api/ticketPriceHistory";

vi.mock("../api/ticketPriceHistory", () => ({ getPublicTicketPriceHistory: vi.fn() }));
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

test("구매자용 API로 선택한 티켓의 차트와 가격 내역을 표시한다", async () => {
  vi.mocked(getPublicTicketPriceHistory).mockResolvedValue({ ticketId: 1, currentPrice: 150000,
    history: [{ id: 1, price: 120000, changedAt: "2026-07-06T19:30:00" }] });
  render(<BuyerTicketPriceHistory eventId={101} ticketId={1} ticketType="프리미엄석" />);
  expect(await screen.findByRole("img", { name: /티켓 가격 변동 차트/ })).toBeTruthy();
  expect(getPublicTicketPriceHistory).toHaveBeenCalledWith(101, 1, expect.any(AbortSignal));
  expect(screen.getByText("150,000원")).toBeTruthy();
});

test("티켓 전환 시 이전 이력을 비우고 늦게 도착한 응답을 무시한다", async () => {
  let resolveFirst!: (value: Awaited<ReturnType<typeof getPublicTicketPriceHistory>>) => void;
  vi.mocked(getPublicTicketPriceHistory).mockImplementation((_event, id) => id === 1
    ? new Promise((resolve) => { resolveFirst = resolve; })
    : Promise.resolve({ ticketId: 2, currentPrice: 110000, history: [] }));
  const { rerender } = render(<BuyerTicketPriceHistory eventId={101} ticketId={1} ticketType="VIP" />);
  rerender(<BuyerTicketPriceHistory eventId={101} ticketId={2} ticketType="R석" />);
  expect(await screen.findByText("110,000원")).toBeTruthy();
  resolveFirst({ ticketId: 1, currentPrice: 150000, history: [{ id: 1, price: 120000, changedAt: "2026-07-06T19:30:00" }] });
  await waitFor(() => expect(screen.queryByRole("img")).toBeNull());
  expect(screen.getByText("아직 기록된 가격 이력이 없습니다.")).toBeTruthy();
});

test("조회 실패 안내에서 재시도하면 가격 정보를 불러온다", async () => {
  vi.mocked(getPublicTicketPriceHistory).mockRejectedValueOnce(new Error("network"))
    .mockResolvedValueOnce({ ticketId: 1, currentPrice: 0, history: [] });
  render(<BuyerTicketPriceHistory eventId={101} ticketId={1} ticketType="A석" />);
  fireEvent.click(await screen.findByRole("button", { name: "가격 이력 다시 시도" }));
  expect(await screen.findByText("0원")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});
