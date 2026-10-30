import type { AffiliationSummary, AffiliationType } from "@/features/auth/types";
import type { AppealStatus, DocumentView, SubmissionStatus } from "@/features/verification/types";

export interface QueueItem {
  id: string;
  userId: string;
  applicantName: string;
  affiliationType: AffiliationType;
  status: SubmissionStatus;
  attemptNumber: number;
  submittedAt: string | null;
  assignedReviewerId: string | null;
  documentCount: number;
}

export interface ReviewView {
  id: string;
  reviewerId: string;
  decision: "APPROVE" | "REJECT";
  reasonCode: string | null;
  reasonTextApplicant: string | null;
  internalNotes: string | null;
  createdAt: string;
}

export interface AppealDetail {
  id: string;
  submissionId: string;
  userId: string;
  applicantName: string;
  status: AppealStatus;
  statement: string;
  assignedReviewerId: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionTextApplicant: string | null;
  internalNotes: string | null;
  createdAt: string;
  originalReviewerId: string | null;
  originalReasonCode: string | null;
}

export interface SubmissionDetail {
  id: string;
  userId: string;
  applicantName: string;
  applicantEmail: string;
  affiliation: AffiliationSummary;
  status: SubmissionStatus;
  attemptNumber: number;
  submittedAt: string | null;
  assignedReviewerId: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  documents: DocumentView[];
  reviews: ReviewView[];
  appeal: AppealDetail | null;
  previousAttempts: number;
}

export interface ReasonCodeView {
  code: string;
  applicantText: string;
  allowsResubmission: boolean;
}

export interface ModerationActionView {
  id: string;
  actorId: string;
  targetUserId: string;
  action: string;
  reason: string;
  expiresAt: string | null;
  createdAt: string;
}
