import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import type { ReactNode } from "react";

type ProtectedRouteProps = {
  children: ReactNode;
};

function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { user, loading, restoreError, retryRestore } = useAuth();

  if (loading) {
    return <div>로딩 중...</div>;
  }

  if (!user && restoreError) {
    return <div className="p-6 text-center">
      <p role="alert">{restoreError}</p>
      <button type="button" onClick={() => void retryRestore()} className="mt-4 rounded bg-black px-4 py-2 text-white">다시 시도</button>
    </div>;
  }

  if (user == null) {
    alert("로그인이 필요한 서비스입니다. 로그인 페이지로 이동합니다."); 
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

export default ProtectedRoute;
