import { api } from "./axios";

export async function sendPasswordRecovery(email: string) {
  const response = await api.post<{ verificationToken: string; expiresIn: number; message: string }>(
    "/auth/password-resets/email", { email }, { timeout: 20_000 },
  );
  return response.data;
}

export async function verifyPasswordRecovery(email: string, verificationToken: string, code: string) {
  const response = await api.post<{ message: string }>("/auth/password-resets/email/verify", {
    email, verificationToken, code,
  });
  return response.data;
}

export async function completePasswordRecovery(email: string, verificationToken: string, newPassword: string, confirmPassword: string) {
  const response = await api.post<{ message: string }>("/auth/password-resets", {
    email, verificationToken, newPassword, confirmPassword,
  }, { timeout: 10_000 });
  return response.data;
}
