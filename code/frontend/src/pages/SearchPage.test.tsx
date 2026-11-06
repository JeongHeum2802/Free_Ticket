// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import App from "../App";
import { api } from "../api/axios";

vi.mock("../context/AuthContext", () => ({ useAuth: () => ({ user: null, loading: false }) }));

const event = {
  id: 401, name: "여름밤의 멜로디", startDate: "2026-10-10", endDate: "2026-10-11",
  location: "전라남도 여수시 박람회길 1", mainImageUrl: "/poster.jpg", category: "concert",
};

function BackButton() {
  const navigate = useNavigate();
  return <button onClick={() => navigate(-1)}>이전 검색</button>;
}

function openSearch(path = "/search") {
  render(<MemoryRouter initialEntries={[path]}><App /><BackButton /></MemoryRouter>);
}

beforeEach(() => {
  vi.spyOn(api, "get").mockResolvedValue({ data: { message: "OK", data: { events: [event], page: 0, size: 30, hasNext: false } } });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

test("돋보기로 검색에 접근하고 제목과 두 필터를 함께 전송한다", async () => {
  openSearch();
  expect(screen.getByRole("link", { name: "공연 검색" }).getAttribute("href")).toBe("/search");
  await screen.findByRole("heading", { name: "여름밤의 멜로디" });
  fireEvent.change(screen.getByLabelText("공연 제목"), { target: { value: "  여름밤  " } });
  fireEvent.change(screen.getByLabelText("지역"), { target: { value: "여수시" } });
  fireEvent.change(screen.getByLabelText("카테고리"), { target: { value: "concert" } });
  fireEvent.click(screen.getByRole("button", { name: "검색" }));
  await waitFor(() => expect(api.get).toHaveBeenLastCalledWith("/events", {
    params: { category: "concert", title: "여름밤", region: "여수시", page: 0, size: 30, sort: "latest" },
  }));
  expect((screen.getByLabelText("공연 제목") as HTMLInputElement).value).toBe("여름밤");
  expect((await screen.findByRole("link", { name: /여름밤의 멜로디 포스터/ })).getAttribute("href")).toBe("/ticket/401");
});

test("주소의 검색 조건을 복원하고 초기화 후 뒤로가기로 되돌린다", async () => {
  openSearch("/search?title=여름밤&region=여수시&category=concert");
  await screen.findByRole("heading", { name: "여름밤의 멜로디" });
  expect((screen.getByLabelText("지역") as HTMLSelectElement).value).toBe("여수시");
  fireEvent.click(screen.getByRole("button", { name: "초기화" }));
  await waitFor(() => expect(api.get).toHaveBeenLastCalledWith("/events", { params: { page: 0, size: 30, sort: "latest" } }));
  expect((screen.getByLabelText("공연 제목") as HTMLInputElement).value).toBe("");
  fireEvent.click(screen.getByRole("button", { name: "이전 검색" }));
  await waitFor(() => expect((screen.getByLabelText("공연 제목") as HTMLInputElement).value).toBe("여름밤"));
  expect((screen.getByLabelText("카테고리") as HTMLSelectElement).value).toBe("concert");
});

test("빈 결과와 요청 실패를 구분하고 같은 조건으로 재검색할 수 있다", async () => {
  vi.mocked(api.get).mockRejectedValueOnce(new Error("offline"));
  openSearch();
  expect(await screen.findByRole("alert")).toBeTruthy();
  vi.mocked(api.get).mockResolvedValueOnce({ data: { message: "OK", data: { events: [], page: 0, size: 30, hasNext: false } } });
  fireEvent.click(screen.getByRole("button", { name: "검색" }));
  expect(await screen.findByText("검색 조건에 맞는 공연이 없습니다.")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});

test("늦게 도착한 이전 검색 결과가 새로운 결과를 덮어쓰지 않는다", async () => {
  let resolveFirst!: (value: unknown) => void;
  vi.mocked(api.get).mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }));
  openSearch();
  await waitFor(() => expect(api.get).toHaveBeenCalledTimes(1));
  fireEvent.change(screen.getByLabelText("공연 제목"), { target: { value: "여름밤" } });
  fireEvent.click(screen.getByRole("button", { name: "검색" }));
  await screen.findByRole("heading", { name: "여름밤의 멜로디" });
  await act(async () => resolveFirst({ data: { data: { events: [{ ...event, id: 402, name: "이전 공연" }], page: 0, size: 30, hasNext: false } } }));
  expect(screen.queryByRole("heading", { name: "이전 공연" })).toBeNull();
  expect(screen.getByRole("heading", { name: "여름밤의 멜로디" })).toBeTruthy();
});

test("검색 결과를 이어 불러오고 마지막 페이지에서는 더보기를 숨긴다", async () => {
  let resolveNext!: (value: unknown) => void;
  vi.mocked(api.get)
    .mockResolvedValueOnce({ data: { data: { events: [event], page: 0, size: 30, hasNext: true } } })
    .mockImplementationOnce(() => new Promise((resolve) => { resolveNext = resolve; }));
  openSearch("/search?title=여름밤&region=여수시&category=concert");
  expect(await screen.findByRole("heading", { name: "불러온 검색 결과 1건" })).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "더보기 +" }));
  expect((screen.getByRole("button", { name: /불러오는 중/ }) as HTMLButtonElement).disabled).toBe(true);
  expect(screen.getByRole("heading", { name: "여름밤의 멜로디" })).toBeTruthy();
  await waitFor(() => expect(api.get).toHaveBeenLastCalledWith("/events", {
    params: { title: "여름밤", region: "여수시", category: "concert", page: 1, size: 30, sort: "latest" },
  }));
  await act(async () => resolveNext({ data: { data: { events: [{ ...event, id: 402, name: "다음 공연" }], page: 1, size: 30, hasNext: false } } }));
  expect(await screen.findByRole("heading", { name: "불러온 검색 결과 2건" })).toBeTruthy();
  expect(screen.getByRole("heading", { name: "여름밤의 멜로디" })).toBeTruthy();
  expect(screen.getByRole("heading", { name: "다음 공연" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "더보기 +" })).toBeNull();
});

test("추가 검색 실패 후 기존 결과를 보존하고 같은 페이지를 재시도한다", async () => {
  vi.mocked(api.get)
    .mockResolvedValueOnce({ data: { data: { events: [event], page: 0, size: 30, hasNext: true } } })
    .mockRejectedValueOnce(new Error("offline"))
    .mockResolvedValueOnce({ data: { data: { events: [{ ...event, id: 402, name: "재시도 공연" }], page: 1, size: 30, hasNext: false } } });
  openSearch();
  fireEvent.click(await screen.findByRole("button", { name: "더보기 +" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(screen.getByRole("heading", { name: "여름밤의 멜로디" })).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "더보기 +" }));
  expect(await screen.findByRole("heading", { name: "재시도 공연" })).toBeTruthy();
  expect(screen.getByRole("heading", { name: "불러온 검색 결과 2건" })).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
  expect(vi.mocked(api.get).mock.calls.slice(1).map(([, options]) => options?.params.page)).toEqual([1, 1]);
});

test("검색 변경 시 페이지를 초기화하고 이전 추가 검색 응답을 무시한다", async () => {
  let resolveNext!: (value: unknown) => void;
  vi.mocked(api.get)
    .mockResolvedValueOnce({ data: { data: { events: [event], page: 0, size: 30, hasNext: true } } })
    .mockImplementationOnce(() => new Promise((resolve) => { resolveNext = resolve; }))
    .mockResolvedValueOnce({ data: { data: { events: [{ ...event, id: 403, name: "새 검색 공연" }], page: 0, size: 30, hasNext: false } } });
  openSearch();
  fireEvent.click(await screen.findByRole("button", { name: "더보기 +" }));
  await waitFor(() => expect(api.get).toHaveBeenCalledTimes(2));
  fireEvent.change(screen.getByLabelText("공연 제목"), { target: { value: "새 검색" } });
  fireEvent.click(screen.getByRole("button", { name: "검색" }));
  expect(await screen.findByRole("heading", { name: "새 검색 공연" })).toBeTruthy();
  await act(async () => resolveNext({ data: { data: { events: [{ ...event, id: 402, name: "이전 추가 공연" }], page: 1, size: 30, hasNext: true } } }));
  expect(screen.queryByRole("heading", { name: "여름밤의 멜로디" })).toBeNull();
  expect(screen.queryByRole("heading", { name: "이전 추가 공연" })).toBeNull();
  expect(screen.getByRole("heading", { name: "불러온 검색 결과 1건" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "더보기 +" })).toBeNull();
  expect(api.get).toHaveBeenLastCalledWith("/events", { params: { title: "새 검색", page: 0, size: 30, sort: "latest" } });
});
