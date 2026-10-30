"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toQuery } from "@/features/profile/api";
import type { MemberCard } from "@/features/profile/types";
import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";

export type ImpactType = "JOB_REFERRAL" | "RESEARCH" | "MENTORSHIP";
export type ImpactStatus = "PENDING_CONFIRMATION" | "CONFIRMED" | "DISPUTED" | "REJECTED";
export type ImpactRole = "BENEFICIARY" | "CONTRIBUTOR";

export interface TrailEntry {
  actor: MemberCard | null;
  role: string;
  decision: "PROPOSE" | "CONFIRM" | "DISPUTE" | "WITHDRAW" | "RESOLVE_CONFIRMED" | "RESOLVE_REJECTED";
  note: string | null;
  createdAt: string;
}

export interface ImpactRecord {
  id: string;
  type: ImpactType;
  sourceId: string;
  sourceTitle: string | null;
  beneficiary: MemberCard | null;
  contributor: MemberCard | null;
  proposedBy: string;
  title: string;
  description: string | null;
  status: ImpactStatus;
  beneficiaryConfirmedAt: string | null;
  contributorConfirmedAt: string | null;
  confirmedAt: string | null;
  disputeReason: string | null;
  resolutionNote: string | null;
  myRole: ImpactRole | null;
  awaitingMe: boolean;
  trail: TrailEntry[];
  createdAt: string;
  updatedAt: string;
}

export interface SourceOption {
  type: ImpactType;
  sourceId: string;
  title: string;
  counterpart: MemberCard | null;
  myRole: ImpactRole;
  alreadyRecorded: boolean;
}

export interface CommunityStats {
  confirmed: number;
  byType: Record<ImpactType, number>;
  contributors: number;
  beneficiaries: number;
  pending: number;
  disputed: number;
}

export interface MemberImpact {
  asContributor: number;
  asBeneficiary: number;
  contributedByType: Record<ImpactType, number>;
  recent: ImpactRecord[];
}

export const IMPACT_TYPE_LABELS: Record<ImpactType, string> = {
  JOB_REFERRAL: "Job or internship",
  RESEARCH: "Research collaboration",
  MENTORSHIP: "Mentorship",
};

export const IMPACT_STATUS_LABELS: Record<ImpactStatus, string> = {
  PENDING_CONFIRMATION: "Awaiting confirmation",
  CONFIRMED: "Confirmed",
  DISPUTED: "Disputed",
  REJECTED: "Not confirmed",
};

export const impactApi = {
  mine: (status: ImpactStatus | "", page = 0) =>
    apiFetch<Page<ImpactRecord>>(`/impact${toQuery({ status: status || undefined, page, size: 20 })}`),
  sources: () => apiFetch<SourceOption[]>("/impact/sources"),
  stats: () => apiFetch<CommunityStats>("/impact/stats"),
  get: (id: string) => apiFetch<ImpactRecord>(`/impact/${id}`),
  propose: (body: { type: ImpactType; sourceId: string; counterpartId?: string; title: string; description?: string }) =>
    apiFetch<ImpactRecord>("/impact", { method: "POST", json: body }),
  confirm: (id: string) => apiFetch<ImpactRecord>(`/impact/${id}/confirm`, { method: "POST" }),
  dispute: (id: string, reason: string) => apiFetch<ImpactRecord>(`/impact/${id}/dispute`, { method: "POST", json: { reason } }),
  withdraw: (id: string) => apiFetch<ImpactRecord>(`/impact/${id}/withdraw`, { method: "POST" }),
  member: (userId: string) => apiFetch<MemberImpact>(`/members/${userId}/impact`),
  disputes: (page = 0) => apiFetch<Page<ImpactRecord>>(`/moderation/impact/disputes${toQuery({ page, size: 20 })}`),
  resolve: (id: string, confirmed: boolean, note?: string) =>
    apiFetch<ImpactRecord>(`/moderation/impact/${id}/resolve`, { method: "POST", json: { confirmed, note } }),
};

export function useMyImpact(status: ImpactStatus | "", page: number) {
  return useQuery({ queryKey: ["impact", "mine", status, page], queryFn: () => impactApi.mine(status, page) });
}

export function useImpactSources() {
  return useQuery({ queryKey: ["impact", "sources"], queryFn: impactApi.sources });
}

export function useImpactStats() {
  return useQuery({ queryKey: ["impact", "stats"], queryFn: impactApi.stats, staleTime: 60_000 });
}

export function useMemberImpact(userId: string) {
  return useQuery({ queryKey: ["impact", "member", userId], queryFn: () => impactApi.member(userId), retry: false });
}

export function useImpactDisputes(page: number) {
  return useQuery({ queryKey: ["impact", "disputes", page], queryFn: () => impactApi.disputes(page) });
}

function useRefresh() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["impact"] });
}

export function useProposeImpact() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: impactApi.propose, onSuccess: refresh });
}

export function useImpactAction(kind: "confirm" | "withdraw") {
  const refresh = useRefresh();
  return useMutation({ mutationFn: (id: string) => impactApi[kind](id), onSuccess: refresh });
}

export function useDisputeImpact() {
  const refresh = useRefresh();
  return useMutation({ mutationFn: ({ id, reason }: { id: string; reason: string }) => impactApi.dispute(id, reason), onSuccess: refresh });
}

export function useResolveImpact() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, confirmed, note }: { id: string; confirmed: boolean; note?: string }) => impactApi.resolve(id, confirmed, note),
    onSuccess: refresh,
  });
}
