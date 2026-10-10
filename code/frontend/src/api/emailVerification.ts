import { api } from "./axios";

export async function sendEmailVerification(email: string) {
  const response = await api.post<{ verificationToken: string; expiresIn: number; message: string }>(
    "/auth/email-verifications", { email }, { timeout: 20_000 },
  );
  return response.data;
}

export async function verifyEmail(email: string, verificationToken: string, code: string) {
  const response = await api.post<{ message: string }>("/auth/email-verifications/verify", {
    email, verificationToken, code,
  });
  return response.data;
}
