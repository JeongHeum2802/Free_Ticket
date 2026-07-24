import { api } from "./axios";
import type {
  LoginRequest,
  LoginResponse,
  MyInfoResponse,
  RefreshResponse,
  SignupRequest,
  SignupResponse,
  UpdateMyInfoRequest,
  UpdateMyInfoResponse,
  ResetPasswordRequest,
  ResetPasswordReponse,
  DeleteAccountRequest,
  DeleteAccountReponse,
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

// 정보수정 API
export async function updateMyInfoApi(data: UpdateMyInfoRequest): Promise<UpdateMyInfoResponse> {
  const response = await api.post("/users/me", data);

  return response.data;
}

// 비밀번호 변경 API
export async function resetPasswordApi(data: ResetPasswordRequest): Promise<ResetPasswordReponse> {
  const response = await api.patch("/users/me/password", data);

  return response.data;
}

// 회원 탈퇴 API
export async function deleteAccountApi(data: DeleteAccountRequest): Promise<DeleteAccountReponse> {
  const response = await api.delete("/users/me", {
    data,
  });

  return response.data;
}