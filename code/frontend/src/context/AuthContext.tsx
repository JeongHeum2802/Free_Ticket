import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";

import { useNavigate } from "react-router-dom";

import {
  getMyInfoApi,
  loginApi,
  logoutApi,
  refreshApi,
} from "../api/auth";

import { setAccessToken as setAxiosAccessToken } from "../api/token";

import type { LoginRequest, User } from "../types/Auth";

type AuthContextType = {
  user: User | null;
  loading: boolean;
  accessToken: string | null;
  login: (data: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  setUser: React.Dispatch<React.SetStateAction<User | null>>;
};

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const navigate = useNavigate();

  const saveAccessToken = (token: string | null) => {
    setAccessToken(token); // react context 상태
    setAxiosAccessToken(token); // Axios 요청 인터셉터가 사용하는 상태
  }

  const login = async (data: LoginRequest) => {
    const response = await loginApi(data);

    saveAccessToken(response.accessToken);
    setUser(response.user);
  }

  const logout = async () => {
    try {
      await logoutApi();
    } catch (error) {
      // 서버 로그아웃이 실패해도 클라이언트 상태는 정리
      console.error("서버 로그아웃 실패:", error);
    } finally {
      saveAccessToken(null);
      setUser(null);
    }
  };
  
  useEffect(() => {
    // 새로고침시 local storage가 아닌 백엔드 요청으로 정보 갱신
    const restoreAuth = async () => {
      try {
        // 새 access token 발급
        const refreshResponse = await refreshApi();
        
        const newAccessToken = refreshResponse.accessToken;
        saveAccessToken(newAccessToken);

        // 새 access token으로 최신 사용자 정보 조회
        const userResponse = await getMyInfoApi();
        setUser(userResponse.user);
      } catch {
        saveAccessToken(null);
        setUser(null);
      } finally {
        setLoading(false);
      }
    }

    restoreAuth();
  }, []);

  // 브라우저에서 로그아웃 이벤트 발생시 ( refresh 만료시 ) 초기화
  useEffect(() => {

    const handleAuthLogout = () => {
      setAccessToken(null);
      setUser(null);
      navigate("/", { replace: true });
    };

    window.addEventListener("auth:logout", handleAuthLogout);

    return () => {
      window.removeEventListener("auth:logout", handleAuthLogout);
    }
  }, [navigate]);

  const value: AuthContextType = {
    user,
    loading,
    accessToken,
    login,
    logout,
    setUser,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth는 AuthProvider 내부에서만 사용할 수 있습니다.");
  }

  return context;
}