// @vitest-environment jsdom
import { AxiosError, type AxiosAdapter } from "axios";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./AuthContext";
import ProtectedRoute from "../components/ProtectedRoute";
import Mypage from "../pages/mypage/Mypage";
import { getAccessToken, setAccessToken } from "../api/token";

const transport = vi.hoisted(() => ({ adapter: undefined as AxiosAdapter | undefined }));
vi.mock("axios", async (importOriginal) => {
  const actual = await importOriginal<typeof import("axios")>();
  return { ...actual, default: { ...actual.default, create: (config: object) =>
    actual.default.create({ ...config, adapter: (request) => transport.adapter!(request) }) } };
});

const user = { id: 1, username: "기존 사용자", email: "user@example.com", phonenumber: "01012345678", role: "USER", eventDirector: false };
function accessToken(sub: string, jti: string) {
  return `${btoa('{"alg":"HS256","typ":"JWT"}')}.${btoa(JSON.stringify({ sub, token_use: "access", jti }))}.signature`;
}
const RESTORED_TOKEN = accessToken("1", "restored"), LOGIN_TOKEN = accessToken("2", "login");
let failRefresh = false, failLogout = false;
let holdMe: Promise<void> | undefined;

beforeEach(() => {
  setAccessToken(null);
  failRefresh = false; failLogout = false; holdMe = undefined;
  let queue = Promise.resolve();
  vi.stubGlobal("navigator", { locks: { request: (_name: string, action: () => Promise<unknown>) => {
    const result = queue.then(action);
    queue = result.then(() => undefined, () => undefined);
    return result;
  } } });
  vi.spyOn(window, "alert").mockImplementation(() => undefined);
  transport.adapter = async (config) => {
    if (config.url === "/auth/refresh" && failRefresh) throw new Error("network unavailable");
    if (config.url === "/auth/logout" && failLogout) throw new Error("network unavailable");
    if (config.url === "/auth/me" && holdMe) await holdMe;
    const data = config.url === "/auth/me" ? { user }
      : config.url === "/auth/login" ? { message: "OK", accessToken: LOGIN_TOKEN, tokenType: "Bearer", expiresIn: 600, user: { ...user, id: 2, username: "새 사용자" } }
      : { accessToken: RESTORED_TOKEN, tokenType: "Bearer", expiresIn: 600 };
    return { config, data, status: 200, statusText: "OK", headers: {} };
  };
});

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); setAccessToken(null); });

function Probe() {
  const { user, loading, login, logout } = useAuth();
  return <>
    <p>{loading ? "복원 중" : user?.username ?? "비로그인"}</p>
    <button onClick={() => void login({ email: "new@example.com", password: "password123" })}>로그인</button>
    <button onClick={() => void logout().catch(() => undefined)}>로그아웃</button>
  </>;
}

function mount(protectedPage = false) {
  render(<MemoryRouter initialEntries={["/private"]}><AuthProvider><Routes>
    <Route path="/private" element={protectedPage ? <ProtectedRoute><p>보호 페이지</p></ProtectedRoute> : <Probe />} />
    <Route path="/login" element={<p>로그인 화면</p>} />
    <Route path="/" element={<p>홈 화면</p>} />
  </Routes></AuthProvider></MemoryRouter>);
}

test("a transient initial restore failure shows retry without claiming the user is logged out", async () => {
  failRefresh = true;
  mount(true);
  expect(await screen.findByRole("button", { name: "다시 시도" })).toBeTruthy();
  expect(screen.queryByText("로그인 화면")).toBeNull();
  failRefresh = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("보호 페이지")).toBeTruthy();
});

test("a browser without Web Locks shows an actionable restore error", async () => {
  vi.stubGlobal("navigator", {});
  mount(true);
  expect(await screen.findByRole("alert")).toHaveProperty("textContent", expect.stringMatching(/브라우저/));
  expect(screen.queryByText("로그인 화면")).toBeNull();
});

test("a late restored user cannot overwrite a newer login", async () => {
  let release!: () => void;
  holdMe = new Promise<void>((resolve) => { release = resolve; });
  mount();
  await waitFor(() => expect(getAccessToken()).toBe(RESTORED_TOKEN));
  fireEvent.click(screen.getByRole("button", { name: "로그인" }));
  await waitFor(() => expect(getAccessToken()).toBe(LOGIN_TOKEN));
  await act(async () => { release(); });
  expect(screen.getByText("새 사용자")).toBeTruthy();
  expect(getAccessToken()).toBe(LOGIN_TOKEN);
});

test("failed server logout retains the authenticated user and token", async () => {
  mount();
  expect(await screen.findByText("기존 사용자")).toBeTruthy();
  failLogout = true;
  fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));
  await act(async () => { await new Promise((resolve) => setTimeout(resolve, 0)); });
  expect(screen.getByText("기존 사용자")).toBeTruthy();
  expect(getAccessToken()).toBe(RESTORED_TOKEN);
});

test("the logout button reports server failure without showing completion or navigating away", async () => {
  render(<MemoryRouter initialEntries={["/mypage"]}><AuthProvider><Routes>
    <Route path="/mypage" element={<Mypage />} />
    <Route path="/" element={<p>홈 화면</p>} />
  </Routes></AuthProvider></MemoryRouter>);
  expect(await screen.findByText("기존 사용자님")).toBeTruthy();
  failLogout = true;
  fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(screen.queryByText("홈 화면")).toBeNull();
  expect(window.alert).not.toHaveBeenCalled();
});

test("an explicit missing refresh cookie resolves restoration as unauthenticated", async () => {
  transport.adapter = async (config) => {
    throw new AxiosError("missing", "ERR_BAD_RESPONSE", config, undefined,
      { config, data: { code: "REFRESH_TOKEN_NOT_FOUND" }, status: 401, statusText: "Unauthorized", headers: {} });
  };
  mount(true);
  expect(await screen.findByText("로그인 화면")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "다시 시도" })).toBeNull();
});
