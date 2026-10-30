import type { AffiliationType } from "@/features/auth/types";

export type ProfileVisibility = "ALL_MEMBERS" | "CONNECTIONS_ONLY";
export type FieldVisibility = "NOBODY" | "CONNECTIONS" | "ALL_MEMBERS";
export type MessagingPolicy = "EVERYONE" | "CONNECTIONS_ONLY" | "NOBODY";
export type EmploymentType = "FULL_TIME" | "PART_TIME" | "INTERNSHIP" | "FREELANCE" | "RESEARCH" | "VOLUNTEER";

export interface SkillView {
  id: string;
  name: string;
  slug: string;
}

export interface ExperienceView {
  id: string;
  title: string;
  organization: string;
  employmentType: EmploymentType | null;
  location: string | null;
  startDate: string;
  endDate: string | null;
  current: boolean;
  description: string | null;
}

export interface EducationView {
  id: string;
  institution: string;
  degree: string | null;
  fieldOfStudy: string | null;
  startYear: number | null;
  endYear: number | null;
  description: string | null;
}

export interface PrivacySettings {
  profileVisibility: ProfileVisibility;
  emailVisibility: FieldVisibility;
  organizationVisibility: FieldVisibility;
  locationVisibility: FieldVisibility;
  mentorVisibility: FieldVisibility;
  searchable: boolean;
  messagingPolicy: MessagingPolicy;
}

export interface RelationshipView {
  self: boolean;
  connected: boolean;
  pendingIncoming: boolean;
  pendingOutgoing: boolean;
  requestId: string | null;
}

export interface MemberCard {
  userId: string;
  displayName: string;
  headline: string | null;
  organization: string | null;
  location: string | null;
  mentorAvailable: boolean | null;
  photoUrl: string | null;
  connected: boolean;
  affiliations: AffiliationType[];
  skills: string[];
}

export interface ProfileView {
  userId: string;
  displayName: string;
  headline: string | null;
  bio: string | null;
  organization: string | null;
  jobTitle: string | null;
  location: string | null;
  country: string | null;
  specializations: string[];
  mentorAvailable: boolean | null;
  mentorTopics: string | null;
  photoUrl: string | null;
  email: string | null;
  affiliations: AffiliationType[];
  skills: SkillView[];
  experiences: ExperienceView[];
  educations: EducationView[];
  memberSince: string;
  restricted: boolean;
  relationship: RelationshipView;
  privacy: PrivacySettings | null;
}

export interface UpdateProfileRequest {
  displayName: string;
  headline?: string;
  bio?: string;
  organization?: string;
  jobTitle?: string;
  location?: string;
  country?: string;
  specializations: string[];
  mentorAvailable: boolean;
  mentorTopics?: string;
}

export interface ExperienceRequest {
  title: string;
  organization: string;
  employmentType?: EmploymentType;
  location?: string;
  startDate: string;
  endDate?: string;
  current: boolean;
  description?: string;
}

export interface EducationRequest {
  institution: string;
  degree?: string;
  fieldOfStudy?: string;
  startYear?: number;
  endYear?: number;
  description?: string;
}

export interface DirectoryParams {
  q?: string;
  affiliation?: AffiliationType;
  skill?: string;
  organization?: string;
  experience?: string;
  mentorAvailable?: boolean;
  page?: number;
  size?: number;
}

export const EMPLOYMENT_LABELS: Record<EmploymentType, string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  INTERNSHIP: "Internship",
  FREELANCE: "Freelance",
  RESEARCH: "Research",
  VOLUNTEER: "Volunteer",
};

export const PROFILE_VISIBILITY_LABELS: Record<ProfileVisibility, string> = {
  ALL_MEMBERS: "All verified members",
  CONNECTIONS_ONLY: "My connections only",
};

export const FIELD_VISIBILITY_LABELS: Record<FieldVisibility, string> = {
  ALL_MEMBERS: "All verified members",
  CONNECTIONS: "My connections",
  NOBODY: "Nobody",
};

export const MESSAGING_LABELS: Record<MessagingPolicy, string> = {
  EVERYONE: "Any verified member",
  CONNECTIONS_ONLY: "My connections only",
  NOBODY: "Nobody",
};

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}
