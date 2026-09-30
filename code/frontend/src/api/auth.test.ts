// @vitest-environment jsdom
import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { waitFor } from "@testing-library/react";
import { api } from "./axios";
import { deleteAccountApi, loginApi, logoutApi, refreshApi } from "./auth";
import { getAccessToken, setAccessToken } from "./token";

const transport = vi.hoisted(() => ({ adapter: undefined as AxiosAdapter | undefined }));
vi.mock("axios", async (importOriginal) => {
  const actual = await importOriginal<typeof import("axios")>();
  return { ...actual, default: { ...actual.default, create: (config: object) =>
    actual.default.create({ ...config, adapter: (request) => transport.adapter!(request) }) } };
});

const calls: { url?: string; authorization: unknown }[] = [];
const credentials = { email: "user@example.com", password: "password123" };
function accessToken(sub: string, jti: string) {
  return `${btoa('{"alg":"HS256","typ":"JWT"}')}.${btoa(JSON.stringify({ sub, token_use: "access", jti }))}.signature`;
}
const OLD_TOKEN = accessToken("1", "old"), NEW_TOKEN = accessToken("1", "new");
const LOGIN_TOKEN = accessToken("2", "login"), OTHER_TOKEN = accessToken("3", "other");

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => { resolve = done; });
  return { promise, resolve };
}

function reply(config: InternalAxiosRequestConfig, data: unknown) {
  return { config, data, status: 200, statusText: "OK", headers: {} };
}

function failure(config: InternalAxiosRequestConfig, status = 401, code = "ACCESS_TOKEN_EXPIRED") {
  return new AxiosError(code, "ERR_BAD_RESPONSE", config, undefined,
    { ...reply(config, { code, message: code }), status });
}

beforeEach(() => {
  calls.length = 0;
  setAccessToken(OLD_TOKEN);
  let queue = Promise.resolve();
  vi.stubGlobal("navigator", { locks: { request: (_name: string, action: () => Promise<unknown>) => {
    const result = queue.then(action);
    queue = result.then(() => undefined, () => undefined);
    return result;
  } } });
  transport.adapter = async (config) => {
    calls.push({ url: config.url, authorization: config.headers.Authorization });
    if (config.url === "/auth/refresh") return reply(config, { accessToken: NEW_TOKEN, tokenType: "Bearer", expiresIn: 600 });
    if (config.url === "/auth/login") return reply(config, { message: "OK", accessToken: LOGIN_TOKEN, tokenType: "Bearer", expiresIn: 600,
      user: { id: 2, username: "새 사용자", email: "user@example.com", phonenumber: "01012345678", role: "USER", eventDirector: false } });
    if (config.url === "/auth/logout" || config.url === "/users/me") return reply(config, { message: "OK" });
    if (config.headers.Authorization === `Bearer ${OLD_TOKEN}`) throw failure(config);
    return reply(config, { authorization: config.headers.Authorization });
  };
});

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); setAccessToken(null); });

test("initial restoration and simultaneous expired requests share one refresh", async () => {
  const gate = deferred();
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/auth/refresh") await gate.promise;
    return adapter(config);
  };
  const results = [refreshApi(), api.get("/orders/a"), api.get("/orders/b")];
  await new Promise((resolve) => setTimeout(resolve, 0));
  gate.resolve();
  await Promise.all(results);
  expect(calls.filter(({ url }) => url === "/auth/refresh")).toHaveLength(1);
  expect(getAccessToken()).toBe(NEW_TOKEN);
});

test("a late old-token 401 retries with the latest token without another refresh", async () => {
  const gate = deferred();
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/orders/late" && config.headers.Authorization === `Bearer ${OLD_TOKEN}`) await gate.promise;
    return adapter(config);
  };
  const late = api.get("/orders/late");
  await api.get("/orders/first");
  gate.resolve();
  const result = await late;
  expect(result.data.authorization).toBe(`Bearer ${NEW_TOKEN}`);
  expect(calls.filter(({ url }) => url === "/auth/refresh")).toHaveLength(1);
});

test.each([0, 500, 401])("transient or unrelated refresh error %s preserves authentication", async (status) => {
  const listener = vi.fn();
  window.addEventListener("auth:logout", listener);
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/auth/refresh") throw status ? failure(config, status, "UNRELATED_ERROR") : new Error("network unavailable");
    return adapter(config);
  };
  try {
    await expect(api.get("/orders")).rejects.toBeDefined();
    expect(getAccessToken()).toBe(OLD_TOKEN);
    expect(listener).not.toHaveBeenCalled();
  } finally { window.removeEventListener("auth:logout", listener); }
});

test.each(["REFRESH_TOKEN_NOT_FOUND", "REFRESH_TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN", "REFRESH_TOKEN_REVOKED"])(
  "confirmed refresh rejection %s clears authentication once", async (code) => {
    const listener = vi.fn();
    window.addEventListener("auth:logout", listener);
    const adapter = transport.adapter!;
    transport.adapter = async (config) => {
      if (config.url === "/auth/refresh") throw failure(config, 401, code);
      return adapter(config);
    };
    try {
      await Promise.allSettled([api.get("/orders/a"), api.get("/orders/b")]);
      expect(getAccessToken()).toBeNull();
      expect(listener).toHaveBeenCalledTimes(1);
    } finally { window.removeEventListener("auth:logout", listener); }
  });

test("an expired retry is not refreshed repeatedly", async () => {
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/orders") { calls.push({ url: config.url, authorization: config.headers.Authorization }); throw failure(config); }
    return adapter(config);
  };
  await expect(api.get("/orders")).rejects.toBeDefined();
  expect(calls.filter(({ url }) => url === "/orders")).toHaveLength(2);
  expect(calls.filter(({ url }) => url === "/auth/refresh")).toHaveLength(1);
});

test("logout waits for cookie rotation and stale refresh cannot restore the access token", async () => {
  const gate = deferred();
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/auth/refresh") { calls.push({ url: "refresh-start", authorization: null }); await gate.promise; }
    return adapter(config);
  };
  const refresh = refreshApi();
  const settledRefresh = Promise.allSettled([refresh]);
  await waitFor(() => expect(calls.some(({ url }) => url === "refresh-start")).toBe(true));
  const logout = logoutApi();
  await new Promise((resolve) => setTimeout(resolve, 0));
  const logoutBeforeRotation = calls.some(({ url }) => url === "/auth/logout");
  gate.resolve();
  await Promise.all([settledRefresh, logout]);
  expect(logoutBeforeRotation).toBe(false);
  expect(getAccessToken()).toBeNull();
});

test("a refresh started before login cannot overwrite the new session", async () => {
  const gate = deferred();
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/auth/refresh") { calls.push({ url: "refresh-start", authorization: null }); await gate.promise; }
    return adapter(config);
  };
  const refresh = Promise.allSettled([refreshApi()]);
  await waitFor(() => expect(calls.some(({ url }) => url === "refresh-start")).toBe(true));
  const login = loginApi(credentials);
  gate.resolve();
  await Promise.all([refresh, login]);
  expect(getAccessToken()).toBe(LOGIN_TOKEN);
});

test("an old request cannot refresh after logout", async () => {
  const gate = deferred();
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/orders/late") await gate.promise;
    return adapter(config);
  };
  const late = Promise.allSettled([api.get("/orders/late")]);
  await new Promise((resolve) => setTimeout(resolve, 0));
  await logoutApi();
  gate.resolve();
  await late;
  expect(calls.filter(({ url }) => url === "/auth/refresh")).toHaveLength(0);
  expect(getAccessToken()).toBeNull();
});

test.each(["login", "refresh", "logout", "delete"])("%s fails safely without Web Locks before mutating cookies", async (operation) => {
  vi.stubGlobal("navigator", {});
  const actions = { login: () => loginApi(credentials), refresh: refreshApi, logout: logoutApi,
    delete: () => deleteAccountApi({ password: "password123" }) };
  await expect(actions[operation as keyof typeof actions]()).rejects.toThrow(/브라우저/);
  expect(calls).toHaveLength(0);
  expect(getAccessToken()).toBe(OLD_TOKEN);
});

test("account deletion clears the canonical access token", async () => {
  await deleteAccountApi({ password: "password123" });
  expect(getAccessToken()).toBeNull();
});

test("a valid access token can delete the account without requiring a refresh cookie", async () => {
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/auth/refresh") throw failure(config, 401, "REFRESH_TOKEN_NOT_FOUND");
    return adapter(config);
  };
  await expect(deleteAccountApi({ password: "password123" })).resolves.toEqual({ message: "OK" });
  expect(calls.filter(({ url }) => url === "/auth/refresh")).toHaveLength(0);
  expect(getAccessToken()).toBeNull();
});

test("an expired deletion request releases the cookie lock before refreshing and retries once", async () => {
  const adapter = transport.adapter!;
  let attempts = 0;
  transport.adapter = async (config) => {
    if (config.url === "/users/me") {
      attempts++;
      if (config.headers.Authorization === `Bearer ${OLD_TOKEN}`) throw failure(config);
    }
    return adapter(config);
  };
  await deleteAccountApi({ password: "password123" });
  expect(attempts).toBe(2);
  expect(calls.filter(({ url }) => url === "/auth/refresh")).toHaveLength(1);
  expect(getAccessToken()).toBeNull();
});

test("a stalled refresh releases the cookie lock within ten seconds so logout can finish", async () => {
  vi.useFakeTimers();
  let logoutFinished = false;
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/auth/refresh") {
      await new Promise((_, reject) => setTimeout(() => reject(new AxiosError("timeout", "ECONNABORTED", config)), config.timeout || 60_000));
    }
    return adapter(config);
  };
  const refresh = Promise.allSettled([refreshApi()]);
  await vi.advanceTimersByTimeAsync(0);
  const logout = logoutApi().then(() => { logoutFinished = true; });
  await vi.advanceTimersByTimeAsync(10_000);
  const completedOnTime = logoutFinished;
  await vi.advanceTimersByTimeAsync(60_000);
  await Promise.all([refresh, logout]);
  expect(completedOnTime).toBe(true);
  expect(getAccessToken()).toBeNull();
});

test("a stale refresh rejection cannot log out a newer login", async () => {
  const gate = deferred();
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/auth/refresh") {
      calls.push({ url: "refresh-start", authorization: null });
      await gate.promise;
      throw failure(config, 401, "REFRESH_TOKEN_REVOKED");
    }
    return adapter(config);
  };
  const listener = vi.fn();
  window.addEventListener("auth:logout", listener);
  try {
    const refresh = Promise.allSettled([refreshApi()]);
    await waitFor(() => expect(calls.some(({ url }) => url === "refresh-start")).toBe(true));
    const login = loginApi(credentials);
    gate.resolve();
    await Promise.all([refresh, login]);
    expect(getAccessToken()).toBe(LOGIN_TOKEN);
    expect(listener).not.toHaveBeenCalled();
  } finally { window.removeEventListener("auth:logout", listener); }
});

test("a request issued during login cannot retry an old user's action as the new user", async () => {
  const loginGate = deferred(), requestGate = deferred();
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/auth/login") {
      calls.push({ url: "login-start", authorization: null });
      await loginGate.promise;
    }
    if (config.url === "/orders/during-login" && config.headers.Authorization === `Bearer ${OLD_TOKEN}`) await requestGate.promise;
    return adapter(config);
  };
  const login = loginApi(credentials);
  await waitFor(() => expect(calls.some(({ url }) => url === "login-start")).toBe(true));
  const request = Promise.allSettled([api.get("/orders/during-login")]);
  await new Promise((resolve) => setTimeout(resolve, 0));
  loginGate.resolve();
  await login;
  requestGate.resolve();
  const [result] = await request;
  expect(result.status).toBe("rejected");
  expect(calls.filter(({ url }) => url === "/orders/during-login")).toHaveLength(1);
  expect(getAccessToken()).toBe(LOGIN_TOKEN);
});

test("a retry superseded before request dispatch cannot acquire a different user's token", async () => {
  let switched = false;
  const interceptor = api.interceptors.request.use(async (config) => {
    if ((config as typeof config & { _retry?: boolean })._retry && !switched) {
      switched = true;
      await loginApi(credentials);
    }
    return config;
  });
  try {
    const [result] = await Promise.allSettled([api.get("/orders/dispatch-race")]);
    expect(result.status).toBe("rejected");
    expect(calls.filter(({ url }) => url === "/orders/dispatch-race")).toHaveLength(1);
    expect(getAccessToken()).toBe(LOGIN_TOKEN);
  } finally { api.interceptors.request.eject(interceptor); }
});

test("a delayed tab notification cannot let another account's refresh retry the old user's order", async () => {
  const adapter = transport.adapter!;
  transport.adapter = async (config) => config.url === "/auth/refresh"
    ? reply(config, { accessToken: LOGIN_TOKEN, tokenType: "Bearer", expiresIn: 600 }) : adapter(config);
  const listener = vi.fn();
  window.addEventListener("auth:logout", listener);
  try {
    await expect(api.post("/orders", { ticketId: 123, quantity: 1 })).rejects.toBeDefined();
    expect(calls.filter(({ url }) => url === "/orders")).toHaveLength(1);
    expect(getAccessToken()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
  } finally { window.removeEventListener("auth:logout", listener); }
});

test.each(["malformed-token", `header.${btoa('{"sub":"1"}')}.signature`])(
  "refresh rejects an invalid access payload %s rather than retrying", async (accessToken) => {
    const adapter = transport.adapter!;
    transport.adapter = async (config) => config.url === "/auth/refresh"
      ? reply(config, { accessToken, tokenType: "Bearer", expiresIn: 600 }) : adapter(config);
    await expect(api.get("/orders")).rejects.toBeDefined();
    expect(calls.filter(({ url }) => url === "/orders")).toHaveLength(1);
    expect(getAccessToken()).toBeNull();
  });

test("a discarded successful login followed by a failed newer login invalidates the previous account", async () => {
  const gate = deferred();
  const adapter = transport.adapter!;
  transport.adapter = async (config) => {
    if (config.url === "/auth/login") {
      if (JSON.parse(config.data).email === "third@example.com") throw failure(config, 401, "INVALID_CREDENTIALS");
      calls.push({ url: "login-start", authorization: null });
      await gate.promise;
    }
    return adapter(config);
  };
  const first = Promise.allSettled([loginApi(credentials)]);
  await waitFor(() => expect(calls.some(({ url }) => url === "login-start")).toBe(true));
  const newer = Promise.allSettled([loginApi({ ...credentials, email: "third@example.com" })]);
  gate.resolve();
  const [[firstResult], [newerResult]] = await Promise.all([first, newer]);
  expect(firstResult.status).toBe("rejected");
  expect(newerResult.status).toBe("rejected");
  expect(calls.filter(({ url }) => url === "/auth/login")).toHaveLength(1);
  expect(getAccessToken()).toBeNull();
});

test("successful cookie mutations notify other tabs and an incoming change clears local authentication", async () => {
  const messages: unknown[] = [];
  const channel = { receive: null as ((event: { data: unknown }) => void) | null };
  vi.stubGlobal("BroadcastChannel", class {
    set onmessage(handler: (event: { data: unknown }) => void) { channel.receive = handler; }
    postMessage(message: unknown) { messages.push(message); }
  });
  vi.resetModules();
  const freshToken = await import("./token");
  const freshAuth = await import("./auth");
  await freshAuth.loginApi(credentials);
  expect(freshToken.getAccessToken()).toBe(LOGIN_TOKEN);
  await freshAuth.logoutApi();
  freshToken.setAccessToken(OLD_TOKEN);
  await freshAuth.deleteAccountApi({ password: "password123" });
  expect(messages).toEqual(["auth:changed", "auth:changed", "auth:changed"]);
  const listener = vi.fn();
  window.addEventListener("auth:logout", listener);
  try {
    freshToken.setAccessToken(OTHER_TOKEN);
    const before = freshToken.getAuthGeneration();
    channel.receive?.({ data: "auth:changed" });
    expect(freshToken.getAccessToken()).toBeNull();
    expect(freshToken.getAuthGeneration()).toBeGreaterThan(before);
    expect(listener).toHaveBeenCalledTimes(1);
    expect(messages).toHaveLength(3);
  } finally { window.removeEventListener("auth:logout", listener); }
});
