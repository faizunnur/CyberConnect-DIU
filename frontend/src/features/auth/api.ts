import { apiFetch } from "@/lib/api/client";
import type { LoginResult, Me, MfaEnrolment, RecoveryCodes, RegisterRequest, StepUpResult } from "./types";

export const authApi = {
  me: () => apiFetch<Me>("/auth/me"),
  register: (body: RegisterRequest) => apiFetch<void>("/auth/register", { method: "POST", json: body }),
  login: (email: string, password: string) =>
    apiFetch<LoginResult>("/auth/login", { method: "POST", json: { email, password } }),
  loginMfa: (code: string) => apiFetch<LoginResult>("/auth/login/mfa", { method: "POST", json: { code } }),
  logout: () => apiFetch<void>("/auth/logout", { method: "POST" }),
  verifyEmail: (token: string) => apiFetch<void>("/auth/verify-email", { method: "POST", json: { token } }),
  resendVerification: () => apiFetch<void>("/auth/resend-verification", { method: "POST" }),
  forgotPassword: (email: string) => apiFetch<void>("/auth/forgot-password", { method: "POST", json: { email } }),
  resetPassword: (token: string, newPassword: string) =>
    apiFetch<void>("/auth/reset-password", { method: "POST", json: { token, newPassword } }),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiFetch<void>("/auth/change-password", { method: "POST", json: { currentPassword, newPassword } }),
  mfaEnrol: () => apiFetch<MfaEnrolment>("/mfa/enrol", { method: "POST" }),
  mfaConfirm: (code: string) => apiFetch<RecoveryCodes>("/mfa/enrol/confirm", { method: "POST", json: { code } }),
  mfaStepUp: (code: string) => apiFetch<StepUpResult>("/mfa/step-up", { method: "POST", json: { code } }),
  mfaRegenerateRecovery: (code: string) =>
    apiFetch<RecoveryCodes>("/mfa/recovery-codes/regenerate", { method: "POST", json: { code } }),
  mfaDisable: (code: string) => apiFetch<void>("/mfa/disable", { method: "POST", json: { code } }),
};
