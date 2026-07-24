import { useAuth } from "../../context/AuthContext";
import { NavLink, Outlet, useNavigate } from "react-router-dom";


export default function Mypage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const menuClass = ({ isActive }: { isActive: boolean }) =>
    `block rounded-lg px-4 py-3 transition-colors ${isActive
      ? "bg-black text-white"
      : "text-gray-700 hover:bg-gray-100"
    }`;

  const handleClickLogout = async () => {
      navigate("/", { replace:true });
      await logout();
      alert("로그아웃이 완료되었습니다.");
      window.location.replace("/");
  }

  return (
    <div className="min-h-screen bg-gray-50 py-10">
      <div className="mx-auto flex max-w-6xl gap-8 px-4">
        {/* 왼쪽 사이드바 */}
        <aside className="w-64 shrink-0">
          <div className="rounded-xl bg-white p-5 shadow-sm">
            <div className="border-b border-gray-200 pb-5">
              <p className="text-sm text-gray-500">마이페이지</p>

              <div className="flex justify-between">
                <h2 className="mt-1 text-xl font-bold">
                  {user?.username ?? "사용자"}님
                </h2>
                <button
                  className="hover:bg-gray-800 rounded-xl p-2 bg-black text-white"
                  onClick={handleClickLogout}
                >
                  로그아웃
                </button>
              </div>
            </div>

            <nav className="mt-5 flex flex-col gap-2">
              <NavLink to="tickets" className={menuClass}>
                내가 예매한 티켓
              </NavLink>

              <NavLink to="selling" className={menuClass}>
                내가 판매 중인 티켓
              </NavLink>

              <NavLink to="profile" className={menuClass}>
                개인정보
              </NavLink>

              <NavLink to="passwordreset" className={menuClass}>
                비밀번호 재설성
              </NavLink>
            </nav>
          </div>
        </aside>

        {/* 오른쪽 콘텐츠 */}
        <main className="min-w-0 flex-1">
          <div className="min-h-125 rounded-xl bg-white p-8 shadow-sm">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}