import { api, refreshAccessToken } from "./axios";
import { isAxiosError } from "axios";
import { beginAuthChange, checkAuthGeneration, clearAuth, getAccessTokenSubject, getAuthGeneration, notifyOtherTabs, setAccessToken, withAuthCookieLock } from "./token";
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

let lastCommittedLoginGeneration = -1;

// 로그인 API 
export async function loginApi(data: LoginRequest) : Promise<LoginResponse> {
  const generation = beginAuthChange();
  return withAuthCookieLock(async () => {
    checkAuthGeneration(generation);
    const response = await api.post<LoginResponse>("/auth/login", data, { timeout: 10_000 });
    if (generation !== getAuthGeneration()) {
      // The cookie changed, but this response cannot cancel the newer pending login.
      if (lastCommittedLoginGeneration <= generation) clearAuth(false);
      notifyOtherTabs();
    }
    checkAuthGeneration(generation);
    try {
      getAccessTokenSubject(response.data.accessToken);
    } catch (error) {
      clearAuth(false);
      notifyOtherTabs();
      throw error;
    }
    // Also invalidate requests issued while login was waiting for its response.
    lastCommittedLoginGeneration = beginAuthChange();
    setAccessToken(response.data.accessToken);
    notifyOtherTabs();
    return response.data;
  });
}

// 회원가입 API
export async function signupApi(data: SignupRequest) : Promise<SignupResponse> {
  const response = await api.post<SignupResponse>("/auth/signup", data);
  return response.data;
}

// Acess 토큰 재발급 API
export async function refreshApi(): Promise<RefreshResponse> {
  return refreshAccessToken();
}

// 내 정보 API 
export async function getMyInfoApi(): Promise<MyInfoResponse> {
  const response = await api.get<MyInfoResponse>("/auth/me", { timeout: 10_000 });

  return response.data;
}

// 로그아웃 API
export async function logoutApi(): Promise<void> {
  const generation = beginAuthChange();
  await withAuthCookieLock(async () => {
    checkAuthGeneration(generation);
    await api.post("/auth/logout", undefined, { timeout: 10_000 });
    checkAuthGeneration(generation);
    beginAuthChange();
    setAccessToken(null);
    notifyOtherTabs();
  });
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
  const generation = beginAuthChange();
  const remove = () => withAuthCookieLock(async () => {
    checkAuthGeneration(generation);
    // Release the cookie lock before any refresh; Web Locks are not reentrant.
    const config = { data, _retry: true, timeout: 10_000 };
    const response = await api.delete<DeleteAccountReponse>("/users/me", config);
    checkAuthGeneration(generation);
    clearAuth();
    notifyOtherTabs();
    return response.data;
  });
  try {
    return await remove();
  } catch (error) {
    checkAuthGeneration(generation);
    if (!isAxiosError<{ code?: string }>(error) || error.response?.status !== 401 ||
        error.response.data?.code !== "ACCESS_TOKEN_EXPIRED") throw error;
    await refreshAccessToken();
    return remove();
  }
}
