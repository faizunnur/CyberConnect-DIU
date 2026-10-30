"use client";

import { CheckCircle2, Circle, Clock, FileCheck2, Loader2, MailCheck, ShieldAlert, Trash2, Upload, XCircle } from "lucide-react";
import { useRef, useState } from "react";
import { FormError, FormSuccess, errorMessage } from "@/components/forms/form-alert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useResendVerification } from "@/features/auth/hooks";
import { AFFILIATION_LABELS, STATUS_LABELS } from "@/features/auth/types";
import { formatBytes, formatDateTime } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import {
  useRemoveDocument,
  useStartSubmission,
  useSubmitAppeal,
  useSubmitSubmission,
  useUploadDocument,
  useVerificationStatus,
} from "../hooks";
import { SUBMISSION_STATUS_LABELS, type StatusView, type SubmissionView } from "../types";

export function OnboardingHub() {
  const { data, isPending, isError, error } = useVerificationStatus();

  if (isPending) {
    return (
      <div className="space-y-4" aria-busy="true">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }
  if (isError) return <FormError error={error} title="Could not load your verification status" />;

  return (
    <div className="space-y-6">
      <StatusHeader status={data} />
      <ol className="space-y-4">
        <EmailStep status={data} />
        <EvidenceStep status={data} />
        <DecisionStep status={data} />
      </ol>
      {data.history.length > 1 ? <History status={data} /> : null}
    </div>
  );
}

function StatusHeader({ status }: { status: StatusView }) {
  const tone =
    status.accountStatus === "REJECTED" ? "destructive" : status.accountStatus === "SUSPENDED" ? "secondary" : "default";
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold leading-none tracking-tight">Your application</h1>
            <CardDescription>
              {status.accountStatus === "PENDING"
                ? "Complete the steps below. A moderator reviews your evidence manually."
                : status.accountStatus === "REJECTED"
                  ? "Your last submission was not approved. See the decision and your options below."
                  : status.accountStatus === "SUSPENDED"
                    ? "Your account is suspended. Contact the moderators for details."
                    : "Your account is deactivated. Contact the moderators to restore it."}
            </CardDescription>
          </div>
          <Badge variant={tone}>{STATUS_LABELS[status.accountStatus]}</Badge>
        </div>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">
          Primary affiliation:{" "}
          <span className="font-medium text-foreground">
            {status.affiliations.map((a) => AFFILIATION_LABELS[a.type]).join(", ") || "—"}
          </span>
        </p>
        {!status.documentCollectionEnabled ? (
          <Alert className="mt-4">
            <ShieldAlert aria-hidden="true" />
            <AlertTitle>Document collection is not open yet</AlertTitle>
            <AlertDescription>Uploads are disabled until the institution has approved the verification policy.</AlertDescription>
          </Alert>
        ) : null}
      </CardContent>
    </Card>
  );
}

function StepShell({ index, title, done, active, children }: { index: number; title: string; done: boolean; active: boolean; children: React.ReactNode }) {
  return (
    <li className={cn("rounded-xl border bg-card p-5", active && "border-primary ring-1 ring-primary/30")}>
      <div className="flex items-center gap-3">
        {done ? (
          <CheckCircle2 className="size-5 text-success" aria-hidden="true" />
        ) : active ? (
          <Clock className="size-5 text-primary" aria-hidden="true" />
        ) : (
          <Circle className="size-5 text-muted-foreground" aria-hidden="true" />
        )}
        <h2 className="font-semibold">
          <span className="text-muted-foreground">Step {index} · </span>
          {title}
        </h2>
      </div>
      <div className="mt-4 pl-8">{children}</div>
    </li>
  );
}

function EmailStep({ status }: { status: StatusView }) {
  const resend = useResendVerification();
  return (
    <StepShell index={1} title="Verify your email" done={status.emailVerified} active={!status.emailVerified}>
      {status.emailVerified ? (
        <p className="text-sm text-muted-foreground">Your email address is verified.</p>
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">
            Open the verification link we emailed you. Links expire after 24 hours.
          </p>
          {resend.isSuccess ? <FormSuccess title="Verification email sent" /> : null}
          <FormError error={resend.error} />
          <Button variant="outline" size="sm" onClick={() => resend.mutate()} disabled={resend.isPending}>
            {resend.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <MailCheck aria-hidden="true" />}
            Resend verification email
          </Button>
        </div>
      )}
    </StepShell>
  );
}

function EvidenceStep({ status }: { status: StatusView }) {
  const current = status.currentSubmission;
  const draft = current?.status === "DRAFT" ? current : null;
  const awaiting = current?.status === "SUBMITTED" || current?.status === "UNDER_REVIEW";
  const done = current?.status === "APPROVED" || awaiting;
  const active = status.emailVerified && !done && (status.canStartSubmission || !!draft);
  const pendingAffiliation = status.affiliations.find((a) => a.status === "PENDING");
  const start = useStartSubmission();

  return (
    <StepShell index={2} title="Submit your eligibility evidence" done={done} active={active}>
      {!status.emailVerified ? (
        <p className="text-sm text-muted-foreground">Verify your email first.</p>
      ) : draft ? (
        <DraftEditor status={status} draft={draft} />
      ) : awaiting ? (
        <p className="text-sm text-muted-foreground">
          Submitted {formatDateTime(current?.submittedAt)}. A moderator will review it; you will receive an email with
          the decision.
        </p>
      ) : current?.status === "APPROVED" ? (
        <p className="text-sm text-muted-foreground">Approved. Welcome to the community.</p>
      ) : status.verificationLocked ? (
        <p className="text-sm text-muted-foreground">Verification is closed for this account unless an administrator reopens it.</p>
      ) : status.canStartSubmission && pendingAffiliation ? (
        <div className="space-y-3">
          <p className="text-sm text-muted-foreground">
            {status.attemptsRemaining} attempt{status.attemptsRemaining === 1 ? "" : "s"} remaining. Accepted: JPEG, PNG or PDF,
            up to {formatBytes(status.maxFileBytes)} each, {status.maxFilesPerSubmission} files maximum.
          </p>
          <FormError error={start.error} />
          <Button onClick={() => start.mutate(pendingAffiliation.id)} disabled={start.isPending || !status.documentCollectionEnabled}>
            {start.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <FileCheck2 aria-hidden="true" />}
            {current?.status === "REJECTED" ? "Start a new submission" : "Start submission"}
          </Button>
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">No further submissions are possible for this affiliation.</p>
      )}
    </StepShell>
  );
}

function DraftEditor({ status, draft }: { status: StatusView; draft: SubmissionView }) {
  const upload = useUploadDocument();
  const remove = useRemoveDocument();
  const submit = useSubmitSubmission();
  const fileInput = useRef<HTMLInputElement>(null);
  const [documentType, setDocumentType] = useState("ID_CARD");
  const [localError, setLocalError] = useState<string | null>(null);
  const full = draft.documents.length >= status.maxFilesPerSubmission;

  const onFile = async (file: File | undefined) => {
    setLocalError(null);
    if (!file) return;
    if (!status.acceptedContentTypes.includes(file.type)) {
      setLocalError("Only JPEG, PNG and PDF files are accepted.");
      return;
    }
    if (file.size > status.maxFileBytes) {
      setLocalError(`The file is larger than ${formatBytes(status.maxFileBytes)}.`);
      return;
    }
    try {
      await upload.mutateAsync({ submissionId: draft.id, file, documentType });
    } catch (error) {
      setLocalError(errorMessage(error));
    } finally {
      if (fileInput.current) fileInput.current.value = "";
    }
  };

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Upload a clear photo or scan of your evidence. Files are encrypted and visible only to authorised reviewers.
        <strong className="text-foreground"> Use fictional sample documents during the pilot.</strong>
      </p>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">Document type</span>
          <select
            className="h-9 rounded-lg border bg-background px-3 text-sm"
            value={documentType}
            onChange={(e) => setDocumentType(e.target.value)}
          >
            <option value="ID_CARD">Student / member ID card</option>
            <option value="ALUMNI_CARD">Alumni card</option>
            <option value="CERTIFICATE">Degree certificate</option>
            <option value="TRANSCRIPT">Transcript (first page)</option>
            <option value="LETTER">CSC-issued letter</option>
            <option value="OTHER">Other evidence</option>
          </select>
        </label>
        <input
          ref={fileInput}
          type="file"
          accept="image/jpeg,image/png,application/pdf"
          className="sr-only"
          id="evidence-file"
          onChange={(e) => onFile(e.target.files?.[0])}
          disabled={upload.isPending || full}
        />
        <Button type="button" onClick={() => fileInput.current?.click()} disabled={upload.isPending || full}>
          {upload.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <Upload aria-hidden="true" />}
          {full ? "Maximum files reached" : "Choose file"}
        </Button>
      </div>
      {localError ? <p role="alert" className="text-sm text-destructive">{localError}</p> : null}

      {draft.documents.length ? (
        <ul className="divide-y rounded-lg border">
          {draft.documents.map((doc) => (
            <li key={doc.id} className="flex items-center justify-between gap-3 p-3 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium">{doc.originalFilename ?? doc.documentType}</p>
                <p className="text-xs text-muted-foreground">
                  {doc.documentType.replace(/_/g, " ").toLowerCase()} · {doc.contentType} · {formatBytes(doc.sizeBytes)} · scanned: {doc.scanStatus.toLowerCase()}
                </p>
              </div>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Remove ${doc.originalFilename ?? "document"}`}
                onClick={() => remove.mutate({ submissionId: draft.id, documentId: doc.id })}
                disabled={remove.isPending}
              >
                <Trash2 aria-hidden="true" />
              </Button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground">No files uploaded yet.</p>
      )}

      <FormError error={submit.error} title="Could not submit" />
      <Button onClick={() => submit.mutate(draft.id)} disabled={submit.isPending || draft.documents.length === 0}>
        {submit.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
        Submit for review
      </Button>
    </div>
  );
}

function DecisionStep({ status }: { status: StatusView }) {
  const current = status.currentSubmission;
  const decision = current?.decision ?? null;
  const approved = current?.status === "APPROVED";
  const rejected = current?.status === "REJECTED";
  return (
    <StepShell index={3} title="Moderator decision" done={approved} active={rejected || !!status.appeal}>
      {!current || current.status === "DRAFT" ? (
        <p className="text-sm text-muted-foreground">Decisions usually arrive within a few days of submission.</p>
      ) : approved ? (
        <p className="text-sm text-muted-foreground">Approved on {formatDateTime(current.decidedAt)}.</p>
      ) : rejected && decision ? (
        <div className="space-y-4">
          <Alert variant="destructive">
            <XCircle aria-hidden="true" />
            <AlertTitle>Not approved on {formatDateTime(decision.decidedAt)}</AlertTitle>
            <AlertDescription>{decision.reasonText ?? "The submission could not be approved."}</AlertDescription>
          </Alert>
          {status.appeal ? (
            <AppealStatus status={status} />
          ) : status.canAppeal ? (
            <AppealForm submissionId={current.id} allowsResubmission={decision.allowsResubmission} />
          ) : null}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">Awaiting review.</p>
      )}
    </StepShell>
  );
}

function AppealStatus({ status }: { status: StatusView }) {
  const appeal = status.appeal!;
  return (
    <div className="rounded-lg border p-4 text-sm">
      <p className="font-medium">
        Appeal {appeal.status === "APPROVED" ? "approved" : appeal.status === "DENIED" ? "denied" : "under review"}
      </p>
      <p className="mt-1 text-muted-foreground">Submitted {formatDateTime(appeal.createdAt)}.</p>
      {appeal.decisionText ? <p className="mt-2">{appeal.decisionText}</p> : null}
      {appeal.status === "DENIED" ? (
        <p className="mt-2 text-muted-foreground">This decision is final unless an administrator reopens your case.</p>
      ) : null}
    </div>
  );
}

function AppealForm({ submissionId, allowsResubmission }: { submissionId: string; allowsResubmission: boolean }) {
  const appeal = useSubmitAppeal();
  const [statement, setStatement] = useState("");
  const [open, setOpen] = useState(false);

  if (!open) {
    return (
      <div className="flex flex-wrap items-center gap-3 text-sm">
        {allowsResubmission ? (
          <p className="text-muted-foreground">You can submit new evidence above, or appeal this decision.</p>
        ) : (
          <p className="text-muted-foreground">This decision does not allow resubmission. You may appeal once.</p>
        )}
        <Button variant="outline" size="sm" onClick={() => setOpen(true)}>Appeal the decision</Button>
      </div>
    );
  }
  return (
    <form
      className="space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        appeal.mutate({ submissionId, statement });
      }}
    >
      <label htmlFor="appeal-statement" className="text-sm font-medium">Explain why the decision should be reconsidered</label>
      <Textarea
        id="appeal-statement"
        value={statement}
        onChange={(e) => setStatement(e.target.value)}
        minLength={20}
        maxLength={4000}
        rows={5}
        required
        placeholder="Describe your eligibility and reference the evidence you provided."
      />
      <p className="text-xs text-muted-foreground">{statement.length}/4000 · a different moderator will review your appeal.</p>
      <FormError error={appeal.error} title="Could not submit appeal" />
      <div className="flex gap-2">
        <Button type="submit" disabled={appeal.isPending || statement.trim().length < 20}>
          {appeal.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
          Submit appeal
        </Button>
        <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
      </div>
    </form>
  );
}

function History({ status }: { status: StatusView }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Submission history</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="divide-y text-sm">
          {status.history.map((s) => (
            <li key={s.id} className="flex items-center justify-between py-2">
              <span>Attempt {s.attemptNumber} · {s.affiliationType ? AFFILIATION_LABELS[s.affiliationType] : ""}</span>
              <span className="text-muted-foreground">
                {SUBMISSION_STATUS_LABELS[s.status]} · {formatDateTime(s.decidedAt ?? s.submittedAt)}
              </span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
