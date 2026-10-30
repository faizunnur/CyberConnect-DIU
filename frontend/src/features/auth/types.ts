export type AffiliationType = "STUDENT" | "ALUMNI" | "CSC_MEMBER";
export type AffiliationStatus = "PENDING" | "VERIFIED" | "REVOKED";
export type AccountStatus = "PENDING" | "VERIFIED" | "REJECTED" | "SUSPENDED" | "DEACTIVATED";
export type RoleName = "MEMBER" | "MODERATOR" | "ADMIN" | "SUPER_ADMIN";

export interface AffiliationSummary {
  id: string;
  type: AffiliationType;
  status: AffiliationStatus;
  primary: boolean;
  studentId: string | null;
  batch: string | null;
  department: string | null;
  graduationYear: number | null;
  cscMemberId: string | null;
  verifiedAt: string | null;
}

export interface Me {
  id: string;
  email: string;
  fullName: string;
  emailVerified: boolean;
  accountStatus: AccountStatus;
  roles: RoleName[];
  authorities: string[];
  mfaEnabled: boolean;
  mfaRequiredForRole: boolean;
  stepUpValidUntil: string | null;
  verificationLocked: boolean;
  affiliations: AffiliationSummary[];
  createdAt: string;
}

export interface LoginResult {
  mfaRequired: boolean;
  user: Me | null;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  affiliationType: AffiliationType;
  studentId?: string;
  batch?: string;
  department?: string;
  graduationYear?: number;
  cscMemberId?: string;
  privacyAccepted: boolean;
}

export interface MfaEnrolment {
  secret: string;
  otpauthUri: string;
}

export interface RecoveryCodes {
  recoveryCodes: string[];
}

export interface StepUpResult {
  validUntil: string;
}

export const AFFILIATION_LABELS: Record<AffiliationType, string> = {
  STUDENT: "Cybersecurity student",
  ALUMNI: "Cybersecurity alumni",
  CSC_MEMBER: "Cyber Security Center member",
};

export const STATUS_LABELS: Record<AccountStatus, string> = {
  PENDING: "Pending verification",
  VERIFIED: "Verified",
  REJECTED: "Not approved",
  SUSPENDED: "Suspended",
  DEACTIVATED: "Deactivated",
};
