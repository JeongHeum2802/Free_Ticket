// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import ProfileModify from "./ProfileModify";
import { updateMyInfoApi } from "../../api/auth";
import { sendEmailVerification, verifyEmail } from "../../api/emailVerification";

const { account, setUser } = vi.hoisted(() => ({
  account: { id: 1, username: "회원", email: "old@example.com", phonenumber: "01012345678", role: "USER" as const, eventDirector: false },
  setUser: vi.fn(),
}));
vi.mock("../../context/AuthContext", () => ({ useAuth: () => ({ user: account, setUser }) }));
vi.mock("react-router-dom", () => ({ useNavigate: () => vi.fn() }));
vi.mock("../../api/auth", () => ({ updateMyInfoApi: vi.fn() }));
vi.mock("../../api/emailVerification", () => ({ sendEmailVerification: vi.fn(), verifyEmail: vi.fn() }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

async function verifyNewAddress() {
  vi.mocked(sendEmailVerification).mockResolvedValue({ verificationToken: "proof", expiresIn: 300, message: "sent" });
  vi.mocked(verifyEmail).mockResolvedValue({ message: "verified" });
  fireEvent.change(screen.getByDisplayValue("old@example.com"), { target: { value: "new@example.com" } });
  fireEvent.click(screen.getByRole("button", { name: "인증번호 보내기" }));
  fireEvent.change(await screen.findByLabelText("이메일 인증번호"), { target: { value: "123456" } });
  fireEvent.click(screen.getByRole("button", { name: "인증 확인" }));
  await screen.findByText("verified");
}

test("submits the verified email and proof together", async () => {
  vi.spyOn(window, "alert").mockImplementation(() => undefined);
  vi.mocked(updateMyInfoApi).mockResolvedValue({ message: "saved", user: { ...account, email: "new@example.com" } });
  render(<ProfileModify />);
  await verifyNewAddress();
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(setUser).toHaveBeenCalledWith(expect.objectContaining({ email: "new@example.com" })));
  expect(updateMyInfoApi).toHaveBeenCalledWith({ email: "new@example.com", emailVerificationToken: "proof" });
});

test("editing an already verified address requires verification again", async () => {
  render(<ProfileModify />);
  await verifyNewAddress();
  fireEvent.change(screen.getByDisplayValue("new@example.com"), { target: { value: "different@example.com" } });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(screen.getByText("변경할 이메일의 인증을 완료해 주세요.")).toBeTruthy();
  expect(updateMyInfoApi).not.toHaveBeenCalled();
});
