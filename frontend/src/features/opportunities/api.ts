import { toQuery } from "@/features/profile/api";
import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";
import type { ApplicationStatus, ApplicationView, OpportunityCard, OpportunityParams, OpportunityRequest, OpportunityView } from "./types";

export const opportunitiesApi = {
  search: (params: OpportunityParams) => apiFetch<Page<OpportunityCard>>(`/opportunities${toQuery({ ...params })}`),
  mine: (page = 0) => apiFetch<Page<OpportunityCard>>(`/opportunities/mine${toQuery({ page, size: 20 })}`),
  myApplications: (page = 0) => apiFetch<Page<ApplicationView>>(`/opportunities/applications${toQuery({ page, size: 20 })}`),
  get: (id: string) => apiFetch<OpportunityView>(`/opportunities/${id}`),
  create: (body: OpportunityRequest) => apiFetch<OpportunityView>("/opportunities", { method: "POST", json: body }),
  update: (id: string, body: OpportunityRequest) => apiFetch<OpportunityView>(`/opportunities/${id}`, { method: "PUT", json: body }),
  deleteDraft: (id: string) => apiFetch<void>(`/opportunities/${id}`, { method: "DELETE" }),
  publish: (id: string) => apiFetch<OpportunityView>(`/opportunities/${id}/publish`, { method: "POST" }),
  close: (id: string) => apiFetch<OpportunityView>(`/opportunities/${id}/close`, { method: "POST" }),
  apply: (id: string, coverNote?: string) =>
    apiFetch<ApplicationView>(`/opportunities/${id}/apply`, { method: "POST", json: { coverNote } }),
  withdraw: (id: string) => apiFetch<void>(`/opportunities/${id}/apply`, { method: "DELETE" }),
  applicants: (id: string, page = 0) =>
    apiFetch<Page<ApplicationView>>(`/opportunities/${id}/applicants${toQuery({ page, size: 50 })}`),
  decide: (id: string, applicationId: string, status: ApplicationStatus) =>
    apiFetch<ApplicationView>(`/opportunities/${id}/applicants/${applicationId}/decide`, { method: "POST", json: { status } }),
  moderatorRemove: (id: string, reason: string) =>
    apiFetch<OpportunityView>(`/moderation/opportunities/${id}/remove`, { method: "POST", json: { reason } }),
  moderatorRestore: (id: string) => apiFetch<OpportunityView>(`/moderation/opportunities/${id}/restore`, { method: "POST" }),
};
