// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test } from "vitest";
import type { AxiosAdapter } from "axios";
import { api } from "../api/axios";
import type { SellingTicket } from "../api/sellingTickets";
import TicketPriceSettings from "./TicketPriceSettings";

const ticket: SellingTicket = {
  id: 1, eventId: 101, eventName: "별빛 아래 우리", type: "프리미엄석", price: 120000,
  totalTicket: 100, soldTicket: 20, startTime: "2026-08-05T19:30:00", bookingEndtime: "2026-08-04T19:30:00",
  initialPrice: 150000, minPrice: 50000, salesStartAt: "2026-07-01T09:00:00", automaticPricingEnabled: true,
  lastPriceEvaluatedAt: "2026-07-01T11:00:00",
};
const originalAdapter = api.defaults.adapter;
let requests: { url?: string; method?: string; data: unknown }[];
let failSave: boolean;
let saved: SellingTicket | null;
let currentPrice: number;

beforeEach(() => {
  requests = []; failSave = false; saved = null; currentPrice = ticket.price!;
  const adapter: AxiosAdapter = async (config) => {
    const settings = JSON.parse(config.data);
    requests.push({ url: config.url, method: config.method, data: settings });
    if (failSave) throw new Error("network");
    return { data: { data: { ...ticket, ...settings,
      price: settings.automaticPricingEnabled ? ticket.price : settings.price ?? currentPrice } },
      status: 200, statusText: "OK", headers: {}, config };
  };
  api.defaults.adapter = adapter;
});
afterEach(() => { cleanup(); api.defaults.adapter = originalAdapter; });

function mount(value = ticket) {
  return render(<TicketPriceSettings ticket={value} onSaved={(updated) => { saved = updated; }} />);
}

test("저장된 자동 모드와 초기 가격을 표시하고 초기 가격 변경을 막는다", () => {
  mount();
  expect(screen.getByRole("checkbox")).toHaveProperty("checked", true);
  expect(screen.getByLabelText("초기 가격 (P0)")).toHaveProperty("value", "150000");
  expect(screen.getByLabelText("초기 가격 (P0)")).toHaveProperty("readOnly", true);
  expect(screen.getByLabelText("최소 가격 (MIN)")).toHaveProperty("value", "50000");
  expect(screen.getByLabelText("가격 수정")).toHaveProperty("disabled", true);
});

test("수동 가격을 저장하면 서버 응답과 성공 안내를 전달한다", async () => {
  mount();
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.change(screen.getByLabelText("가격 수정"), { target: { value: "110000" } });
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  expect(await screen.findByRole("status")).toHaveProperty("textContent", "가격 설정을 저장했습니다.");
  expect(requests).toEqual([{ url: "/tickets/1/price-settings", method: "put", data: {
    initialPrice: 150000, minPrice: 50000, automaticPricingEnabled: false, price: 110000,
  } }]);
  expect(saved).toMatchObject({ id: 1, price: 110000, automaticPricingEnabled: false });
  expect(screen.getByRole("checkbox")).toHaveProperty("checked", false);
});

test("자동 모드를 끌 때 수동 가격을 편집하지 않았다면 서버의 최신 가격을 유지한다", async () => {
  currentPrice = 45000;
  mount({ ...ticket, price: 50000, minPrice: 10000 });
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await screen.findByRole("status");
  expect(requests[0]?.data).toEqual({ initialPrice: 150000, minPrice: 10000,
    automaticPricingEnabled: false, price: null });
  expect(saved).toMatchObject({ price: 45000, automaticPricingEnabled: false });
  expect(screen.getByLabelText("가격 수정")).toHaveProperty("value", "45000");
});

test("수동 가격을 저장한 후 추가 편집 없이 다시 저장하면 최신 서버 가격을 유지한다", async () => {
  mount({ ...ticket, automaticPricingEnabled: false });
  fireEvent.change(screen.getByLabelText("가격 수정"), { target: { value: "110000" } });
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await screen.findByRole("status");
  currentPrice = 100000;
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await waitFor(() => expect(screen.getByLabelText("가격 수정")).toHaveProperty("value", "100000"));
  expect(requests[1]?.data).toMatchObject({ automaticPricingEnabled: false, price: null });
});

test("새 자동 설정의 초기 가격과 최저 가격을 저장한다", async () => {
  mount({ ...ticket, initialPrice: null, minPrice: null, automaticPricingEnabled: false, salesStartAt: null,
    lastPriceEvaluatedAt: null });
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.change(screen.getByLabelText("초기 가격 (P0)"), { target: { value: "150000" } });
  fireEvent.change(screen.getByLabelText("최소 가격 (MIN)"), { target: { value: "50000" } });
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await screen.findByRole("status");
  expect(requests[0]?.data).toEqual({ initialPrice: 150000, minPrice: 50000, automaticPricingEnabled: true, price: null });
  expect(screen.getByLabelText("초기 가격 (P0)")).toHaveProperty("readOnly", true);
});

test.each(["", "0", "-1", "1.5", "2147483648"])("유효하지 않은 수동 가격 %s는 저장하지 않는다", async (price) => {
  mount({ ...ticket, automaticPricingEnabled: false });
  fireEvent.change(screen.getByLabelText("가격 수정"), { target: { value: price } });
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(requests).toHaveLength(0);
  expect(saved).toBeNull();
});

test.each([
  { initialPrice: "0", minPrice: "0" },
  { initialPrice: "150000", minPrice: "0" },
])("0원인 자동 가격 설정은 저장하지 않는다: $initialPrice / $minPrice", async (values) => {
  mount({ ...ticket, initialPrice: null, minPrice: null, salesStartAt: null, automaticPricingEnabled: false,
    lastPriceEvaluatedAt: null });
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.change(screen.getByLabelText("초기 가격 (P0)"), { target: { value: values.initialPrice } });
  fireEvent.change(screen.getByLabelText("최소 가격 (MIN)"), { target: { value: values.minPrice } });
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(requests).toHaveLength(0);
  expect(saved).toBeNull();
});

test("다시 자동 모드를 켤 때 현재 가격보다 높은 최저 가격을 거부한다", async () => {
  mount({ ...ticket, automaticPricingEnabled: false });
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.change(screen.getByLabelText("최소 가격 (MIN)"), { target: { value: "130000" } });
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(requests).toHaveLength(0);
});

test("초기 가격이 저장되었으면 판매 시작 기록이 없어도 자동 가격 범위를 검증한다", async () => {
  mount({ ...ticket, price: 160000, salesStartAt: null, automaticPricingEnabled: false });
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(requests).toHaveLength(0);
});

test("저장 실패를 안내하고 편집한 설정을 유지하여 재시도한다", async () => {
  failSave = true;
  mount();
  fireEvent.change(screen.getByLabelText("최소 가격 (MIN)"), { target: { value: "60000" } });
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  expect(await screen.findByRole("alert")).toHaveProperty("textContent", "가격 설정을 저장하지 못했습니다. 다시 시도해 주세요.");
  expect(screen.getByLabelText("최소 가격 (MIN)")).toHaveProperty("value", "60000");
  expect(saved).toBeNull();
  failSave = false;
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await screen.findByRole("status");
  expect(saved).toMatchObject({ minPrice: 60000 });
});

test("다른 티켓으로 이동하면 늦게 완료된 저장의 콜백을 실행하지 않는다", async () => {
  let finish!: () => void;
  api.defaults.adapter = (config) => new Promise((resolve) => {
    finish = () => resolve({ data: { data: ticket }, status: 200, statusText: "OK", headers: {}, config });
  });
  const view = mount();
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await waitFor(() => expect(finish).toBeTypeOf("function"));
  view.unmount();
  finish();
  await new Promise((resolve) => setTimeout(resolve, 0));
  expect(saved).toBeNull();
});
