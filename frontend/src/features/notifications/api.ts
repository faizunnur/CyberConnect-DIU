"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toQuery } from "@/features/profile/api";
import type { MemberCard } from "@/features/profile/types";
import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";

export type NotificationType =
  | "CONNECTION_REQUEST"
  | "CONNECTION_ACCEPTED"
  | "MENTORSHIP_REQUEST"
  | "MENTORSHIP_RESPONSE"
  | "POST_COMMENT"
  | "COMMENT_REPLY"
  | "APPLICATION_RECEIVED"
  | "APPLICATION_DECISION"
  | "RESEARCH_APPLICATION"
  | "RESEARCH_DECISION"
  | "MESSAGE_REQUEST"
  | "NEW_MESSAGE"
  | "VERIFICATION_DECISION"
  | "CONTENT_REMOVED"
  | "ACCOUNT_NOTICE";

export interface NotificationView {
  id: string;
  type: NotificationType;
  title: string;
  body: string | null;
  link: string | null;
  actor: MemberCard | null;
  readAt: string | null;
  createdAt: string;
}

export interface PreferenceView {
  type: NotificationType;
  inApp: boolean;
  email: boolean;
}

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  CONNECTION_REQUEST: "Connection requests",
  CONNECTION_ACCEPTED: "Accepted connections",
  MENTORSHIP_REQUEST: "Mentorship requests",
  MENTORSHIP_RESPONSE: "Mentorship responses",
  POST_COMMENT: "Comments on my posts",
  COMMENT_REPLY: "Replies to my comments",
  APPLICATION_RECEIVED: "Applications to my opportunities",
  APPLICATION_DECISION: "Decisions on my applications",
  RESEARCH_APPLICATION: "Applications to my research projects",
  RESEARCH_DECISION: "Decisions on my research applications",
  MESSAGE_REQUEST: "Message requests",
  NEW_MESSAGE: "New messages",
  VERIFICATION_DECISION: "Verification decisions (always on)",
  CONTENT_REMOVED: "Moderation notices (always on)",
  ACCOUNT_NOTICE: "Account notices (always on)",
};

export const MANDATORY_TYPES: NotificationType[] = ["VERIFICATION_DECISION", "CONTENT_REMOVED", "ACCOUNT_NOTICE"];

export const notificationsApi = {
  list: (unread: boolean, page = 0, size = 20) =>
    apiFetch<Page<NotificationView>>(`/notifications${toQuery({ unread: unread ? true : undefined, page, size })}`),
  summary: () => apiFetch<{ unread: number }>("/notifications/summary"),
  markRead: (id: string) => apiFetch<void>(`/notifications/${id}/read`, { method: "POST" }),
  markAllRead: () => apiFetch<{ updated: number }>("/notifications/read-all", { method: "POST" }),
  preferences: () => apiFetch<PreferenceView[]>("/notifications/preferences"),
  updatePreferences: (prefs: PreferenceView[]) =>
    apiFetch<PreferenceView[]>("/notifications/preferences", { method: "PUT", json: prefs }),
};

export function useNotifications(unread: boolean, page: number) {
  return useQuery({ queryKey: ["notifications", "list", unread, page], queryFn: () => notificationsApi.list(unread, page) });
}

export function useNotificationSummary(enabled = true) {
  return useQuery({
    queryKey: ["notifications", "summary"],
    queryFn: notificationsApi.summary,
    enabled,
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
}

export function useNotificationPreferences() {
  return useQuery({ queryKey: ["notifications", "preferences"], queryFn: notificationsApi.preferences });
}

function useRefresh() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["notifications"] });
}

export function useMarkRead() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: (id: string) => notificationsApi.markRead(id), onSuccess: refresh });
}

export function useMarkAllRead() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: () => notificationsApi.markAllRead(), onSuccess: refresh });
}

export function useUpdatePreferences() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: (prefs: PreferenceView[]) => notificationsApi.updatePreferences(prefs), onSuccess: refresh });
}
