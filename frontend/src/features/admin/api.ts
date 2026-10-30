import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";
import type { AccountStatus, AffiliationSummary, RoleName } from "@/features/auth/types";
import type { ModerationActionView } from "@/features/moderation/types";

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  accountStatus: AccountStatus;
  emailVerified: boolean;
  roles: RoleName[];
  mfaEnabled: boolean;
  verificationLocked: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  statusChangedAt: string;
}

export interface MemberDetail {
  user: UserSummary;
  affiliations: AffiliationSummary[];
  moderationHistory: ModerationActionView[];
}

export interface SettingView {
  key: string;
  value: string;
  description: string;
  updatedAt: string;
}

export interface AuditRecord {
  id: string;
  actorId: string | null;
  actorRoles: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  outcome: string;
  metadata: string | null;
  ip: string | null;
  createdAt: string;
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== "") search.set(k, String(v));
  });
  const s = search.toString();
  return s ? `?${s}` : "";
}

export const adminApi = {
  members: (params: { q?: string; status?: string; role?: string; page?: number; size?: number }) =>
    apiFetch<Page<UserSummary>>(`/admin/members${query(params)}`),
  member: (id: string) => apiFetch<MemberDetail>(`/admin/members/${id}`),
  replaceRoles: (id: string, roles: RoleName[]) =>
    apiFetch<UserSummary>(`/admin/members/${id}/roles`, { method: "PUT", json: { roles } }),
  deactivate: (id: string, reason: string) =>
    apiFetch<void>(`/admin/members/${id}/deactivate`, { method: "POST", json: { reason } }),
  reactivate: (id: string, reason: string) =>
    apiFetch<void>(`/admin/members/${id}/reactivate`, { method: "POST", json: { reason } }),
  reopenVerification: (id: string, reason: string) =>
    apiFetch<void>(`/admin/members/${id}/reopen-verification`, { method: "POST", json: { reason } }),
  settings: () => apiFetch<SettingView[]>("/admin/settings"),
  updateSetting: (key: string, value: string) =>
    apiFetch<SettingView>(`/admin/settings/${encodeURIComponent(key)}`, { method: "PUT", json: { value } }),
  audit: (params: { targetType?: string; targetId?: string; actorId?: string; action?: string; page?: number; size?: number }) =>
    apiFetch<Page<AuditRecord>>(`/admin/audit${query(params)}`),
};
