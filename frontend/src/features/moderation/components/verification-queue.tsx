"use client";

import Link from "next/link";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useMe } from "@/features/auth/hooks";
import { AFFILIATION_LABELS } from "@/features/auth/types";
import { SUBMISSION_STATUS_LABELS } from "@/features/verification/types";
import { formatDateTime } from "@/lib/api/types";
import { useQueue } from "../hooks";

const STATUS_OPTIONS = [
  { value: "", label: "Awaiting review" },
  { value: "SUBMITTED", label: "Submitted (unassigned)" },
  { value: "UNDER_REVIEW", label: "Under review" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
];

export function VerificationQueue() {
  const { data: me } = useMe();
  const [status, setStatus] = useState("");
  const [mine, setMine] = useState(false);
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useQueue({ status: status || undefined, assignee: mine ? me?.id : undefined, page, size: 20 });

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Verification queue</h1>
        <p className="mt-1 text-sm text-muted-foreground">Oldest submissions first. Assign a case to yourself before deciding it.</p>
      </div>
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <label className="flex items-center gap-2">
          <span className="text-muted-foreground">Status</span>
          <select className="h-9 rounded-lg border bg-background px-2" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            {STATUS_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" className="size-4 accent-primary" checked={mine} onChange={(e) => { setMine(e.target.checked); setPage(0); }} />
          Assigned to me
        </label>
      </div>

      {isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : isError ? (
        <FormError error={error} title="Could not load the queue" />
      ) : data.content.length === 0 ? (
        <EmptyState title="Nothing to review" text="New submissions appear here as soon as applicants submit them." />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Applicant</TableHead>
                <TableHead>Affiliation</TableHead>
                <TableHead>Attempt</TableHead>
                <TableHead>Submitted</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Files</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    <Link href={`/admin/moderation/${item.id}`} className="font-medium text-primary underline-offset-4 hover:underline">
                      {item.applicantName}
                    </Link>
                  </TableCell>
                  <TableCell>{AFFILIATION_LABELS[item.affiliationType]}</TableCell>
                  <TableCell>{item.attemptNumber}</TableCell>
                  <TableCell>{formatDateTime(item.submittedAt)}</TableCell>
                  <TableCell>
                    <Badge variant={item.status === "UNDER_REVIEW" ? "default" : "secondary"}>
                      {SUBMISSION_STATUS_LABELS[item.status]}
                      {item.assignedReviewerId === me?.id ? " · you" : ""}
                    </Badge>
                  </TableCell>
                  <TableCell>{item.documentCount}</TableCell>
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
