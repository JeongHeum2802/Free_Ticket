// @vitest-environment jsdom

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, test, vi } from "vitest";

import { getEvents, getWhatsHot } from "../../api/events";
import EventPageLayout from "./EventPageLayout";

vi.mock("../../api/events", () => ({
  getEvents: vi.fn(),
  getWhatsHot: vi.fn(),
}));

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(getEvents).mockResolvedValue([]);
  vi.mocked(getWhatsHot).mockResolvedValue([{
    rank: 1, id: 2, name: "early", startDate: "2026-09-01", endDate: "2026-10-01",
    location: "Seoul", bannerImageUrl: "banner", mainImageUrl: "poster", category: "musical",
  }]);
});
afterEach(cleanup);

test("주간순위는 HOT 결과로 표시하고 주간순위 API를 다시 호출하지 않는다", async () => {
  render(<MemoryRouter><EventPageLayout category="musical" categoryName="뮤지컬" /></MemoryRouter>);

  expect(await screen.findByText("이번 주 1위")).toBeTruthy();
  expect(getWhatsHot).toHaveBeenCalledWith({ category: "musical", limit: 5 });
  expect(getWhatsHot).toHaveBeenCalledTimes(1);
});
