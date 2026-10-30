"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ME_QUERY_KEY } from "@/features/auth/hooks";
import { moderationApi } from "./api";

export const queueKey = (params: object) => ["moderation", "queue", params] as const;
export const submissionKey = (id: string) => ["moderation", "submission", id] as const;
export const appealsKey = (params: object) => ["moderation", "appeals", params] as const;
export const appealKey = (id: string) => ["moderation", "appeal", id] as const;

export function useQueue(params: { status?: string; assignee?: string; page?: number; size?: number }) {
  return useQuery({ queryKey: queueKey(params), queryFn: () => moderationApi.queue(params) });
}

export function useReasonCodes() {
  return useQuery({ queryKey: ["moderation", "reason-codes"], queryFn: moderationApi.reasonCodes, staleTime: 600_000 });
}

export function useSubmission(id: string) {
  return useQuery({ queryKey: submissionKey(id), queryFn: () => moderationApi.detail(id) });
}

function useRefresh() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ["moderation"] });
    queryClient.invalidateQueries({ queryKey: ["admin"] });
    queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
  };
}

export function useAssign() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: (id: string) => moderationApi.assign(id), onSuccess: refresh });
}

export function useApprove() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, internalNotes }: { id: string; internalNotes?: string }) => moderationApi.approve(id, internalNotes),
    onSuccess: refresh,
  });
}

export function useReject() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, ...body }: { id: string; reasonCode: string; reasonText?: string; internalNotes?: string }) =>
      moderationApi.reject(id, body),
    onSuccess: refresh,
  });
}

export function useAppeals(params: { status?: string; page?: number; size?: number }) {
  return useQuery({ queryKey: appealsKey(params), queryFn: () => moderationApi.appeals(params) });
}

export function useAppeal(id: string) {
  return useQuery({ queryKey: appealKey(id), queryFn: () => moderationApi.appeal(id) });
}

export function useDecideAppeal() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, ...body }: { id: string; approved: boolean; textForApplicant?: string; internalNotes?: string }) =>
      moderationApi.decideAppeal(id, body),
    onSuccess: refresh,
  });
}

export function useSuspend() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ userId, reason, expiresAt }: { userId: string; reason: string; expiresAt?: string }) =>
      moderationApi.suspend(userId, reason, expiresAt),
    onSuccess: refresh,
  });
}

export function useUnsuspend() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ userId, reason }: { userId: string; reason: string }) => moderationApi.unsuspend(userId, reason),
    onSuccess: refresh,
  });
}
