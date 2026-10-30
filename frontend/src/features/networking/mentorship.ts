"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toQuery } from "@/features/profile/api";
import type { MemberCard } from "@/features/profile/types";
import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";

export type MentorshipStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "COMPLETED" | "CANCELLED";

export interface MentorshipRequestView {
  id: string;
  mentee: MemberCard | null;
  mentor: MemberCard | null;
  status: MentorshipStatus;
  topic: string;
  message: string;
  responseNote: string | null;
  incoming: boolean;
  createdAt: string;
  respondedAt: string | null;
  completedAt: string | null;
}

export const MENTORSHIP_STATUS_LABELS: Record<MentorshipStatus, string> = {
  PENDING: "Pending",
  ACCEPTED: "Active",
  DECLINED: "Declined",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

export const mentorshipApi = {
  list: (role: "mentee" | "mentor", page = 0) =>
    apiFetch<Page<MentorshipRequestView>>(`/mentorship/requests${toQuery({ role, page, size: 20 })}`),
  summary: () => apiFetch<{ pendingAsMentor: number }>("/mentorship/requests/summary"),
  request: (mentorId: string, topic: string, message: string) =>
    apiFetch<MentorshipRequestView>("/mentorship/requests", { method: "POST", json: { mentorId, topic, message } }),
  respond: (id: string, accept: boolean, note?: string) =>
    apiFetch<MentorshipRequestView>(`/mentorship/requests/${id}/${accept ? "accept" : "decline"}`, { method: "POST", json: { note } }),
  complete: (id: string) => apiFetch<MentorshipRequestView>(`/mentorship/requests/${id}/complete`, { method: "POST" }),
  cancel: (id: string) => apiFetch<void>(`/mentorship/requests/${id}`, { method: "DELETE" }),
};

export function useMentorships(role: "mentee" | "mentor", page: number) {
  return useQuery({ queryKey: ["mentorship", role, page], queryFn: () => mentorshipApi.list(role, page) });
}

export function useMentorshipSummary() {
  return useQuery({ queryKey: ["mentorship", "summary"], queryFn: mentorshipApi.summary, staleTime: 30_000 });
}

function useRefresh() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ["mentorship"] });
    queryClient.invalidateQueries({ queryKey: ["members"] });
  };
}

export function useRequestMentorship() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ mentorId, topic, message }: { mentorId: string; topic: string; message: string }) =>
      mentorshipApi.request(mentorId, topic, message),
    onSuccess: refresh,
  });
}

export function useRespondMentorship() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, accept, note }: { id: string; accept: boolean; note?: string }) => mentorshipApi.respond(id, accept, note),
    onSuccess: refresh,
  });
}

export function useMentorshipAction(kind: "complete" | "cancel") {
  const refresh = useRefresh();
  return useMutation({ mutationFn: async (id: string) => { await mentorshipApi[kind](id); }, onSuccess: refresh });
}
