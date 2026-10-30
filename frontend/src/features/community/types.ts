import type { MemberCard } from "@/features/profile/types";

export type PostIntent =
  | "JOB_OPPORTUNITY"
  | "RESEARCH_COLLABORATION"
  | "SEEKING_HELP"
  | "MENTORSHIP"
  | "TECHNICAL_DISCUSSION"
  | "ACHIEVEMENT"
  | "EVENT";
export type PostVisibility = "ALL_MEMBERS" | "CONNECTIONS_ONLY";
export type ContentStatus = "ACTIVE" | "REMOVED" | "DELETED";
export type ReportTargetType = "POST" | "COMMENT" | "USER" | "MESSAGE";
export type ReportReason = "SPAM" | "HARASSMENT" | "INAPPROPRIATE" | "MISINFORMATION" | "IMPERSONATION" | "OTHER";
export type ReportStatus = "OPEN" | "RESOLVED" | "DISMISSED";
export type ReportResolution = "CONTENT_REMOVED" | "NO_ACTION" | "DISMISSED";

export interface PostView {
  id: string;
  author: MemberCard | null;
  intent: PostIntent;
  body: string;
  visibility: PostVisibility;
  hashtags: string[];
  likeCount: number;
  commentCount: number;
  repostCount: number;
  liked: boolean;
  bookmarked: boolean;
  own: boolean;
  status: ContentStatus;
  removedReason: string | null;
  repostOf: PostView | null;
  repostUnavailable: boolean;
  createdAt: string;
  editedAt: string | null;
}

export interface CommentView {
  id: string;
  postId: string;
  parentId: string | null;
  author: MemberCard | null;
  body: string | null;
  status: ContentStatus;
  own: boolean;
  replies: CommentView[];
  createdAt: string;
  editedAt: string | null;
}

export interface TrendingTag {
  tag: string;
  posts: number;
}

export interface ReportView {
  id: string;
  targetType: ReportTargetType;
  targetId: string;
  reporter: MemberCard | null;
  targetOwner: MemberCard | null;
  reason: ReportReason;
  details: string | null;
  contentSnapshot: string | null;
  status: ReportStatus;
  resolution: ReportResolution | null;
  resolutionNote: string | null;
  openReportsOnTarget: number;
  targetPost: PostView | null;
  targetComment: CommentView | null;
  targetMember: MemberCard | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface FeedParams {
  intent?: PostIntent;
  tag?: string;
  q?: string;
  author?: string;
  page?: number;
  size?: number;
}

export interface CreatePostRequest {
  intent: PostIntent;
  body: string;
  visibility: PostVisibility;
  repostOfId?: string;
}

export const INTENT_LABELS: Record<PostIntent, string> = {
  JOB_OPPORTUNITY: "Job opportunity",
  RESEARCH_COLLABORATION: "Research collaboration",
  SEEKING_HELP: "Seeking help",
  MENTORSHIP: "Mentorship",
  TECHNICAL_DISCUSSION: "Technical discussion",
  ACHIEVEMENT: "Achievement",
  EVENT: "Event",
};

export const INTENTS = Object.keys(INTENT_LABELS) as PostIntent[];

export const VISIBILITY_LABELS: Record<PostVisibility, string> = {
  ALL_MEMBERS: "All verified members",
  CONNECTIONS_ONLY: "My connections only",
};

export const REPORT_REASON_LABELS: Record<ReportReason, string> = {
  SPAM: "Spam or advertising",
  HARASSMENT: "Harassment or abuse",
  INAPPROPRIATE: "Inappropriate content",
  MISINFORMATION: "Misleading or false",
  IMPERSONATION: "Impersonation",
  OTHER: "Something else",
};

export const REPORT_STATUS_LABELS: Record<ReportStatus, string> = {
  OPEN: "Open",
  RESOLVED: "Resolved",
  DISMISSED: "Dismissed",
};

export function relativeTime(value: string): string {
  const diff = Date.now() - new Date(value).getTime();
  const minutes = Math.round(diff / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days}d`;
  return new Date(value).toLocaleDateString(undefined, { dateStyle: "medium" });
}
