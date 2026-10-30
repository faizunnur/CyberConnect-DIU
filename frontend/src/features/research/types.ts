import type { MemberCard, SkillView } from "@/features/profile/types";

export type ResearchStatus = "PROPOSED" | "ACTIVE" | "COMPLETED" | "ARCHIVED" | "REMOVED";
export type ResearchApplicationStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "WITHDRAWN";
export type ResearchRole = "LEAD" | "MEMBER";

export interface ResearchCard {
  id: string;
  lead: MemberCard | null;
  title: string;
  status: ResearchStatus;
  lookingForMembers: boolean;
  memberCount: number;
  skills: string[];
  own: boolean;
  member: boolean;
  createdAt: string;
}

export interface ResearchMemberView {
  member: MemberCard | null;
  role: ResearchRole;
  joinedAt: string;
}

export interface ResearchApplicationView {
  id: string;
  projectId: string;
  applicant: MemberCard | null;
  status: ResearchApplicationStatus;
  message: string | null;
  createdAt: string;
  decidedAt: string | null;
  project: ResearchCard | null;
}

export interface ResearchProjectView {
  id: string;
  lead: MemberCard | null;
  title: string;
  summary: string;
  status: ResearchStatus;
  lookingForMembers: boolean;
  memberCount: number;
  startDate: string | null;
  endDate: string | null;
  skills: SkillView[];
  members: ResearchMemberView[];
  own: boolean;
  member: boolean;
  canApply: boolean;
  myApplication: ResearchApplicationView | null;
  pendingApplications: ResearchApplicationView[];
  removedReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ResearchRequest {
  title: string;
  summary: string;
  lookingForMembers: boolean;
  startDate?: string;
  endDate?: string;
  skills: string[];
}

export interface ResearchParams {
  q?: string;
  status?: ResearchStatus;
  skill?: string;
  lookingForMembers?: boolean;
  mine?: boolean;
  page?: number;
  size?: number;
}

export const RESEARCH_STATUS_LABELS: Record<ResearchStatus, string> = {
  PROPOSED: "Proposed",
  ACTIVE: "Active",
  COMPLETED: "Completed",
  ARCHIVED: "Archived",
  REMOVED: "Removed",
};
