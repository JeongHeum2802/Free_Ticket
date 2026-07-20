import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import type { ReactNode } from "react";

type ProtectedRouteProps = {
  children: ReactNode;
};

function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isLogin, loading } = useAuth();

  if (loading) {
    return <div>로딩 중...</div>;
  }

  if (!isLogin) {
    alert("로그인이 필요한 서비스입니다. 로그인 페이지로 이동합니다."); 
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

export default ProtectedRoute;