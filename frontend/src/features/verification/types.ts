import type { AccountStatus, AffiliationSummary, AffiliationType } from "@/features/auth/types";

export type SubmissionStatus = "DRAFT" | "SUBMITTED" | "UNDER_REVIEW" | "APPROVED" | "REJECTED" | "WITHDRAWN";
export type AppealStatus = "SUBMITTED" | "UNDER_REVIEW" | "APPROVED" | "DENIED";

export interface DocumentView {
  id: string;
  documentType: string;
  contentType: string;
  sizeBytes: number;
  originalFilename: string | null;
  scanStatus: "PENDING" | "CLEAN" | "INFECTED" | "ERROR";
  createdAt: string;
  purged: boolean;
}

export interface DecisionView {
  decision: "APPROVE" | "REJECT";
  reasonCode: string | null;
  reasonText: string | null;
  allowsResubmission: boolean;
  decidedAt: string;
}

export interface SubmissionView {
  id: string;
  affiliationId: string;
  affiliationType: AffiliationType | null;
  status: SubmissionStatus;
  attemptNumber: number;
  submittedAt: string | null;
  decidedAt: string | null;
  documents: DocumentView[];
  decision: DecisionView | null;
}

export interface AppealView {
  id: string;
  submissionId: string;
  status: AppealStatus;
  statement: string;
  createdAt: string;
  decidedAt: string | null;
  decisionText: string | null;
}

export interface StatusView {
  accountStatus: AccountStatus;
  emailVerified: boolean;
  verificationLocked: boolean;
  affiliations: AffiliationSummary[];
  currentSubmission: SubmissionView | null;
  history: SubmissionView[];
  appeal: AppealView | null;
  canStartSubmission: boolean;
  canAppeal: boolean;
  attemptsRemaining: number;
  documentCollectionEnabled: boolean;
  maxFileBytes: number;
  maxFilesPerSubmission: number;
  acceptedContentTypes: string[];
}

export const SUBMISSION_STATUS_LABELS: Record<SubmissionStatus, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  UNDER_REVIEW: "Under review",
  APPROVED: "Approved",
  REJECTED: "Not approved",
  WITHDRAWN: "Withdrawn",
};
