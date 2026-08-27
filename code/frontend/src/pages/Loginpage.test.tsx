// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import Loginpage from "./Loginpage";

const { signupApiMock } = vi.hoisted(() => ({
  signupApiMock: vi.fn(),
}));

vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({
    login: vi.fn(),
  }),
}));

vi.mock("react-router-dom", () => ({
  useNavigate: () => vi.fn(),
}));

vi.mock("../api/auth", () => ({
  signupApi: signupApiMock,
}));

describe("Loginpage 약관 동의", () => {
  beforeEach(() => {
    signupApiMock.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  test("각 문서를 끝까지 읽고 동의 버튼을 누르면 해당 체크박스가 선택된다", () => {
    render(<Loginpage />);
    fireEvent.click(screen.getByRole("button", { name: "회원가입" }));

    expect((screen.getByLabelText("프리티켓 서비스 이용약관 동의") as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByLabelText("개인정보 수집·이용 동의") as HTMLInputElement).disabled).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: "서비스 이용약관 보기" }));

    expect((screen.getByRole("button", { name: "동의" }) as HTMLButtonElement).disabled).toBe(true);

    const termsScrollArea = screen.getByTestId("terms-scroll-area");
    Object.defineProperties(termsScrollArea, {
      clientHeight: { configurable: true, value: 300 },
      scrollHeight: { configurable: true, value: 900 },
      scrollTop: { configurable: true, value: 600 },
    });
    fireEvent.scroll(termsScrollArea);
    expect((screen.getByRole("button", { name: "동의" }) as HTMLButtonElement).disabled).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: "동의" }));

    expect((screen.getByLabelText("프리티켓 서비스 이용약관 동의") as HTMLInputElement).disabled).toBe(false);
    expect((screen.getByLabelText("프리티켓 서비스 이용약관 동의") as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText("개인정보 수집·이용 동의") as HTMLInputElement).disabled).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: "개인정보 수집·이용 동의 보기" }));

    const privacyScrollArea = screen.getByTestId("privacy-scroll-area");
    Object.defineProperties(privacyScrollArea, {
      clientHeight: { configurable: true, value: 300 },
      scrollHeight: { configurable: true, value: 900 },
      scrollTop: { configurable: true, value: 600 },
    });
    fireEvent.scroll(privacyScrollArea);
    fireEvent.click(screen.getByRole("button", { name: "동의" }));

    expect((screen.getByLabelText("개인정보 수집·이용 동의") as HTMLInputElement).disabled).toBe(false);
    expect((screen.getByLabelText("개인정보 수집·이용 동의") as HTMLInputElement).checked).toBe(true);
  });

  test("필수 약관에 모두 동의하지 않으면 회원가입 요청을 보내지 않는다", () => {
    render(<Loginpage />);
    fireEvent.click(screen.getByRole("button", { name: "회원가입" }));
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));

    expect(screen.getByText("필수 약관에 모두 동의해 주세요.")).toBeTruthy();
    expect(signupApiMock).not.toHaveBeenCalled();
  });

  test("한 항목만 동의하면 가입을 막고 두 항목 모두 동의하면 가입을 요청한다", async () => {
    signupApiMock.mockResolvedValue({ message: "회원가입 성공" });
    vi.spyOn(window, "alert").mockImplementation(() => undefined);

    render(<Loginpage />);
    fireEvent.click(screen.getByRole("button", { name: "회원가입" }));

    fireEvent.click(screen.getByRole("button", { name: "서비스 이용약관 보기" }));
    const termsScrollArea = screen.getByTestId("terms-scroll-area");
    Object.defineProperties(termsScrollArea, {
      clientHeight: { configurable: true, value: 300 },
      scrollHeight: { configurable: true, value: 900 },
      scrollTop: { configurable: true, value: 600 },
    });
    fireEvent.scroll(termsScrollArea);
    fireEvent.click(screen.getByRole("button", { name: "동의" }));
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));

    expect(signupApiMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByLabelText("프리티켓 서비스 이용약관 동의"));
    fireEvent.click(screen.getByRole("button", { name: "개인정보 수집·이용 동의 보기" }));
    const privacyScrollArea = screen.getByTestId("privacy-scroll-area");
    Object.defineProperties(privacyScrollArea, {
      clientHeight: { configurable: true, value: 300 },
      scrollHeight: { configurable: true, value: 900 },
      scrollTop: { configurable: true, value: 600 },
    });
    fireEvent.scroll(privacyScrollArea);
    fireEvent.click(screen.getByRole("button", { name: "동의" }));
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));

    expect(signupApiMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByLabelText("프리티켓 서비스 이용약관 동의"));
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));

    await waitFor(() => expect(signupApiMock).toHaveBeenCalledTimes(1));
  });

  test("Escape 키로 약관 모달을 닫는다", () => {
    render(<Loginpage />);
    fireEvent.click(screen.getByRole("button", { name: "회원가입" }));
    fireEvent.click(screen.getByRole("button", { name: "서비스 이용약관 보기" }));

    fireEvent.keyDown(document, { key: "Escape" });

    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
