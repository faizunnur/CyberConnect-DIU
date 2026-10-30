import { toQuery } from "@/features/profile/api";
import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";
import type { ResearchApplicationView, ResearchCard, ResearchParams, ResearchProjectView, ResearchRequest, ResearchStatus } from "./types";

export const researchApi = {
  search: (params: ResearchParams) => apiFetch<Page<ResearchCard>>(`/research${toQuery({ ...params })}`),
  myApplications: (page = 0) => apiFetch<Page<ResearchApplicationView>>(`/research/applications${toQuery({ page, size: 20 })}`),
  get: (id: string) => apiFetch<ResearchProjectView>(`/research/${id}`),
  create: (body: ResearchRequest) => apiFetch<ResearchProjectView>("/research", { method: "POST", json: body }),
  update: (id: string, body: ResearchRequest) => apiFetch<ResearchProjectView>(`/research/${id}`, { method: "PUT", json: body }),
  setStatus: (id: string, status: ResearchStatus) =>
    apiFetch<ResearchProjectView>(`/research/${id}/status`, { method: "POST", json: { status } }),
  apply: (id: string, message?: string) =>
    apiFetch<ResearchApplicationView>(`/research/${id}/apply`, { method: "POST", json: { message } }),
  withdraw: (id: string) => apiFetch<void>(`/research/${id}/apply`, { method: "DELETE" }),
  accept: (id: string, applicationId: string) =>
    apiFetch<ResearchProjectView>(`/research/${id}/applications/${applicationId}/accept`, { method: "POST" }),
  decline: (id: string, applicationId: string) =>
    apiFetch<ResearchProjectView>(`/research/${id}/applications/${applicationId}/decline`, { method: "POST" }),
  removeMember: (id: string, userId: string) => apiFetch<ResearchProjectView>(`/research/${id}/members/${userId}`, { method: "DELETE" }),
  leave: (id: string) => apiFetch<void>(`/research/${id}/leave`, { method: "POST" }),
  moderatorRemove: (id: string, reason: string) =>
    apiFetch<ResearchProjectView>(`/moderation/research/${id}/remove`, { method: "POST", json: { reason } }),
  moderatorRestore: (id: string) => apiFetch<ResearchProjectView>(`/moderation/research/${id}/restore`, { method: "POST" }),
};
