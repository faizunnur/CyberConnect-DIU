"use client";

import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { useStepUpFresh } from "@/features/auth/components/step-up-banner";
import { useMe } from "@/features/auth/hooks";
import { formatDateTime } from "@/lib/api/types";
import { useAppeal, useAppeals, useDecideAppeal } from "../hooks";

export function AppealQueue() {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useAppeals({ status: status || undefined, page, size: 20 });
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Appeals</h1>
        <p className="mt-1 text-sm text-muted-foreground">An appeal must be decided by a moderator who did not make the original decision.</p>
      </div>
      <label className="flex items-center gap-2 text-sm">
        <span className="text-muted-foreground">Status</span>
        <select className="h-9 rounded-lg border bg-background px-2" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">Open</option>
          <option value="APPROVED">Approved</option>
          <option value="DENIED">Denied</option>
        </select>
      </label>
      {isPending ? <Skeleton className="h-64 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title="No appeals" />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Applicant</TableHead>
                <TableHead>Original reason</TableHead>
                <TableHead>Submitted</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((a) => (
                <TableRow key={a.id}>
                  <TableCell>
                    <Link href={`/admin/appeals/${a.id}`} className="font-medium text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">{a.applicantName}</Link>
                  </TableCell>
                  <TableCell>{a.originalReasonCode?.replace(/_/g, " ") ?? "—"}</TableCell>
                  <TableCell>{formatDateTime(a.createdAt)}</TableCell>
                  <TableCell><Badge variant="secondary">{a.status.replace("_", " ").toLowerCase()}</Badge></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pager page={data.page} onChange={setPage} />
        </>
      )}
    </div>
  );
}

export function AppealReview({ appealId }: { appealId: string }) {
  const { data, isPending, isError, error } = useAppeal(appealId);
  const { data: me } = useMe();
  const fresh = useStepUpFresh();
  const decide = useDecideAppeal();
  const [text, setText] = useState("");
  const [notes, setNotes] = useState("");

  if (isPending) return <Skeleton className="h-80 w-full" />;
  if (isError) return <FormError error={error} title="Could not load the appeal" />;

  const open = data.status === "SUBMITTED" || data.status === "UNDER_REVIEW";
  const conflict = me && (data.originalReviewerId === me.id || data.userId === me.id);

  return (
    <div className="space-y-6">
      <div>
        <Link href="/admin/appeals" className="text-sm text-muted-foreground hover:text-foreground">← Appeals</Link>
        <h1 className="text-2xl font-semibold">Appeal by {data.applicantName}</h1>
        <p className="text-sm text-muted-foreground">
          Submitted {formatDateTime(data.createdAt)} · original decision {data.originalReasonCode?.replace(/_/g, " ") ?? "—"} ·{" "}
          <Link href={`/admin/moderation/${data.submissionId}`} className="text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">open submission</Link>
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Applicant statement</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="whitespace-pre-wrap text-sm">{data.statement}</p>
        </CardContent>
      </Card>

      {!open ? (
        <Card>
          <CardHeader>
            <CardTitle>{data.status === "APPROVED" ? "Approved" : "Denied"} on {formatDateTime(data.decidedAt)}</CardTitle>
            <CardDescription>{data.decisionTextApplicant}</CardDescription>
          </CardHeader>
          {data.internalNotes ? <CardContent className="text-sm text-muted-foreground">Internal: {data.internalNotes}</CardContent> : null}
        </Card>
      ) : conflict ? (
        <Alert variant="destructive">
          <AlertTitle>You cannot decide this appeal</AlertTitle>
          <AlertDescription>You made the original decision or this is your own application. Another moderator must decide.</AlertDescription>
        </Alert>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Decision</CardTitle>
            <CardDescription>A denied appeal is final unless an administrator reopens the case.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <label className="text-sm font-medium" htmlFor="appeal-text">Explanation for the applicant</label>
            <Textarea id="appeal-text" value={text} onChange={(e) => setText(e.target.value)} rows={3} maxLength={2000} />
            <label className="text-sm font-medium" htmlFor="appeal-notes">Internal notes (never shown)</label>
            <Textarea id="appeal-notes" value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} maxLength={4000} />
            <FormError error={decide.error} title="Decision failed" />
            <div className="flex gap-2">
              <Button disabled={!fresh || decide.isPending} onClick={() => decide.mutate({ id: data.id, approved: true, textForApplicant: text || undefined, internalNotes: notes || undefined })}>
                {decide.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Approve appeal
              </Button>
              <Button variant="destructive" disabled={!fresh || decide.isPending} onClick={() => decide.mutate({ id: data.id, approved: false, textForApplicant: text || undefined, internalNotes: notes || undefined })}>
                Deny appeal
              </Button>
            </div>
            {!fresh ? <p className="text-xs text-muted-foreground">Confirm your identity above to decide.</p> : null}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
