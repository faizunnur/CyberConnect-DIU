"use client";

import { Eye, Loader2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useStepUpFresh } from "@/features/auth/components/step-up-banner";
import { useMe } from "@/features/auth/hooks";
import { AFFILIATION_LABELS } from "@/features/auth/types";
import type { DocumentView } from "@/features/verification/types";
import { SUBMISSION_STATUS_LABELS } from "@/features/verification/types";
import { formatBytes, formatDateTime } from "@/lib/api/types";
import { moderationApi } from "../api";
import { useApprove, useAssign, useReasonCodes, useReject, useSubmission } from "../hooks";
import type { SubmissionDetail } from "../types";

export function SubmissionReview({ submissionId }: { submissionId: string }) {
  const { data, isPending, isError, error } = useSubmission(submissionId);
  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) return <FormError error={error} title="Could not load the submission" />;
  return (
    <div className="space-y-6">
      <Header detail={data} />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-6">
          <ApplicantCard detail={data} />
          <DocumentsCard detail={data} />
          <HistoryCard detail={data} />
        </div>
        <DecisionPanel detail={data} />
      </div>
    </div>
  );
}

function Header({ detail }: { detail: SubmissionDetail }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <Link href="/admin/moderation" className="text-sm text-muted-foreground hover:text-foreground">← Queue</Link>
        <h1 className="text-2xl font-semibold">{detail.applicantName}</h1>
        <p className="text-sm text-muted-foreground">
          {AFFILIATION_LABELS[detail.affiliation.type]} · attempt {detail.attemptNumber}
          {detail.previousAttempts > 0 ? ` (${detail.previousAttempts} earlier)` : ""} · submitted {formatDateTime(detail.submittedAt)}
        </p>
      </div>
      <Badge variant={detail.status === "APPROVED" ? "default" : detail.status === "REJECTED" ? "destructive" : "secondary"}>
        {SUBMISSION_STATUS_LABELS[detail.status]}
      </Badge>
    </div>
  );
}

function ApplicantCard({ detail }: { detail: SubmissionDetail }) {
  const a = detail.affiliation;
  const rows: [string, string | number | null][] = [
    ["Email", detail.applicantEmail],
    ["Affiliation", AFFILIATION_LABELS[a.type]],
    ["Student ID", a.studentId],
    ["Batch", a.batch],
    ["Department", a.department],
    ["Graduation year", a.graduationYear],
    ["CSC member ID", a.cscMemberId],
  ];
  return (
    <Card>
      <CardHeader>
        <CardTitle>Applicant-entered details</CardTitle>
        <CardDescription>Compare these with the document before deciding.</CardDescription>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
          {rows.filter(([, v]) => v !== null && v !== undefined && v !== "").map(([k, v]) => (
            <div key={k}>
              <dt className="text-xs uppercase tracking-wide text-muted-foreground">{k}</dt>
              <dd className="font-medium">{String(v)}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  );
}

function DocumentsCard({ detail }: { detail: SubmissionDetail }) {
  const fresh = useStepUpFresh();
  const [open, setOpen] = useState<DocumentView | null>(null);
  return (
    <Card>
      <CardHeader>
        <CardTitle>Documents</CardTitle>
        <CardDescription>Decrypted on demand and streamed to you only. Every view is logged.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <ul className="divide-y rounded-lg border text-sm">
          {detail.documents.map((doc) => (
            <li key={doc.id} className="flex flex-wrap items-center justify-between gap-2 p-3">
              <div>
                <p className="font-medium">{doc.documentType.replace(/_/g, " ").toLowerCase()}</p>
                <p className="text-xs text-muted-foreground">
                  {doc.contentType} · {formatBytes(doc.sizeBytes)} · scan {doc.scanStatus.toLowerCase()} · {formatDateTime(doc.createdAt)}
                  {doc.purged ? " · purged under retention policy" : ""}
                </p>
              </div>
              <Button variant="outline" size="sm" disabled={!fresh || doc.purged} onClick={() => setOpen(open?.id === doc.id ? null : doc)}>
                <Eye aria-hidden="true" /> {open?.id === doc.id ? "Hide" : "View"}
              </Button>
            </li>
          ))}
        </ul>
        {!fresh ? <p className="text-xs text-muted-foreground">Confirm your identity above to view documents.</p> : null}
        {open && fresh ? (
          <div className="rounded-lg border bg-muted/30 p-2">
            {open.contentType === "application/pdf" ? (
              <iframe
                title="Submitted document"
                src={moderationApi.documentUrl(detail.id, open.id)}
                className="h-[70vh] w-full rounded"
              />
            ) : (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={moderationApi.documentUrl(detail.id, open.id)}
                alt="Submitted evidence document"
                className="mx-auto max-h-[70vh] w-auto rounded"
              />
            )}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function HistoryCard({ detail }: { detail: SubmissionDetail }) {
  if (detail.reviews.length === 0 && !detail.appeal) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Review history</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        {detail.reviews.map((r) => (
          <div key={r.id} className="rounded-lg border p-3">
            <p className="font-medium">
              {r.decision === "APPROVE" ? "Approved" : "Rejected"}{r.reasonCode ? ` · ${r.reasonCode}` : ""} · {formatDateTime(r.createdAt)}
            </p>
            {r.reasonTextApplicant ? <p className="mt-1">Applicant text: {r.reasonTextApplicant}</p> : null}
            {r.internalNotes ? <p className="mt-1 text-muted-foreground">Internal: {r.internalNotes}</p> : null}
          </div>
        ))}
        {detail.appeal ? (
          <p>
            Appeal {detail.appeal.status.toLowerCase().replace("_", " ")}:{" "}
            <Link href={`/admin/appeals/${detail.appeal.id}`} className="text-primary underline-offset-4 hover:underline">open appeal</Link>
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

function DecisionPanel({ detail }: { detail: SubmissionDetail }) {
  const { data: me } = useMe();
  const fresh = useStepUpFresh();
  const assign = useAssign();
  const approve = useApprove();
  const reject = useReject();
  const reasonCodes = useReasonCodes();
  const [mode, setMode] = useState<"approve" | "reject" | null>(null);
  const [reasonCode, setReasonCode] = useState("");
  const [reasonText, setReasonText] = useState("");
  const [notes, setNotes] = useState("");

  const reviewable = detail.status === "SUBMITTED" || detail.status === "UNDER_REVIEW";
  const isSelf = me?.id === detail.userId;
  const assignedToMe = detail.assignedReviewerId === me?.id;

  if (!reviewable) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Decision</CardTitle>
          <CardDescription>
            {SUBMISSION_STATUS_LABELS[detail.status]} on {formatDateTime(detail.decidedAt)}.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }
  if (isSelf) {
    return (
      <Alert variant="destructive">
        <AlertTitle>This is your own application</AlertTitle>
        <AlertDescription>Separation of duties: another moderator must review it.</AlertDescription>
      </Alert>
    );
  }

  return (
    <Card className="self-start lg:sticky lg:top-20">
      <CardHeader>
        <CardTitle>Decision</CardTitle>
        <CardDescription>
          {assignedToMe ? "Assigned to you." : detail.assignedReviewerId ? "Assigned to another moderator." : "Unassigned."}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {!assignedToMe ? (
          <Button variant="outline" className="w-full" onClick={() => assign.mutate(detail.id)} disabled={assign.isPending}>
            {assign.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
            Assign to me
          </Button>
        ) : null}
        <FormError error={assign.error ?? approve.error ?? reject.error} title="Action failed" />

        {mode === null ? (
          <div className="grid grid-cols-2 gap-2">
            <Button onClick={() => setMode("approve")} disabled={!fresh}>Approve…</Button>
            <Button variant="destructive" onClick={() => setMode("reject")} disabled={!fresh}>Reject…</Button>
          </div>
        ) : null}

        {mode === "approve" ? (
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); approve.mutate({ id: detail.id, internalNotes: notes || undefined }); }}>
            <label className="text-sm font-medium" htmlFor="approve-notes">Internal notes (optional, never shown to the applicant)</label>
            <Textarea id="approve-notes" value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} maxLength={4000} />
            <div className="flex gap-2">
              <Button type="submit" disabled={approve.isPending || !fresh}>
                {approve.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
                Confirm approval
              </Button>
              <Button type="button" variant="ghost" onClick={() => setMode(null)}>Cancel</Button>
            </div>
          </form>
        ) : null}

        {mode === "reject" ? (
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); reject.mutate({ id: detail.id, reasonCode, reasonText: reasonText || undefined, internalNotes: notes || undefined }); }}>
            <label className="text-sm font-medium" htmlFor="reason-code">Reason</label>
            <select id="reason-code" className="h-9 w-full rounded-lg border bg-background px-2 text-sm" value={reasonCode} onChange={(e) => setReasonCode(e.target.value)} required>
              <option value="">Choose a reason…</option>
              {reasonCodes.data?.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.code.replace(/_/g, " ")}{c.allowsResubmission ? "" : " (no resubmission)"}
                </option>
              ))}
            </select>
            {reasonCode ? (
              <p className="text-xs text-muted-foreground">
                Applicant will read: “{reasonCodes.data?.find((c) => c.code === reasonCode)?.applicantText}”
              </p>
            ) : null}
            <label className="text-sm font-medium" htmlFor="reason-text">Custom explanation for the applicant (optional)</label>
            <Textarea id="reason-text" value={reasonText} onChange={(e) => setReasonText(e.target.value)} rows={3} maxLength={2000} />
            <label className="text-sm font-medium" htmlFor="reject-notes">Internal notes (never shown)</label>
            <Textarea id="reject-notes" value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} maxLength={4000} />
            <div className="flex gap-2">
              <Button type="submit" variant="destructive" disabled={reject.isPending || !reasonCode || !fresh}>
                {reject.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
                Confirm rejection
              </Button>
              <Button type="button" variant="ghost" onClick={() => setMode(null)}>Cancel</Button>
            </div>
          </form>
        ) : null}
      </CardContent>
    </Card>
  );
}
