// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import EmailVerification from "./EmailVerification";
import { sendEmailVerification, verifyEmail } from "../api/emailVerification";

vi.mock("../api/emailVerification", () => ({ sendEmailVerification: vi.fn(), verifyEmail: vi.fn() }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

test("only returns proof after the server confirms the code, and resend clears it", async () => {
  vi.mocked(sendEmailVerification).mockResolvedValue({ verificationToken: "proof", expiresIn: 300, message: "sent" });
  vi.mocked(verifyEmail).mockResolvedValue({ message: "verified" });
  const onVerified = vi.fn();
  render(<EmailVerification email="user@example.com" onVerified={onVerified} />);
  fireEvent.click(screen.getByRole("button", { name: "인증번호 보내기" }));
  fireEvent.change(await screen.findByLabelText("이메일 인증번호"), { target: { value: "123456" } });
  expect(onVerified).not.toHaveBeenCalledWith("proof");
  fireEvent.click(screen.getByRole("button", { name: "인증 확인" }));
  await waitFor(() => expect(onVerified).toHaveBeenLastCalledWith("proof"));
  expect(verifyEmail).toHaveBeenCalledWith("user@example.com", "proof", "123456");
  fireEvent.click(screen.getByRole("button", { name: "인증번호 다시 보내기" }));
  expect(onVerified).toHaveBeenLastCalledWith("");
});

test("ignores a response after the email field has changed and the old component unmounts", async () => {
  let finish!: (result: { verificationToken: string; expiresIn: number; message: string }) => void;
  vi.mocked(sendEmailVerification).mockReturnValue(new Promise(resolve => { finish = resolve; }));
  const onVerified = vi.fn();
  const view = render(<EmailVerification key="first" email="first@example.com" onVerified={onVerified} />);
  fireEvent.click(screen.getByRole("button", { name: "인증번호 보내기" }));
  view.rerender(<EmailVerification key="second" email="second@example.com" onVerified={onVerified} />);
  finish({ verificationToken: "old-proof", expiresIn: 300, message: "sent" });
  await waitFor(() => expect(screen.queryByLabelText("이메일 인증번호")).toBeNull());
  expect(onVerified).not.toHaveBeenCalledWith("old-proof");
});
