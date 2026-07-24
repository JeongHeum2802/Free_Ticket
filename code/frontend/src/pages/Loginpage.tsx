import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import axios from "axios";
import { signupApi } from "../api/auth";

import type { SignupRequest } from '../types/Auth';

export default function Loginpage() {
  const navigate = useNavigate();
  const [isLogin, setIsLogin] = useState(true); // 로그인 회원가입 분리 state
  const [emailInput, setEmailInput] = useState<string>("");
  const [passwordInput, setPasswordInput] = useState<string>("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isFindPasswordModalOpen, setIsFindPasswordModalOpen] = useState<boolean>(false);

  const [signUpInputUsername, setSignUpInputUsername] = useState<string>("");
  const [signUpInputEmail, setSignUpInputEmail] = useState<string>("");
  const [signUpInputPassword, setSignUpInputPassword] = useState<string>("");
  const [signUpInputPasswordDetector, setSignUpInputPasswordDetector] = useState<string>("");
  const [signUpInputPhonenumber, setSignUpInputPhonenumber] = useState<string>("");

  const { login } = useAuth();

  const handleClickLoginTab = () => {
    setIsLogin(true);
  }

  const handleClickSignupTab = () => {
    setIsLogin(false);
  }

  // 로그인 Input 상태 변화
  const handleChangeEmailInput = (value: string): void => {
    setEmailInput(value);
  }

  const handleChangePasswordInput = (value: string): void => {
    setPasswordInput(value);
  }

  // 회원가입 Input 상태변화
  const handleChangeSignupUsernameInput = (value: string): void => {
    setSignUpInputUsername(value);
  }

  const handleChangeSignupEmailInput = (value: string): void => {
    setSignUpInputEmail(value);
  }

  const handleChangeSignupPasswordInput = (value: string): void => {
    setSignUpInputPassword(value);
  }

  const handleChangeSignupPasswordDetectorInput = (value: string): void => {
    setSignUpInputPasswordDetector(value);
  }

  const handleChangeSignupPhonenumberInput = (value: string): void => {
    setSignUpInputPhonenumber(value);
  }

  const handleClickFindPassword = () => {
    setIsFindPasswordModalOpen(true);
  }

  // 로그인
  const handleClickLogin = async () => {
    try {
      await login({
        email: emailInput,
        password: passwordInput,
      });
      navigate("/");
    } catch (error) {
      if (axios.isAxiosError(error)) {
        let message = error.response?.data?.message ?? "로그인에 실패했습니다.";

        setErrorMessage(message);
        return;
      }

      setErrorMessage("예상치 못한 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }
  }

  const handleClickSignup = async () => {
    if (signUpInputPassword !== signUpInputPasswordDetector) {
      setErrorMessage("비밀번호가 일치하지 않습니다.");
      return;
    }

    try {
      const data: SignupRequest = {
        username: signUpInputUsername,
        password: signUpInputPassword,
        email: signUpInputEmail,
        phonenumber: signUpInputPhonenumber,
      }
      const response = await signupApi(data);

      alert(response.message);
      setIsLogin(true);
      return;
    } catch (error) {
      if (axios.isAxiosError(error)) {
        let message = error.response?.data?.message ?? "예상치 못한 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
        setErrorMessage(message);
      }
    }
  }

  return (
    <div className="min-h-screen bg-white text-[#333]">
      {isFindPasswordModalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4"
          onClick={() => setIsFindPasswordModalOpen(false)}
        >
          <div
            className="w-full max-w-md rounded-2xl bg-white p-7 shadow-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-2xl font-bold text-gray-900">
                  비밀번호 재설정
                </h2>
                <p className="mt-2 text-sm leading-6 text-gray-500">
                  가입할 때 사용한 이메일을 입력하면
                  <br />
                  비밀번호 재설정 페이지를 보내드립니다.
                </p>
              </div>

              <button
                type="button"
                onClick={() => setIsFindPasswordModalOpen(false)}
                className="rounded-lg p-2 text-gray-400 transition hover:bg-gray-100 hover:text-gray-700"
                aria-label="모달 닫기"
              >
                ✕
              </button>
            </div>

            <div className="mt-7">
              <label
                htmlFor="reset-email"
                className="mb-2 block text-sm font-medium text-gray-700"
              >
                이메일
              </label>

              <input
                id="reset-email"
                type="email"
                placeholder="example@email.com"
                className="w-full rounded-xl border border-gray-300 px-4 py-3 text-sm outline-none transition placeholder:text-gray-400 focus:border-black focus:ring-2 focus:ring-black/10"
              />
            </div>

            <div className="mt-7 flex gap-3">
              <button
                type="button"
                onClick={() => setIsFindPasswordModalOpen(false)}
                className="flex-1 rounded-xl border border-gray-300 px-4 py-3 font-medium text-gray-700 transition hover:bg-gray-50"
              >
                취소
              </button>

              <button
                type="button"
                className="flex-1 rounded-xl bg-black px-4 py-3 font-medium text-white transition hover:bg-gray-800"
              >
                재설정 페이지 전송
              </button>
            </div>

            <p className="mt-5 text-center text-xs text-gray-400">
              이메일이 도착하지 않으면 스팸 메일함을 확인해주세요.
            </p>
          </div>
        </div>
      )}
      {/* Main */}
      <main className="mx-auto mt-14 w-90">
        {/* Tabs */}
        <div className="flex h-10">
          <button
            className={isLogin ?
              "flex-1 border border-[#7da7e8] border-b-white text-sm text-[#4d82d8]" :
              "flex-1 border border-gray-300 border-l-0 bg-[#fafafa] text-sm text-gray-600"}
            onClick={handleClickLoginTab}
          >
            로그인
          </button>
          <button
            className={!isLogin ?
              "flex-1 border border-[#7da7e8] border-b-white text-sm text-[#4d82d8]" :
              "flex-1 border border-gray-300 border-l-0 bg-[#fafafa] text-sm text-gray-600"}
            onClick={handleClickSignupTab}
          >
            회원가입
          </button>
        </div>

        {/* Login Inputs */}
        {isLogin && (<form
          onSubmit={(e) => {
            e.preventDefault();
            handleClickLogin();
          }}>
          <div className="mt-5 space-y-3">
            <input
              type="text"
              placeholder="이메일"
              value={emailInput}
              onChange={(e) => handleChangeEmailInput(e.target.value)}
              className="h-12 w-full border border-gray-300 px-4 text-sm outline-none placeholder:text-gray-400 focus:border-[#1e88ff]"
            />

            <input
              type="password"
              placeholder="비밀번호"
              value={passwordInput}
              onChange={(e) => handleChangePasswordInput(e.target.value)}
              className="h-12 w-full border border-gray-300 px-4 text-sm outline-none placeholder:text-gray-400 focus:border-[#1e88ff]"
            />
          </div>
          {/* 로그인 실패 메세지 */}
          {errorMessage && <span className="text-red-400 text-sm mt-10">{errorMessage}</span>}

          <button
            type="submit"
            className="mt-5 h-14.5 w-full bg-[#1089ff] text-lg font-semibold text-white hover:bg-[#0078ed]"
          >
            로그인
          </button>

          <div className="mt-5 flex items-center gap-3 text-sm text-gray-600">
            <button className="hover:text-blue-500">아이디 찾기</button>
            <span className="text-gray-300">|</span>
            <button onClick={handleClickFindPassword} className="hover:text-blue-500">비밀번호 찾기</button>
          </div>
        </form>)}

        {/* SignUp Inputs */}
        {!isLogin && (<form
          onSubmit={(e) => {
            e.preventDefault();
            handleClickSignup();
          }}
        >
          <div className="mt-5 space-y-3">
            <input
              type="text"
              name="username"
              value={signUpInputUsername}
              onChange={(e) => handleChangeSignupUsernameInput(e.target.value)}
              placeholder="아이디 (유저이름)"
              className="h-12 w-full border border-gray-300 px-4 text-sm outline-none placeholder:text-gray-400 focus:border-[#1e88ff]"
            />

            <input
              type="email"
              name="email"
              value={signUpInputEmail}
              onChange={(e) => handleChangeSignupEmailInput(e.target.value)}
              placeholder="이메일 (예: user@example.com)"
              className="h-12 w-full border border-gray-300 px-4 text-sm outline-none placeholder:text-gray-400 focus:border-[#1e88ff]"
            />

            <input
              type="password"
              name="password"
              value={signUpInputPassword}
              onChange={(e) => handleChangeSignupPasswordInput(e.target.value)}
              placeholder="비밀번호"
              className="h-12 w-full border border-gray-300 px-4 text-sm outline-none placeholder:text-gray-400 focus:border-[#1e88ff]"
            />

            <input
              type="password"
              name="password_valid"
              value={signUpInputPasswordDetector}
              onChange={(e) => handleChangeSignupPasswordDetectorInput(e.target.value)}
              placeholder="비밀번호를 한번 더 입력해 주세요"
              className="h-12 w-full border border-gray-300 px-4 text-sm outline-none placeholder:text-gray-400 focus:border-[#1e88ff]"
            />

            <input
              type="tel"
              name="phonenumber"
              value={signUpInputPhonenumber}
              onChange={(e) => handleChangeSignupPhonenumberInput(e.target.value)}
              placeholder="휴대폰 번호 (- 없이 입력)"
              className="h-12 w-full border border-gray-300 px-4 text-sm outline-none placeholder:text-gray-400 focus:border-[#1e88ff]"
            />
          </div>

          {errorMessage && <span className="text-red-400 text-sm mt-10">{errorMessage}</span>}

          {/* 약관 동의 (옵션) */}
          <div className="mt-5 flex items-center gap-2 text-sm text-gray-600">
            <input type="checkbox" id="terms" className="h-5 w-5 accent-[#1e88ff]" />
            <label htmlFor="terms">이용약관 및 개인정보 수집에 동의합니다.</label>
          </div>

          {/* 회원가입 버튼 */}
          <button
            type="submit"
            className="mt-6 h-14 w-full bg-[#1089ff] text-lg font-semibold text-white transition-colors hover:bg-[#0078ed]"
          >
            가입하기
          </button>
        </form>)}
      </main>

      {/* Footer */}
      <footer className="mt-20 border-t border-gray-200 py-5 text-center text-xs text-gray-500">
        Copyright © <strong>Free Ticket.</strong> All rights Reserved.
      </footer>
    </div >
  );
}