// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import Loginpage from "./Loginpage";
import { api } from "../api/axios";

vi.mock("../api/axios", () => ({ api: { post: vi.fn() } }));
vi.mock("../context/AuthContext", () => ({ useAuth: () => ({ login: vi.fn() }) }));
vi.mock("react-router-dom", () => ({ useNavigate: () => vi.fn() }));

beforeEach(() => {
  vi.mocked(api.post).mockReset();
  vi.mocked(api.post).mockResolvedValue({ data: { verificationToken: "proof", expiresIn: 300, message: "인증번호를 보냈습니다." } });
});
afterEach(cleanup);

function open() {
  render(<Loginpage />);
  fireEvent.click(screen.getByRole("button", { name: "비밀번호 찾기" }));
}

async function authenticate() {
  open();
  fireEvent.change(screen.getByLabelText("가입 이메일"), { target: { value: "member@example.com" } });
  fireEvent.click(screen.getByRole("button", { name: "인증번호 보내기" }));
  fireEvent.change(await screen.findByLabelText("이메일 인증번호"), { target: { value: "123456" } });
  fireEvent.click(screen.getByRole("button", { name: "인증 확인" }));
  await screen.findByLabelText("새 비밀번호");
}

test("login recovery authenticates email before showing passwords and completes with matching values", async () => {
  await authenticate();
  expect(api.post).toHaveBeenCalledWith("/auth/password-resets/email/verify", {
    email: "member@example.com", verificationToken: "proof", code: "123456",
  });
  fireEvent.change(screen.getByLabelText("새 비밀번호"), { target: { value: "New-password123!" } });
  fireEvent.change(screen.getByLabelText("비밀번호 확인"), { target: { value: "New-password123!" } });
  fireEvent.click(screen.getByRole("button", { name: "비밀번호 변경" }));
  expect(await screen.findByText("비밀번호를 변경했습니다")).toBeTruthy();
  expect(api.post).toHaveBeenLastCalledWith("/auth/password-resets", {
    email: "member@example.com", verificationToken: "proof", newPassword: "New-password123!", confirmPassword: "New-password123!",
  }, { timeout: 10_000 });
  fireEvent.click(screen.getByRole("button", { name: "로그인으로 돌아가기" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("invalid passwords are explained without sending a reset request", async () => {
  await authenticate();
  for (const password of ["short", "has space123", "has\u001ccontrol123", "a".repeat(73), "가".repeat(25)]) {
    fireEvent.change(screen.getByLabelText("새 비밀번호"), { target: { value: password } });
    fireEvent.change(screen.getByLabelText("비밀번호 확인"), { target: { value: password } });
    fireEvent.submit(screen.getByLabelText("새 비밀번호").closest("form")!);
    expect(within(screen.getByRole("dialog")).getByRole("alert").textContent).toBeTruthy();
  }
  fireEvent.change(screen.getByLabelText("새 비밀번호"), { target: { value: "New-password123!" } });
  fireEvent.change(screen.getByLabelText("비밀번호 확인"), { target: { value: "different" } });
  fireEvent.submit(screen.getByLabelText("새 비밀번호").closest("form")!);
  expect(screen.getByRole("alert").textContent).toContain("일치");
  expect(api.post).toHaveBeenCalledTimes(2);
});

test("failed code stays in the email step and expired proof allows restarting", async () => {
  open();
  fireEvent.change(screen.getByLabelText("가입 이메일"), { target: { value: "member@example.com" } });
  fireEvent.click(screen.getByRole("button", { name: "인증번호 보내기" }));
  fireEvent.change(await screen.findByLabelText("이메일 인증번호"), { target: { value: "123456" } });
  vi.mocked(api.post).mockRejectedValueOnce({ isAxiosError: true, response: { data: { message: "인증번호를 확인해 주세요." } } });
  fireEvent.click(screen.getByRole("button", { name: "인증 확인" }));
  expect(await screen.findByText("인증번호를 확인해 주세요.")).toBeTruthy();
  expect(screen.queryByLabelText("새 비밀번호")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "인증 확인" }));
  fireEvent.change(await screen.findByLabelText("새 비밀번호"), { target: { value: "New-password123!" } });
  fireEvent.change(screen.getByLabelText("비밀번호 확인"), { target: { value: "New-password123!" } });
  vi.mocked(api.post).mockRejectedValueOnce({ isAxiosError: true, response: { data: { code: "INVALID_EMAIL_VERIFICATION", message: "이메일 인증이 만료되었습니다." } } });
  fireEvent.click(screen.getByRole("button", { name: "비밀번호 변경" }));
  expect(await screen.findByText("이메일 인증이 만료되었습니다.")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "이메일 다시 인증하기" }));
  expect(screen.queryByLabelText("새 비밀번호")).toBeNull();
  expect(screen.getByLabelText("가입 이메일")).toBeTruthy();
  expect(document.activeElement).toBe(screen.getByLabelText("가입 이메일"));
});

test("a timed-out password save releases the dialog and explains how to recover", async () => {
  await authenticate();
  fireEvent.change(screen.getByLabelText("새 비밀번호"), { target: { value: "New-password123!" } });
  fireEvent.change(screen.getByLabelText("비밀번호 확인"), { target: { value: "New-password123!" } });
  vi.mocked(api.post).mockRejectedValueOnce({ isAxiosError: true, code: "ECONNABORTED" });
  fireEvent.click(screen.getByRole("button", { name: "비밀번호 변경" }));
  expect(await screen.findByText(/새 비밀번호로 로그인해 보거나/)).toBeTruthy();
  expect((screen.getByRole("button", { name: "비밀번호 찾기 닫기" }) as HTMLButtonElement).disabled).toBe(false);
});

test("changing the email discards the old challenge and Escape returns focus to the opener", async () => {
  open();
  fireEvent.change(screen.getByLabelText("가입 이메일"), { target: { value: "first@example.com" } });
  fireEvent.click(screen.getByRole("button", { name: "인증번호 보내기" }));
  await screen.findByLabelText("이메일 인증번호");
  fireEvent.change(screen.getByLabelText("가입 이메일"), { target: { value: "second@example.com" } });
  expect(screen.queryByLabelText("이메일 인증번호")).toBeNull();
  fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "비밀번호 찾기" }));
});
