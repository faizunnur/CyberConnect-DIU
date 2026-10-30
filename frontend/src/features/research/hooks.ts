"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { researchApi } from "./api";
import type { ResearchParams, ResearchRequest, ResearchStatus } from "./types";

export function useResearchProjects(params: ResearchParams) {
  return useQuery({ queryKey: ["research", "search", params], queryFn: () => researchApi.search(params) });
}

export function useMyResearchApplications(page: number) {
  return useQuery({ queryKey: ["research", "applications", page], queryFn: () => researchApi.myApplications(page) });
}

export function useResearchProject(id: string) {
  return useQuery({ queryKey: ["research", "detail", id], queryFn: () => researchApi.get(id), retry: false });
}

function useRefresh() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["research"] });
}

export function useSaveResearch() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: ResearchRequest }) => (id ? researchApi.update(id, body) : researchApi.create(body)),
    onSuccess: refresh,
  });
}

export function useResearchStatus() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: ResearchStatus }) => researchApi.setStatus(id, status),
    onSuccess: refresh,
  });
}

export function useResearchApply() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: ({ id, message }: { id: string; message?: string }) => researchApi.apply(id, message), onSuccess: refresh });
}

export function useResearchAction(kind: "withdraw" | "leave" | "moderatorRestore") {
  const refresh = useRefresh();
  return useMutation({ mutationFn: async (id: string) => { await researchApi[kind](id); }, onSuccess: refresh });
}

export function useResearchDecision() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, applicationId, accept }: { id: string; applicationId: string; accept: boolean }) =>
      accept ? researchApi.accept(id, applicationId) : researchApi.decline(id, applicationId),
    onSuccess: refresh,
  });
}

export function useRemoveResearchMember() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: ({ id, userId }: { id: string; userId: string }) => researchApi.removeMember(id, userId), onSuccess: refresh });
}

export function useModeratorRemoveResearch() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: ({ id, reason }: { id: string; reason: string }) => researchApi.moderatorRemove(id, reason), onSuccess: refresh });
}
