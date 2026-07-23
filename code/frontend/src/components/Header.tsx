import React from 'react';
import { NavLink, Link } from 'react-router-dom';
import { useAuth } from "../context/AuthContext";


export default function Header() {
  const { user } = useAuth();
  const navItems = [
    { label: "콘서트", path: "/concert" },
    { label: "뮤지컬", path: "/musical" },
    { label: "연극", path: "/play" },
    { label: "클래식/무용", path: "/classic" },
    { label: "전시/스포츠", path: "/exhibition" },
    { label: "버스킹", path: "/busking" },
  ]

  return (
    <header className="w-full bg-[#f6f6f6] border-b border-gray-200 px-6 py-4 flex items-center justify-between">

      {/* 1. 왼쪽 영역: 햄버거 메뉴 & 로고 */}
      <div className="flex items-center space-x-5">
        {/* 로고 텍스트 (실제 구현 시 img 태그로 대체 권장) */}
        <Link to="/">
          <div className="text-[#453eda] font-black text-3xl italic tracking-tighter cursor-pointer">
            Free Ticket
          </div>
        </Link>
      </div>

      {/* 2. 중앙 영역: 네비게이션 링크 */}
      <nav className="flex items-center text-[15px] font-medium text-gray-800">
        {navItems.map((item, index) => (
          <React.Fragment key={item.path}>
            <NavLink
              to={item.path}
              className={({ isActive }) =>
                `
            relative whitespace-nowrap transition-colors
            ${isActive
                  ? "font-bold text-[#453eda]"
                  : "text-gray-800 hover:text-[#ff4b2b]"
                }
          `
              }
            >
              {item.label}
            </NavLink>

            {index < navItems.length - 1 && (
              <span className="px-3 text-xs text-gray-300">•</span>
            )}
          </React.Fragment>
        ))}
      </nav>

      {/* 3. 오른쪽 영역: 아이콘 버튼들 */}
      <div className="flex items-center space-x-4 text-gray-400">
        {/* 사람 (마이페이지) 아이콘 */}
        <Link to="/mypage">
          <button className="flex flex-col items-center hover:text-gray-700 transition-colors">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
              <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
            </svg>
            <span className="text-xs">{user?.username}</span>
          </button>
        </Link>
      </div>
    </header>
  );
}
