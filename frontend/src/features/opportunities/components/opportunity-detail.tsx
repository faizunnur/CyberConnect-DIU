"use client";

import { Briefcase, CalendarClock, ExternalLink, Loader2, Mail, MapPin, Pencil, ShieldAlert } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useMe } from "@/features/auth/hooks";
import { hasAuthority } from "@/features/auth/routing";
import { PostBody } from "@/features/community/components/post-card";
import { ReportButton } from "@/features/community/components/report-dialog";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { isApiError } from "@/lib/api/client";
import { formatDate, formatDateTime } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { useApplicants, useApply, useDecide, useModeratorRemoveOpportunity, useOpportunity, useOpportunityAction } from "../hooks";
import {
  APPLICATION_STATUS_LABELS,
  LOCATION_LABELS,
  OPPORTUNITY_STATUS_LABELS,
  OPPORTUNITY_TYPE_LABELS,
  type ApplicationStatus,
  type OpportunityView,
} from "../types";

export function OpportunityDetail({ id }: { id: string }) {
  const { data, isPending, isError, error } = useOpportunity(id);
  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) {
    if (isApiError(error) && error.status === 404) {
      return (
        <div className="space-y-2">
          <Link href="/opportunities" className="text-sm text-muted-foreground hover:text-foreground">← Opportunities</Link>
          <h1 className="text-2xl font-semibold">Opportunity not found</h1>
          <p className="text-muted-foreground">It may have been closed, removed or is not available to you.</p>
        </div>
      );
    }
    return <FormError error={error} title="Could not load the opportunity" />;
  }
  return <Body o={data} />;
}

function Body({ o }: { o: OpportunityView }) {
  const router = useRouter();
  const { data: me } = useMe();
  const publish = useOpportunityAction("publish");
  const close = useOpportunityAction("close");
  const deleteDraft = useOpportunityAction("deleteDraft");
  const canModerate = hasAuthority(me, "MODERATION_ACTION");
  const busy = publish.isPending || close.isPending || deleteDraft.isPending;

  return (
    <div className="space-y-6">
      <Link href="/opportunities" className="text-sm text-muted-foreground hover:text-foreground">← Opportunities</Link>
      {o.status !== "OPEN" ? (
        <p className="flex items-center gap-2 rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">
          <ShieldAlert className="size-4 shrink-0" aria-hidden="true" />
          {o.status === "REMOVED" ? `Removed by moderation${o.removedReason ? `: ${o.removedReason}` : "."}` : `This opportunity is ${OPPORTUNITY_STATUS_LABELS[o.status].toLowerCase()}.`}
        </p>
      ) : null}
      <header className="space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold">{o.title}</h1>
            <p className="text-muted-foreground">{o.organization}</p>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <Badge variant="secondary">{OPPORTUNITY_TYPE_LABELS[o.type]}</Badge>
            <Badge variant="outline">{OPPORTUNITY_STATUS_LABELS[o.status]}</Badge>
          </div>
        </div>
        <dl className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
          <div className="flex items-center gap-1"><MapPin className="size-4" aria-hidden="true" /><dd>{LOCATION_LABELS[o.locationType]}{o.location ? ` · ${o.location}` : ""}</dd></div>
          {o.compensation ? <div className="flex items-center gap-1"><Briefcase className="size-4" aria-hidden="true" /><dd>{o.compensation}</dd></div> : null}
          {o.deadline ? <div className="flex items-center gap-1"><CalendarClock className="size-4" aria-hidden="true" /><dd>Deadline {formatDate(o.deadline)}</dd></div> : null}
          {o.publishedAt ? <div><dd>Posted {formatDate(o.publishedAt)}</dd></div> : null}
        </dl>
        {o.author ? (
          <div className="flex items-center gap-2 text-sm">
            <MemberAvatar name={o.author.displayName} photoUrl={o.author.photoUrl} size="sm" />
            <span className="text-muted-foreground">Posted by</span>
            <Link href={`/members/${o.author.userId}`} className="font-medium hover:underline">{o.author.displayName}</Link>
          </div>
        ) : null}
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="space-y-6">
          <Card>
            <CardHeader><CardTitle>About the opportunity</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <PostBody text={o.description} />
              {o.skills.length > 0 ? (
                <div className="flex flex-wrap gap-1.5">{o.skills.map((s) => <Badge key={s.id} variant="outline">{s.name}</Badge>)}</div>
              ) : null}
            </CardContent>
          </Card>
          {o.own ? <Applicants o={o} /> : null}
        </div>
        <div className="space-y-4">
          {o.own ? (
            <Card>
              <CardHeader><CardTitle>Manage</CardTitle><CardDescription>{o.applicationCount} application{o.applicationCount === 1 ? "" : "s"}</CardDescription></CardHeader>
              <CardContent className="flex flex-wrap gap-2">
                {o.status === "DRAFT" || o.status === "OPEN" ? (
                  <Link href={`/opportunities/${o.id}/edit`} className={cn(buttonVariants({ variant: "outline", size: "sm" }))}><Pencil aria-hidden="true" /> Edit</Link>
                ) : null}
                {o.status === "DRAFT" || o.status === "CLOSED" ? (
                  <Button size="sm" disabled={busy} onClick={() => publish.mutate(o.id)}>{o.status === "DRAFT" ? "Publish" : "Reopen"}</Button>
                ) : null}
                {o.status === "OPEN" ? <Button size="sm" variant="outline" disabled={busy} onClick={() => close.mutate(o.id)}>Close</Button> : null}
                {o.status === "DRAFT" ? (
                  <Button size="sm" variant="destructive" disabled={busy} onClick={() => deleteDraft.mutate(o.id, { onSuccess: () => router.push("/opportunities") })}>Delete draft</Button>
                ) : null}
                <div className="basis-full"><FormError error={publish.error ?? close.error ?? deleteDraft.error} /></div>
              </CardContent>
            </Card>
          ) : (
            <ApplyCard o={o} />
          )}
          {canModerate && !o.own ? <ModerationCard o={o} /> : null}
          {!o.own && o.author ? (
            <div className="text-xs text-muted-foreground">
              Something wrong with this listing? <ReportButton targetType="USER" targetId={o.author.userId} label="Report the poster" size="xs" />
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function ApplyCard({ o }: { o: OpportunityView }) {
  const apply = useApply();
  const withdraw = useOpportunityAction("withdraw");
  const [note, setNote] = useState("");
  const mine = o.myApplication;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Apply</CardTitle>
        {o.deadline ? <CardDescription>Deadline {formatDate(o.deadline)}</CardDescription> : null}
      </CardHeader>
      <CardContent className="space-y-3">
        {o.applicationMethod === "EXTERNAL_URL" && o.applicationUrl ? (
          <a href={o.applicationUrl} target="_blank" rel="noopener noreferrer nofollow" className={cn(buttonVariants(), "w-full")}>
            <ExternalLink aria-hidden="true" /> Apply on the external site
          </a>
        ) : o.applicationMethod === "EMAIL" && o.applicationEmail ? (
          <a href={`mailto:${o.applicationEmail}?subject=${encodeURIComponent(`Application: ${o.title}`)}`} className={cn(buttonVariants(), "w-full")}>
            <Mail aria-hidden="true" /> Apply by email
          </a>
        ) : mine && mine.status !== "WITHDRAWN" ? (
          <div className="space-y-2 text-sm">
            <FormSuccess title={`Application ${APPLICATION_STATUS_LABELS[mine.status].toLowerCase()}`}>Submitted {formatDateTime(mine.createdAt)}.</FormSuccess>
            {mine.status === "SUBMITTED" || mine.status === "REVIEWED" || mine.status === "SHORTLISTED" ? (
              <Button variant="outline" size="sm" disabled={withdraw.isPending} onClick={() => withdraw.mutate(o.id)}>Withdraw application</Button>
            ) : null}
            <FormError error={withdraw.error} />
          </div>
        ) : o.canApply ? (
          <form
            className="space-y-2"
            onSubmit={(e) => {
              e.preventDefault();
              apply.mutate({ id: o.id, coverNote: note || undefined });
            }}
          >
            <label htmlFor="cover-note" className="text-sm font-medium">Cover note (optional)</label>
            <Textarea id="cover-note" rows={4} maxLength={3000} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Why you are a good fit." />
            <Button type="submit" className="w-full" disabled={apply.isPending}>
              {apply.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Submit application
            </Button>
            <FormError error={apply.error} />
          </form>
        ) : (
          <p className="text-sm text-muted-foreground">Applications are closed.</p>
        )}
      </CardContent>
    </Card>
  );
}

const DECISIONS: ApplicationStatus[] = ["REVIEWED", "SHORTLISTED", "ACCEPTED", "REJECTED"];

function Applicants({ o }: { o: OpportunityView }) {
  const { data, isPending, isError, error } = useApplicants(o.id, o.own);
  const decide = useDecide();
  return (
    <Card>
      <CardHeader><CardTitle>Applicants</CardTitle><CardDescription>Only you can see this list.</CardDescription></CardHeader>
      <CardContent>
        {isPending ? <Skeleton className="h-24 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
          <p className="text-sm text-muted-foreground">No applications yet.</p>
        ) : (
          <ul className="space-y-3">
            {data.content.map((a) => (
              <li key={a.id} className="space-y-2 rounded-lg border p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    {a.applicant ? (
                      <>
                        <MemberAvatar name={a.applicant.displayName} photoUrl={a.applicant.photoUrl} />
                        <div className="text-sm">
                          <Link href={`/members/${a.applicant.userId}`} className="font-medium hover:underline">{a.applicant.displayName}</Link>
                          <p className="text-xs text-muted-foreground">{a.applicant.headline ?? ""} · applied {formatDate(a.createdAt)}</p>
                        </div>
                      </>
                    ) : <span className="text-sm">Member</span>}
                  </div>
                  <Badge variant={a.status === "ACCEPTED" ? "default" : "secondary"}>{APPLICATION_STATUS_LABELS[a.status]}</Badge>
                </div>
                {a.coverNote ? <p className="text-sm whitespace-pre-line">{a.coverNote}</p> : null}
                {a.status !== "WITHDRAWN" ? (
                  <div className="flex flex-wrap gap-1.5">
                    {DECISIONS.filter((d) => d !== a.status).map((d) => (
                      <Button key={d} size="xs" variant={d === "REJECTED" ? "ghost" : "outline"} disabled={decide.isPending} onClick={() => decide.mutate({ id: o.id, applicationId: a.id, status: d })}>
                        {APPLICATION_STATUS_LABELS[d]}
                      </Button>
                    ))}
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}
        <FormError error={decide.error} />
      </CardContent>
    </Card>
  );
}

function ModerationCard({ o }: { o: OpportunityView }) {
  const remove = useModeratorRemoveOpportunity();
  const restore = useOpportunityAction("moderatorRestore");
  const [reason, setReason] = useState("");
  return (
    <Card>
      <CardHeader><CardTitle className="text-base">Moderation</CardTitle></CardHeader>
      <CardContent className="space-y-2">
        {o.status === "REMOVED" ? (
          <Button size="sm" variant="outline" disabled={restore.isPending} onClick={() => restore.mutate(o.id)}>Restore listing</Button>
        ) : (
          <>
            <Textarea aria-label="Removal reason" rows={2} maxLength={1000} placeholder="Reason shown to the author" value={reason} onChange={(e) => setReason(e.target.value)} />
            <Button size="sm" variant="destructive" disabled={!reason.trim() || remove.isPending} onClick={() => remove.mutate({ id: o.id, reason })}>Remove listing</Button>
          </>
        )}
        <FormError error={remove.error ?? restore.error} />
      </CardContent>
    </Card>
  );
}
