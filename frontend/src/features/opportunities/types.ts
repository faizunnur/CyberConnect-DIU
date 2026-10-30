import type { MemberCard, SkillView } from "@/features/profile/types";

export type OpportunityType = "FULL_TIME" | "PART_TIME" | "INTERNSHIP" | "RESEARCH_ASSISTANT" | "FREELANCE" | "SCHOLARSHIP";
export type OpportunityStatus = "DRAFT" | "OPEN" | "CLOSED" | "EXPIRED" | "REMOVED";
export type ApplicationMethod = "INTERNAL" | "EXTERNAL_URL" | "EMAIL";
export type LocationType = "ONSITE" | "REMOTE" | "HYBRID";
export type ApplicationStatus = "SUBMITTED" | "REVIEWED" | "SHORTLISTED" | "ACCEPTED" | "REJECTED" | "WITHDRAWN";

export interface OpportunityCard {
  id: string;
  author: MemberCard | null;
  type: OpportunityType;
  title: string;
  organization: string;
  locationType: LocationType;
  location: string | null;
  deadline: string | null;
  status: OpportunityStatus;
  applicationMethod: ApplicationMethod;
  skills: string[];
  applicationCount: number;
  own: boolean;
  publishedAt: string | null;
  createdAt: string;
}

export interface ApplicationView {
  id: string;
  opportunityId: string;
  applicant: MemberCard | null;
  status: ApplicationStatus;
  coverNote: string | null;
  createdAt: string;
  decidedAt: string | null;
  opportunity: OpportunityCard | null;
}

export interface OpportunityView {
  id: string;
  author: MemberCard | null;
  type: OpportunityType;
  title: string;
  organization: string;
  locationType: LocationType;
  location: string | null;
  description: string;
  compensation: string | null;
  deadline: string | null;
  status: OpportunityStatus;
  applicationMethod: ApplicationMethod;
  applicationUrl: string | null;
  applicationEmail: string | null;
  skills: SkillView[];
  applicationCount: number;
  own: boolean;
  canApply: boolean;
  myApplication: ApplicationView | null;
  removedReason: string | null;
  publishedAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface OpportunityRequest {
  type: OpportunityType;
  title: string;
  organization: string;
  locationType: LocationType;
  location?: string;
  description: string;
  compensation?: string;
  deadline?: string;
  applicationMethod: ApplicationMethod;
  applicationUrl?: string;
  applicationEmail?: string;
  skills: string[];
  publish: boolean;
}

export interface OpportunityParams {
  q?: string;
  type?: OpportunityType;
  locationType?: LocationType;
  skill?: string;
  author?: string;
  page?: number;
  size?: number;
}

export const OPPORTUNITY_TYPE_LABELS: Record<OpportunityType, string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  INTERNSHIP: "Internship",
  RESEARCH_ASSISTANT: "Research assistant",
  FREELANCE: "Freelance",
  SCHOLARSHIP: "Scholarship",
};

export const OPPORTUNITY_STATUS_LABELS: Record<OpportunityStatus, string> = {
  DRAFT: "Draft",
  OPEN: "Open",
  CLOSED: "Closed",
  EXPIRED: "Expired",
  REMOVED: "Removed",
};

export const LOCATION_LABELS: Record<LocationType, string> = { ONSITE: "On-site", REMOTE: "Remote", HYBRID: "Hybrid" };

export const METHOD_LABELS: Record<ApplicationMethod, string> = {
  INTERNAL: "Apply on CyberConnect",
  EXTERNAL_URL: "Apply on an external site",
  EMAIL: "Apply by email",
};

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  SUBMITTED: "Submitted",
  REVIEWED: "Reviewed",
  SHORTLISTED: "Shortlisted",
  ACCEPTED: "Accepted",
  REJECTED: "Not selected",
  WITHDRAWN: "Withdrawn",
};
