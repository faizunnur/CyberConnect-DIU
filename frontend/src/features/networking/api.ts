import { toQuery } from "@/features/profile/api";
import type { MemberCard } from "@/features/profile/types";
import { apiFetch } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";
import type { ConnectionRequestView, ConnectionView, NetworkSummary } from "./types";

export const networkingApi = {
  connections: (page = 0, size = 20) =>
    apiFetch<Page<ConnectionView>>(`/connections${toQuery({ page, size })}`),
  summary: () => apiFetch<NetworkSummary>("/connections/summary"),
  requests: (direction: "incoming" | "outgoing", page = 0, size = 20) =>
    apiFetch<Page<ConnectionRequestView>>(`/connections/requests${toQuery({ direction, page, size })}`),
  request: (userId: string, message?: string) =>
    apiFetch<ConnectionRequestView>("/connections/requests", { method: "POST", json: { userId, message } }),
  accept: (requestId: string) => apiFetch<void>(`/connections/requests/${requestId}/accept`, { method: "POST" }),
  decline: (requestId: string) => apiFetch<void>(`/connections/requests/${requestId}/decline`, { method: "POST" }),
  cancel: (requestId: string) => apiFetch<void>(`/connections/requests/${requestId}`, { method: "DELETE" }),
  remove: (userId: string) => apiFetch<void>(`/connections/${userId}`, { method: "DELETE" }),
  blocks: () => apiFetch<MemberCard[]>("/blocks"),
  block: (userId: string) => apiFetch<void>("/blocks", { method: "POST", json: { userId } }),
  unblock: (userId: string) => apiFetch<void>(`/blocks/${userId}`, { method: "DELETE" }),
};
