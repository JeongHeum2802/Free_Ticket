// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import SellingTickets from "./SellingTickets";
import TicketPriceChart from "../../components/TicketPriceChart";
import { getSellingTickets, getTicketPriceHistory, type SellingTicket } from "../../api/sellingTickets";

vi.mock("../../context/AuthContext", () => ({ useAuth: () => ({ user: { id: 1, eventDirector: true }, loading: false }) }));
vi.mock("../../api/sellingTickets", () => ({ getSellingTickets: vi.fn(), getTicketPriceHistory: vi.fn() }));

const ticket: SellingTicket = {
  id: 1, eventId: 101, eventName: "별빛 아래 우리", type: "프리미엄석", price: 150000,
  totalTicket: 100, soldTicket: 20, startTime: "2026-08-05T19:30:00", bookingEndtime: "2026-08-04T19:30:00",
};
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

test("목록에서 선택한 티켓의 실제 API 가격 이력을 차트로 표시한다", async () => {
  vi.mocked(getSellingTickets).mockResolvedValue([ticket]);
  vi.mocked(getTicketPriceHistory).mockResolvedValue({ ticket, history: [
    { id: 1, price: 120000, changedAt: "2026-07-06T19:30:00" },
    { id: 2, price: 150000, changedAt: "2026-08-04T19:30:00" },
  ] });
  render(<SellingTickets />);
  fireEvent.click(await screen.findByRole("button", { name: /프리미엄석/ }));
  expect(await screen.findByRole("img", { name: /티켓 가격 변동 차트/ })).toBeTruthy();
  expect(getTicketPriceHistory).toHaveBeenCalledWith(1, expect.any(AbortSignal));
  expect(screen.getByText("전체 변경 내역 (2건)")).toBeTruthy();
});

test("이력이 없는 티켓은 가상 차트를 만들지 않는다", async () => {
  vi.mocked(getSellingTickets).mockResolvedValue([ticket]);
  vi.mocked(getTicketPriceHistory).mockResolvedValue({ ticket, history: [] });
  render(<SellingTickets />);
  fireEvent.click(await screen.findByRole("button", { name: /프리미엄석/ }));
  expect(await screen.findByText("아직 기록된 가격 이력이 없습니다.")).toBeTruthy();
  expect(screen.queryByRole("img")).toBeNull();
});

test("가격 이력 요청 실패 후 재시도할 수 있다", async () => {
  vi.mocked(getSellingTickets).mockResolvedValue([ticket]);
  vi.mocked(getTicketPriceHistory).mockRejectedValueOnce(new Error("network"))
    .mockResolvedValueOnce({ ticket, history: [] });
  render(<SellingTickets />);
  fireEvent.click(await screen.findByRole("button", { name: /프리미엄석/ }));
  fireEvent.click(await screen.findByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("아직 기록된 가격 이력이 없습니다.")).toBeTruthy();
});

test("티켓을 빠르게 바꾸면 늦게 도착한 이전 응답을 표시하지 않는다", async () => {
  const second = { ...ticket, id: 2, type: "R석" };
  vi.mocked(getSellingTickets).mockResolvedValue([ticket, second]);
  let resolveFirst!: (value: Awaited<ReturnType<typeof getTicketPriceHistory>>) => void;
  vi.mocked(getTicketPriceHistory).mockImplementation((id) => id === 1
    ? new Promise((resolve) => { resolveFirst = resolve; })
    : Promise.resolve({ ticket: second, history: [] }));
  render(<SellingTickets />);
  fireEvent.click(await screen.findByRole("button", { name: /프리미엄석/ }));
  fireEvent.click(screen.getByRole("button", { name: /R석/ }));
  await screen.findByText("아직 기록된 가격 이력이 없습니다.");
  resolveFirst({ ticket, history: [{ id: 1, price: 123000, changedAt: "2026-07-06T19:30:00" }] });
  await waitFor(() => expect(screen.queryByRole("img")).toBeNull());
});

test("가격 기록이 한 건이고 0원이어도 유효한 점으로 표시한다", () => {
  render(<TicketPriceChart history={[{ id: 1, price: 0, changedAt: "2026-07-06T19:30:00" }]} />);
  expect(screen.getByRole("button", { name: /0원/ }).getAttribute("cx")).toBe("395");
  expect(screen.getByRole("button", { name: /0원/ }).getAttribute("cy")).toBe("235");
});
