import type { MemberCard } from "@/features/profile/types";

export type ConnectionStatus = "PENDING" | "ACCEPTED" | "DECLINED";

export interface ConnectionRequestView {
  id: string;
  member: MemberCard;
  message: string | null;
  status: ConnectionStatus;
  incoming: boolean;
  createdAt: string;
}

export interface ConnectionView {
  id: string;
  member: MemberCard;
  connectedAt: string;
}

export interface NetworkSummary {
  connections: number;
  pendingIncoming: number;
  pendingOutgoing: number;
  blocked: number;
}
