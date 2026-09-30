import { CanceledError } from "axios";

let accessToken: string | null = null;
let authGeneration = 0;
const authChannel = typeof window !== "undefined" && typeof window.BroadcastChannel === "function"
  ? new window.BroadcastChannel("free-ticket-auth") : null;
if (authChannel) {
  authChannel.onmessage = (event) => {
    if (event.data === "auth:changed") clearAuth();
  };
}

export function getAccessToken() {
  return accessToken;
}

// Identity comparison only; JWT signature and authorization are verified by the backend.
export function getAccessTokenSubject(token: string): string {
  const parts = token.split(".");
  if (parts.length !== 3 || parts.some((part) => !part)) throw new Error("잘못된 로그인 정보입니다. 다시 로그인해 주세요.");
  const payload = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
  if (typeof payload?.sub !== "string" || !payload.sub || payload.token_use !== "access") {
    throw new Error("잘못된 로그인 정보입니다. 다시 로그인해 주세요.");
  }
  return payload.sub;
}

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAuthGeneration() {
  return authGeneration;
}

export function beginAuthChange() {
  return ++authGeneration;
}

export function checkAuthGeneration(generation: number) {
  if (generation !== authGeneration) {
    throw new CanceledError("인증 상태가 변경되었습니다.");
  }
}

export function clearAuth(invalidateGeneration = true) {
  if (invalidateGeneration) beginAuthChange();
  setAccessToken(null);
  window.dispatchEvent(new Event("auth:logout"));
}

export function notifyOtherTabs() {
  authChannel?.postMessage("auth:changed");
}

export async function withAuthCookieLock<T>(action: () => Promise<T>): Promise<T> {
  if (!navigator.locks?.request) {
    throw new Error("이 브라우저에서는 안전한 로그인을 사용할 수 없습니다. HTTPS 또는 localhost에서 최신 Chrome 또는 Edge 브라우저를 사용해 주세요.");
  }
  return navigator.locks.request("free-ticket-auth", action);
}
