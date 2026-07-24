export type UserRole = "USER" | "ADMIN" | "SELLER";

export type User = {
  id: number;
  username: string;
  email: string;
  phonenumber: string;
  role: UserRole;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type LoginResponse = {
  message: string;
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: User;
};

export type SignupRequest = {
  username: string;
  password: string;
  email: string;
  phonenumber: string;
};

export type SignupResponse = {
  message: string;
  user: User;
};

export type RefreshResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expirseIn: number;
};

export type MyInfoResponse = {
  user: User;
};

export type UpdateMyInfoRequest = {
  username?: string;
  email?: string;
  phonenumber?: string;
};

export type UpdateMyInfoResponse = {
  message: string;
  user: User;
}

export type ChangePasswordRequest = {
  currentPassword: string;
  newPassword: string;
};

export type MessageResponse = {
  message: string;
};

export type ApiErrorResponse = {
  code: string;
  message: string;
  errors?: Record<string, string>;
};

export type ResetPasswordRequest = {
  currentPassword: string;
  newPassword: string;
}

export type ResetPasswordReponse = {
  code?: string;
  message: string;
}