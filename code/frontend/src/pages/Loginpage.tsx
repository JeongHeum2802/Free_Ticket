import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

export default function Loginpage() {
  const navigate = useNavigate();
  const [isLogin, setIsLogin] = useState(true); // 로그인 회원가입 분리 state
  const { login } = useAuth();

  const handleClickLoginTab = () => {
    setIsLogin(true);
  }

  const handleClickSignupTab = () => {
    setIsLogin(false);
  }

  const handleClickLogin = () => {
    login({
      id: 2802,
      name: "최정흠",
      email: "wjdgma60@gmail.com",
    });
    navigate('/');
  }

  return (
    <div className="min-h-screen bg-white text-[#333]">
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

        {/* Inputs */}
        <div className="mt-5 space-y-3">
          <input
            type="text"
            placeholder="아이디"
            className="h-12 w-full border border-gray-300 px-4 text-sm outline-none placeholder:text-gray-400 focus:border-[#1e88ff]"
          />

          <input
            type="password"
            placeholder="비밀번호"
            className="h-12 w-full border border-gray-300 px-4 text-sm outline-none placeholder:text-gray-400 focus:border-[#1e88ff]"
          />
        </div>

        {/* Options */}
        <div className="mt-5 flex items-center gap-5 text-sm text-gray-600">
          <label className="flex items-center gap-2">
            <input type="checkbox" className="h-5 w-5 accent-[#1e88ff]" />
            로그인 상태 유지
          </label>

          <label className="flex items-center gap-2">
            <input type="checkbox" className="h-5 w-5 accent-[#1e88ff]" />
            아이디 저장
          </label>
        </div>

        {/* Login Button */}
        <button
          className="mt-5 h-14.5 w-full bg-[#1089ff] text-lg font-semibold text-white hover:bg-[#0078ed]"
          onClick={handleClickLogin}
        >
          로그인
        </button>

        {/* Find Links */}
        <div className="mt-5 flex items-center gap-3 text-sm text-gray-600">
          <button>아이디 찾기</button>
          <span className="text-gray-300">|</span>
          <button>비밀번호 찾기</button>
        </div>

        {/* Social Login */}
        <div className="mt-11 space-y-2">
          <button className="flex h-40px h-10 w-full items-center border border-gray-300 bg-white text-sm">
            <span className="flex h-full w-10 items-center justify-center border-r border-gray-300 text-2xl font-bold text-[#1ec800]">
              N
            </span>
            <span className="pl-4">네이버 아이디로 로그인</span>
          </button>

          <button className="flex h-10 w-full items-center border border-gray-300 bg-white text-sm">
            <span className="flex h-full w-10 items-center justify-center border-r border-gray-300">
              <span className="h-4 w-4 rounded-full bg-[#f7d600]" />
            </span>
            <span className="pl-4">카카오 아이디로 로그인</span>
          </button>
        </div>
      </main>

      {/* Footer */}
      <footer className="mt-20 border-t border-gray-200 py-5 text-center text-xs text-gray-500">
        Copyright © <strong>YES24 Corp.</strong> All rights Reserved.
      </footer>
    </div>
  );
}