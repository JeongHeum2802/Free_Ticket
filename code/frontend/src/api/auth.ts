import { api } from "./axios";
import type {
  LoginRequest,
  LoginResponse,
  MyInfoResponse,
  RefreshResponse,
  SignupRequest,
  SignupResponse,
} from "../types/Auth";

// 로그인 API 
export async function loginApi(data: LoginRequest) : Promise<LoginResponse> {
  const response = await api.post<LoginResponse>("/auth/login", data);
  return response.data;
}

// 회원가입 API
export async function signupApi(data: SignupRequest) : Promise<SignupResponse> {
  const response = await api.post<SignupResponse>("/auth/signup", data);
  return response.data;
}

// Acess 토큰 재발급 API
export async function refreshApi(): Promise<RefreshResponse> {
  const response = await api.post<RefreshResponse>("/auth/refresh");
  return response.data;
}

// 내 정보 API 
export async function getMyInfoApi(): Promise<MyInfoResponse> {
  const response = await api.get<MyInfoResponse>("/auth/me");

  return response.data;
}

// 로그아웃 API
export async function logoutApi(): Promise<void> {
  await api.post("/auth/logout");
}