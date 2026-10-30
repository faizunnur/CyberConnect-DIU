"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { PostBody } from "@/features/community/components/post-card";
import {
  INTENT_LABELS,
  REPORT_REASON_LABELS,
  REPORT_STATUS_LABELS,
  type ReportResolution,
  type ReportStatus,
  type ReportView,
} from "@/features/community/types";
import { toQuery } from "@/features/profile/api";
import { apiFetch } from "@/lib/api/client";
import { formatDateTime, type Page } from "@/lib/api/types";

const reportsApi = {
  list: (status: ReportStatus | "", page: number) =>
    apiFetch<Page<ReportView>>(`/moderation/reports${toQuery({ status: status || undefined, page, size: 20 })}`),
  get: (id: string) => apiFetch<ReportView>(`/moderation/reports/${id}`),
  resolve: (id: string, resolution: ReportResolution, note?: string) =>
    apiFetch<ReportView>(`/moderation/reports/${id}/resolve`, { method: "POST", json: { resolution, note } }),
  restorePost: (id: string) => apiFetch<void>(`/moderation/posts/${id}/restore`, { method: "POST" }),
  restoreComment: (id: string) => apiFetch<void>(`/moderation/comments/${id}/restore`, { method: "POST" }),
};

export function ReportQueue() {
  const [status, setStatus] = useState<ReportStatus | "">("OPEN");
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useQuery({
    queryKey: ["moderation", "reports", status, page],
    queryFn: () => reportsApi.list(status, page),
  });
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Reports</h1>
        <p className="mt-1 text-sm text-muted-foreground">Member reports on posts, comments and profiles. Oldest open reports first.</p>
      </div>
      <label className="flex items-center gap-2 text-sm">
        <span className="text-muted-foreground">Status</span>
        <select className="h-9 rounded-lg border bg-background px-2" value={status} onChange={(e) => { setStatus(e.target.value as ReportStatus | ""); setPage(0); }}>
          <option value="OPEN">Open</option>
          <option value="RESOLVED">Resolved</option>
          <option value="DISMISSED">Dismissed</option>
          <option value="">All</option>
        </select>
      </label>
      {isPending ? <Skeleton className="h-64 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title="No reports" text="Reports from members appear here." />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Target</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Reported by</TableHead>
                <TableHead>Snapshot</TableHead>
                <TableHead>When</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>
                    <Link href={`/admin/reports/${r.id}`} className="font-medium text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">
                      {r.targetType.toLowerCase()} by {r.targetOwner?.displayName ?? "unknown"}
                    </Link>
                    {r.openReportsOnTarget > 1 ? <span className="ml-1 text-xs text-muted-foreground">({r.openReportsOnTarget} open)</span> : null}
                  </TableCell>
                  <TableCell>{REPORT_REASON_LABELS[r.reason]}</TableCell>
                  <TableCell>{r.reporter?.displayName ?? "—"}</TableCell>
                  <TableCell className="max-w-xs truncate text-muted-foreground">{r.contentSnapshot ?? "—"}</TableCell>
                  <TableCell>{formatDateTime(r.createdAt)}</TableCell>
                  <TableCell><Badge variant={r.status === "OPEN" ? "default" : "secondary"}>{REPORT_STATUS_LABELS[r.status]}</Badge></TableCell>
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

export function ReportDetail({ reportId }: { reportId: string }) {
  const queryClient = useQueryClient();
  const { data, isPending, isError, error } = useQuery({ queryKey: ["moderation", "report", reportId], queryFn: () => reportsApi.get(reportId) });
  const [note, setNote] = useState("");
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["moderation"] });
    queryClient.invalidateQueries({ queryKey: ["community"] });
  };
  const resolve = useMutation({ mutationFn: (resolution: ReportResolution) => reportsApi.resolve(reportId, resolution, note || undefined), onSuccess: refresh });
  const restore = useMutation({
    mutationFn: () => (data?.targetType === "COMMENT" ? reportsApi.restoreComment(data.targetId) : reportsApi.restorePost(data!.targetId)),
    onSuccess: refresh,
  });

  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) return <FormError error={error} title="Could not load the report" />;
  const r = data;
  const removed = (r.targetPost && r.targetPost.status === "REMOVED") || (r.targetComment && r.targetComment.status === "REMOVED");

  return (
    <div className="space-y-6">
      <div>
        <Link href="/admin/reports" className="text-sm text-muted-foreground hover:text-foreground">← Reports</Link>
        <h1 className="text-2xl font-semibold">Report on a {r.targetType.toLowerCase()}</h1>
        <p className="text-sm text-muted-foreground">
          {REPORT_REASON_LABELS[r.reason]} · reported by {r.reporter?.displayName ?? "unknown"} · {formatDateTime(r.createdAt)} ·{" "}
          <Badge variant={r.status === "OPEN" ? "default" : "secondary"}>{REPORT_STATUS_LABELS[r.status]}</Badge>
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Reported content</CardTitle>
            <CardDescription>Snapshot at the time of the report, then the current state.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 text-sm">
            <div>
              <p className="mb-1 text-xs font-medium text-muted-foreground uppercase">Snapshot</p>
              <p className="rounded-lg bg-muted p-3 whitespace-pre-line">{r.contentSnapshot ?? "—"}</p>
            </div>
            {r.details ? (
              <div>
                <p className="mb-1 text-xs font-medium text-muted-foreground uppercase">Reporter’s note</p>
                <p className="whitespace-pre-line">{r.details}</p>
              </div>
            ) : null}
            <div>
              <p className="mb-1 text-xs font-medium text-muted-foreground uppercase">Current state</p>
              {r.targetPost ? (
                <div className="space-y-1 rounded-lg border p-3">
                  <p className="text-xs text-muted-foreground">
                    <Badge variant="secondary">{INTENT_LABELS[r.targetPost.intent]}</Badge> · {r.targetPost.status}
                    {r.targetPost.removedReason ? ` · ${r.targetPost.removedReason}` : ""}
                  </p>
                  <PostBody text={r.targetPost.body} />
                </div>
              ) : r.targetComment ? (
                <div className="space-y-1 rounded-lg border p-3">
                  <p className="text-xs text-muted-foreground">{r.targetComment.status}</p>
                  {r.targetComment.body ? <PostBody text={r.targetComment.body} /> : <p className="text-muted-foreground">(no longer visible)</p>}
                </div>
              ) : r.targetMember ? (
                <p>
                  <Link href={`/admin/members/${r.targetMember.userId}`} className="text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">
                    {r.targetMember.displayName}
                  </Link>{" "}
                  — use member actions (warn, suspend) from the member page.
                </p>
              ) : (
                <p className="text-muted-foreground">The target no longer exists.</p>
              )}
            </div>
            {r.targetOwner ? (
              <p className="text-xs text-muted-foreground">
                Owner: <Link href={`/admin/members/${r.targetOwner.userId}`} className="text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">{r.targetOwner.displayName}</Link>
                {r.openReportsOnTarget > 1 ? ` · ${r.openReportsOnTarget} open reports on this item` : ""}
              </p>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Decision</CardTitle>
            <CardDescription>Removal hides the content for everyone except the author, who sees the reason. It can be restored.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {r.status === "OPEN" ? (
              <>
                <Textarea aria-label="Note" placeholder="Note for the audit trail (shown to the author when content is removed)" rows={3} maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)} />
                <div className="flex flex-wrap gap-2">
                  {r.targetType === "POST" || r.targetType === "COMMENT" ? (
                    <Button variant="destructive" disabled={resolve.isPending} onClick={() => resolve.mutate("CONTENT_REMOVED")}>
                      {resolve.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Remove content
                    </Button>
                  ) : null}
                  <Button variant="outline" disabled={resolve.isPending} onClick={() => resolve.mutate("NO_ACTION")}>Resolve, no action</Button>
                  <Button variant="ghost" disabled={resolve.isPending} onClick={() => resolve.mutate("DISMISSED")}>Dismiss</Button>
                </div>
              </>
            ) : (
              <div className="text-sm">
                <p>
                  <span className="font-medium">{r.resolution?.replace("_", " ").toLowerCase()}</span> · {formatDateTime(r.resolvedAt)}
                </p>
                {r.resolutionNote ? <p className="mt-1 whitespace-pre-line text-muted-foreground">{r.resolutionNote}</p> : null}
                {removed ? (
                  <Button className="mt-3" variant="outline" disabled={restore.isPending} onClick={() => restore.mutate()}>Restore content</Button>
                ) : null}
              </div>
            )}
            <FormError error={resolve.error ?? restore.error} />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
