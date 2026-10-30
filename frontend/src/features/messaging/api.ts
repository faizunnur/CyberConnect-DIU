"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toQuery } from "@/features/profile/api";
import type { MemberCard } from "@/features/profile/types";
import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";

export type ConversationState = "ACTIVE" | "REQUEST" | "DECLINED" | "ARCHIVED";
export type Box = "inbox" | "requests" | "archived";

export interface ConversationView {
  id: string;
  other: MemberCard | null;
  state: ConversationState;
  otherState: ConversationState | null;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  lastMessageMine: boolean;
  unreadCount: number;
  canSend: boolean;
  createdAt: string;
}

export interface MessageView {
  id: string;
  conversationId: string;
  senderId: string;
  mine: boolean;
  body: string | null;
  deleted: boolean;
  createdAt: string;
}

export const messagingApi = {
  list: (box: Box, page = 0) => apiFetch<Page<ConversationView>>(`/conversations${toQuery({ box, page, size: 30 })}`),
  summary: () => apiFetch<{ unread: number }>("/conversations/summary"),
  start: (userId: string, body: string) => apiFetch<ConversationView>("/conversations", { method: "POST", json: { userId, body } }),
  get: (id: string) => apiFetch<ConversationView>(`/conversations/${id}`),
  history: (id: string, before?: string) =>
    apiFetch<MessageView[]>(`/conversations/${id}/messages${toQuery({ before, size: 50 })}`),
  send: (id: string, body: string) => apiFetch<MessageView>(`/conversations/${id}/messages`, { method: "POST", json: { body } }),
  read: (id: string) => apiFetch<void>(`/conversations/${id}/read`, { method: "POST" }),
  accept: (id: string) => apiFetch<ConversationView>(`/conversations/${id}/accept`, { method: "POST" }),
  decline: (id: string) => apiFetch<ConversationView>(`/conversations/${id}/decline`, { method: "POST" }),
  archive: (id: string) => apiFetch<ConversationView>(`/conversations/${id}/archive`, { method: "POST" }),
  unarchive: (id: string) => apiFetch<ConversationView>(`/conversations/${id}/unarchive`, { method: "POST" }),
  deleteMessage: (id: string) => apiFetch<void>(`/messages/${id}`, { method: "DELETE" }),
};

export function useConversations(box: Box, page: number) {
  return useQuery({ queryKey: ["messaging", "list", box, page], queryFn: () => messagingApi.list(box, page) });
}

export function useMessagingSummary(enabled = true) {
  return useQuery({ queryKey: ["messaging", "summary"], queryFn: messagingApi.summary, enabled, staleTime: 30_000, refetchInterval: 60_000 });
}

export function useConversation(id: string) {
  return useQuery({ queryKey: ["messaging", "conversation", id], queryFn: () => messagingApi.get(id), retry: false });
}

export function useHistory(id: string) {
  return useQuery({ queryKey: ["messaging", "history", id], queryFn: () => messagingApi.history(id), refetchInterval: 30_000 });
}

function useRefresh() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["messaging"] });
}

export function useStartConversation() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: ({ userId, body }: { userId: string; body: string }) => messagingApi.start(userId, body), onSuccess: refresh });
}

export function useSendMessage() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: ({ id, body }: { id: string; body: string }) => messagingApi.send(id, body), onSuccess: refresh });
}

export function useConversationAction(kind: "read" | "accept" | "decline" | "archive" | "unarchive") {
  const refresh = useRefresh();
  return useMutation({ mutationFn: async (id: string) => { await messagingApi[kind](id); }, onSuccess: refresh });
}

export function useDeleteMessage() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: (id: string) => messagingApi.deleteMessage(id), onSuccess: refresh });
}
