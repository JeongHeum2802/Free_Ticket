import axios, {
  AxiosError,
  type InternalAxiosRequestConfig,
} from "axios";
import { getAccessToken, setAccessToken } from "./token";

type RetryRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean;
};

type RefreshResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
}

type ErrorResponse = {
  code?: string;
  message?: string;
};

export const api = axios.create({
  baseURL: import.meta.env.VITE_BACKEND_URL,
  withCredentials: true,
});

// 모든 요청에 Access Token 자동 추가
api.interceptors.request.use((config) => {
  const token = getAccessToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

// Refresh 요청 전용 인스턴스
const refreshClient = axios.create({
  baseURL: import.meta.env.VITE_BACKEND_URL,
  withCredentials: true,
});

api.interceptors.response.use(
  (response) => response,

  async (error: AxiosError) => {
    const originalRequest = error.config as RetryRequestConfig | undefined;

    if (!originalRequest) {
      return Promise.reject(error);
    }

    const axiosError = error as AxiosError<ErrorResponse>;

    const isRefreshRequest = originalRequest.url?.includes("/auth/refresh");
    const isAccessTokenExpired = axiosError.response?.status === 401 &&
      axiosError.response.data?.code === "ACCESS_TOKEN_EXPIRED";
    if (
      !isAccessTokenExpired ||
      originalRequest._retry ||
      isRefreshRequest
    ) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      const response = await refreshClient.post<RefreshResponse>(
        "/auth/refresh",
      );

      const newAccessToken = response.data.accessToken;
      setAccessToken(newAccessToken);

      originalRequest.headers.Authorization =
        `Bearer ${newAccessToken}`;

      return api(originalRequest);
    } catch (refreshError) {
      setAccessToken(null);
      // refresh 토큰이 만료된 경우 브라우저 이벤트 발생
      window.dispatchEvent(new Event("auth:logout"));
      return Promise.reject(refreshError);
    }
  },
);