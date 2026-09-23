// @vitest-environment jsdom

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import Mypage from "./Mypage";
import SellingTickets from "./SellingTickets";

const { auth } = vi.hoisted(() => ({
  auth: {
    user: { username: "사용자", role: "USER", eventDirector: false as boolean | undefined },
    loading: false,
    logout: vi.fn(),
  },
}));

vi.mock("../../context/AuthContext", () => ({ useAuth: () => auth }));
vi.mock("../../api/sellingTickets", () => ({
  getSellingTickets: vi.fn().mockResolvedValue([]),
  getTicketPriceHistory: vi.fn(),
}));

afterEach(cleanup);

function renderMypage(path = "/mypage/tickets") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/mypage" element={<Mypage />}>
          <Route path="tickets" element={<div>예매 내역</div>} />
          <Route path="selling" element={<SellingTickets />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("행사 담당자 판매 메뉴", () => {
  test.each(["USER", "DIRECTOR", "ADMIN"])("%s 권한이라도 등록되지 않았으면 메뉴와 직접 접근을 차단한다", (role) => {
    auth.user = { username: "사용자", role, eventDirector: false };
    renderMypage("/mypage/selling");

    expect(screen.queryByRole("link", { name: "내가 판매 중인 티켓" })).toBeNull();
    expect(screen.queryByRole("heading", { name: "내가 판매 중인 티켓" })).toBeNull();
    expect(screen.getByText("예매 내역")).toBeTruthy();
  });

  test("일반 권한이어도 등록되어 있으면 판매 메뉴와 판매 페이지에 접근할 수 있다", async () => {
    auth.user = { username: "사용자", role: "USER", eventDirector: true };
    renderMypage("/mypage/selling");

    expect(screen.getByRole("link", { name: "내가 판매 중인 티켓" })).toBeTruthy();
    expect(screen.getByRole("link", { name: "내가 판매 중인 티켓" }).getAttribute("aria-current")).toBe("page");
    expect(await screen.findByText("담당 행사에 등록된 티켓이 없습니다.")).toBeTruthy();
  });

  test("등록 여부가 없는 응답에서는 판매 메뉴를 숨긴다", () => {
    auth.user = { username: "사용자", role: "DIRECTOR", eventDirector: undefined };
    renderMypage();

    expect(screen.queryByRole("link", { name: "내가 판매 중인 티켓" })).toBeNull();
  });
});
