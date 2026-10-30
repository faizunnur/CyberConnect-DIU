import { API_BASE, apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";
import type { AppealDetail, ModerationActionView, QueueItem, ReasonCodeView, SubmissionDetail } from "./types";

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== "") search.set(k, String(v));
  });
  const s = search.toString();
  return s ? `?${s}` : "";
}

export const moderationApi = {
  queue: (params: { status?: string; assignee?: string; page?: number; size?: number }) =>
    apiFetch<Page<QueueItem>>(`/moderation/verifications${query(params)}`),
  reasonCodes: () => apiFetch<ReasonCodeView[]>("/moderation/verifications/reason-codes"),
  detail: (id: string) => apiFetch<SubmissionDetail>(`/moderation/verifications/${id}`),
  assign: (id: string) => apiFetch<void>(`/moderation/verifications/${id}/assign`, { method: "POST" }),
  approve: (id: string, internalNotes?: string) =>
    apiFetch<void>(`/moderation/verifications/${id}/approve`, { method: "POST", json: { internalNotes } }),
  reject: (id: string, body: { reasonCode: string; reasonText?: string; internalNotes?: string }) =>
    apiFetch<void>(`/moderation/verifications/${id}/reject`, { method: "POST", json: body }),
  /** Same-origin URL for the protected document stream; the browser sends the session cookie. */
  documentUrl: (submissionId: string, documentId: string) =>
    `${API_BASE}/moderation/verifications/${submissionId}/documents/${documentId}/content`,
  appeals: (params: { status?: string; page?: number; size?: number }) =>
    apiFetch<Page<AppealDetail>>(`/moderation/appeals${query(params)}`),
  appeal: (id: string) => apiFetch<AppealDetail>(`/moderation/appeals/${id}`),
  assignAppeal: (id: string) => apiFetch<void>(`/moderation/appeals/${id}/assign`, { method: "POST" }),
  decideAppeal: (id: string, body: { approved: boolean; textForApplicant?: string; internalNotes?: string }) =>
    apiFetch<void>(`/moderation/appeals/${id}/decide`, { method: "POST", json: body }),
  suspend: (userId: string, reason: string, expiresAt?: string) =>
    apiFetch<void>(`/moderation/members/${userId}/suspend`, { method: "POST", json: { reason, expiresAt } }),
  unsuspend: (userId: string, reason: string) =>
    apiFetch<void>(`/moderation/members/${userId}/unsuspend`, { method: "POST", json: { reason } }),
  memberActions: (userId: string) => apiFetch<ModerationActionView[]>(`/moderation/members/${userId}/actions`),
};
