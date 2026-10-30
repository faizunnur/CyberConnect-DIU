"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { opportunitiesApi } from "./api";
import type { ApplicationStatus, OpportunityParams, OpportunityRequest } from "./types";

export function useOpportunities(params: OpportunityParams) {
  return useQuery({ queryKey: ["opportunities", "search", params], queryFn: () => opportunitiesApi.search(params) });
}

export function useMyOpportunities(page: number) {
  return useQuery({ queryKey: ["opportunities", "mine", page], queryFn: () => opportunitiesApi.mine(page) });
}

export function useMyApplications(page: number) {
  return useQuery({ queryKey: ["opportunities", "applications", page], queryFn: () => opportunitiesApi.myApplications(page) });
}

export function useOpportunity(id: string) {
  return useQuery({ queryKey: ["opportunities", "detail", id], queryFn: () => opportunitiesApi.get(id), retry: false });
}

export function useApplicants(id: string, enabled: boolean) {
  return useQuery({ queryKey: ["opportunities", "applicants", id], queryFn: () => opportunitiesApi.applicants(id), enabled });
}

function useRefresh() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["opportunities"] });
}

export function useSaveOpportunity() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: OpportunityRequest }) =>
      id ? opportunitiesApi.update(id, body) : opportunitiesApi.create(body),
    onSuccess: refresh,
  });
}

export function useOpportunityAction(kind: "publish" | "close" | "deleteDraft" | "withdraw" | "moderatorRestore") {
  const refresh = useRefresh();
  return useMutation({ mutationFn: async (id: string) => { await opportunitiesApi[kind](id); }, onSuccess: refresh });
}

export function useApply() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, coverNote }: { id: string; coverNote?: string }) => opportunitiesApi.apply(id, coverNote),
    onSuccess: refresh,
  });
}

export function useDecide() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, applicationId, status }: { id: string; applicationId: string; status: ApplicationStatus }) =>
      opportunitiesApi.decide(id, applicationId, status),
    onSuccess: refresh,
  });
}

export function useModeratorRemoveOpportunity() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => opportunitiesApi.moderatorRemove(id, reason),
    onSuccess: refresh,
  });
}
