import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import { checkAuthGeneration, clearAuth, getAccessToken, getAccessTokenSubject, getAuthGeneration, setAccessToken, withAuthCookieLock } from "./token";
import type { RefreshResponse } from "../types/Auth";

type RetryRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean;
  _authGeneration?: number;
};

type ErrorResponse = { code?: string; message?: string };

const config = { baseURL: import.meta.env.VITE_BACKEND_URL, withCredentials: true };
export const api = axios.create(config);
const refreshClient = axios.create({ ...config, timeout: 10_000 });
let refreshPromise: Promise<RefreshResponse> | null = null;
let refreshGeneration = -1;

export function refreshAccessToken(): Promise<RefreshResponse> {
  const generation = getAuthGeneration();
  if (refreshPromise && refreshGeneration === generation) return refreshPromise;
  refreshGeneration = generation;
  const request = withAuthCookieLock(async () => {
    checkAuthGeneration(generation);
    const response = await refreshClient.post<RefreshResponse>("/auth/refresh");
    checkAuthGeneration(generation);
    try {
      const subject = getAccessTokenSubject(response.data.accessToken);
      const current = getAccessToken();
      if (current && getAccessTokenSubject(current) !== subject) {
        throw new Error("다른 계정으로 로그인 상태가 변경되었습니다. 다시 로그인해 주세요.");
      }
    } catch (error) {
      clearAuth();
      throw error;
    }
    setAccessToken(response.data.accessToken);
    return response.data;
  }).catch((error: unknown) => {
    checkAuthGeneration(generation);
    if (axios.isAxiosError<ErrorResponse>(error) && error.response?.status === 401 &&
        ["REFRESH_TOKEN_NOT_FOUND", "REFRESH_TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN", "REFRESH_TOKEN_REVOKED"].includes(error.response.data?.code ?? "")) {
      clearAuth();
    }
    throw error;
  }).finally(() => {
    if (refreshPromise === request) refreshPromise = null;
  });
  refreshPromise = request;
  return request;
}

api.interceptors.request.use((config: RetryRequestConfig) => {
  config._authGeneration ??= getAuthGeneration();
  checkAuthGeneration(config._authGeneration);
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  else delete config.headers.Authorization;
  return config;
});

api.interceptors.response.use((response) => response, async (error: AxiosError<ErrorResponse>) => {
  const request = error.config as RetryRequestConfig | undefined;
  if (!request || request._retry || request.url?.includes("/auth/refresh") ||
      error.response?.status !== 401 || error.response.data?.code !== "ACCESS_TOKEN_EXPIRED") {
    throw error;
  }
  checkAuthGeneration(request._authGeneration!);
  const token = getAccessToken();
  if (!token) throw error;
  request._retry = true;
  if (request.headers.Authorization === `Bearer ${token}`) await refreshAccessToken();
  checkAuthGeneration(request._authGeneration!);
  return api(request);
});
