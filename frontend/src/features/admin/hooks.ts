"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { RoleName } from "@/features/auth/types";
import { adminApi } from "./api";

export function useMembers(params: { q?: string; status?: string; role?: string; page?: number; size?: number }) {
  return useQuery({ queryKey: ["admin", "members", params], queryFn: () => adminApi.members(params) });
}

export function useMember(id: string) {
  return useQuery({ queryKey: ["admin", "member", id], queryFn: () => adminApi.member(id) });
}

function useRefresh() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ["admin"] });
    queryClient.invalidateQueries({ queryKey: ["moderation"] });
  };
}

export function useReplaceRoles() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, roles }: { id: string; roles: RoleName[] }) => adminApi.replaceRoles(id, roles),
    onSuccess: refresh,
  });
}

export function useMemberAction(kind: "deactivate" | "reactivate" | "reopenVerification") {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => adminApi[kind](id, reason),
    onSuccess: refresh,
  });
}

export function useSettings() {
  return useQuery({ queryKey: ["admin", "settings"], queryFn: adminApi.settings });
}

export function useUpdateSetting() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) => adminApi.updateSetting(key, value),
    onSuccess: refresh,
  });
}

export function useAudit(params: { targetType?: string; targetId?: string; actorId?: string; action?: string; page?: number; size?: number }) {
  return useQuery({ queryKey: ["admin", "audit", params], queryFn: () => adminApi.audit(params) });
}
