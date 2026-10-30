"use client";

import { Loader2, Pencil, ShieldAlert, Users } from "lucide-react";
import Link from "next/link";
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
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { isApiError } from "@/lib/api/client";
import { formatDate } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import {
  useModeratorRemoveResearch,
  useRemoveResearchMember,
  useResearchAction,
  useResearchApply,
  useResearchDecision,
  useResearchProject,
  useResearchStatus,
} from "../hooks";
import { RESEARCH_STATUS_LABELS, type ResearchProjectView, type ResearchStatus } from "../types";

export function ResearchDetail({ id }: { id: string }) {
  const { data, isPending, isError, error } = useResearchProject(id);
  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) {
    if (isApiError(error) && error.status === 404) {
      return (
        <div className="space-y-2">
          <Link href="/research" className="text-sm text-muted-foreground hover:text-foreground">← Research hub</Link>
          <h1 className="text-2xl font-semibold">Project not found</h1>
        </div>
      );
    }
    return <FormError error={error} title="Could not load the project" />;
  }
  return <Body p={data} />;
}

const STATUSES: ResearchStatus[] = ["PROPOSED", "ACTIVE", "COMPLETED", "ARCHIVED"];

function Body({ p }: { p: ResearchProjectView }) {
  const { data: me } = useMe();
  const setStatus = useResearchStatus();
  const apply = useResearchApply();
  const withdraw = useResearchAction("withdraw");
  const leave = useResearchAction("leave");
  const decide = useResearchDecision();
  const removeMember = useRemoveResearchMember();
  const [message, setMessage] = useState("");
  const canModerate = hasAuthority(me, "MODERATION_ACTION");

  return (
    <div className="space-y-6">
      <Link href="/research" className="text-sm text-muted-foreground hover:text-foreground">← Research hub</Link>
      {p.status === "REMOVED" ? (
        <p className="flex items-center gap-2 rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">
          <ShieldAlert className="size-4" aria-hidden="true" /> Removed by moderation{p.removedReason ? `: ${p.removedReason}` : "."}
        </p>
      ) : null}
      <header className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <h1 className="text-2xl font-semibold">{p.title}</h1>
          <div className="flex flex-wrap gap-1.5">
            <Badge variant="secondary">{RESEARCH_STATUS_LABELS[p.status]}</Badge>
            {p.lookingForMembers ? <Badge variant="outline" className="border-success text-success">Recruiting</Badge> : null}
          </div>
        </div>
        <p className="flex flex-wrap items-center gap-x-3 text-sm text-muted-foreground">
          <span className="inline-flex items-center gap-1"><Users className="size-4" aria-hidden="true" /> {p.memberCount} member{p.memberCount === 1 ? "" : "s"}</span>
          {p.startDate ? <span>{formatDate(p.startDate)}{p.endDate ? ` – ${formatDate(p.endDate)}` : " onwards"}</span> : null}
          {p.lead ? <span>led by <Link href={`/members/${p.lead.userId}`} className="hover:underline">{p.lead.displayName}</Link></span> : null}
        </p>
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="space-y-6">
          <Card>
            <CardHeader><CardTitle>Summary</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              <PostBody text={p.summary} />
              {p.skills.length > 0 ? <div className="flex flex-wrap gap-1.5">{p.skills.map((s) => <Badge key={s.id} variant="outline">{s.name}</Badge>)}</div> : null}
            </CardContent>
          </Card>
          {p.own ? (
            <Card>
              <CardHeader><CardTitle>Applications</CardTitle><CardDescription>Only the lead sees these.</CardDescription></CardHeader>
              <CardContent>
                {p.pendingApplications.length === 0 ? <p className="text-sm text-muted-foreground">No pending applications.</p> : (
                  <ul className="space-y-3">
                    {p.pendingApplications.map((a) => (
                      <li key={a.id} className="space-y-2 rounded-lg border p-3">
                        <div className="flex items-center gap-2 text-sm">
                          {a.applicant ? (
                            <>
                              <MemberAvatar name={a.applicant.displayName} photoUrl={a.applicant.photoUrl} />
                              <Link href={`/members/${a.applicant.userId}`} className="font-medium hover:underline">{a.applicant.displayName}</Link>
                            </>
                          ) : "Member"}
                          <span className="text-xs text-muted-foreground">· {formatDate(a.createdAt)}</span>
                        </div>
                        {a.message ? <p className="text-sm whitespace-pre-line">{a.message}</p> : null}
                        <div className="flex gap-2">
                          <Button size="xs" disabled={decide.isPending} onClick={() => decide.mutate({ id: p.id, applicationId: a.id, accept: true })}>Accept</Button>
                          <Button size="xs" variant="outline" disabled={decide.isPending} onClick={() => decide.mutate({ id: p.id, applicationId: a.id, accept: false })}>Decline</Button>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
                <FormError error={decide.error} />
              </CardContent>
            </Card>
          ) : null}
        </div>
        <div className="space-y-4">
          <Card>
            <CardHeader><CardTitle>Members</CardTitle></CardHeader>
            <CardContent>
              <ul className="space-y-2">
                {p.members.map((m) => (
                  <li key={m.member?.userId ?? m.joinedAt} className="flex items-center justify-between gap-2 text-sm">
                    <div className="flex items-center gap-2">
                      {m.member ? (
                        <>
                          <MemberAvatar name={m.member.displayName} photoUrl={m.member.photoUrl} size="sm" />
                          <Link href={`/members/${m.member.userId}`} className="hover:underline">{m.member.displayName}</Link>
                        </>
                      ) : "Member"}
                      <Badge variant="outline" className="text-[10px]">{m.role.toLowerCase()}</Badge>
                    </div>
                    {p.own && m.role !== "LEAD" && m.member ? (
                      <Button size="xs" variant="ghost" disabled={removeMember.isPending} onClick={() => removeMember.mutate({ id: p.id, userId: m.member!.userId })}>Remove</Button>
                    ) : null}
                  </li>
                ))}
              </ul>
              <FormError error={removeMember.error} />
            </CardContent>
          </Card>
          {p.own ? (
            <Card>
              <CardHeader><CardTitle>Manage</CardTitle></CardHeader>
              <CardContent className="space-y-2">
                {p.status !== "REMOVED" ? <Link href={`/research/${p.id}/edit`} className={cn(buttonVariants({ variant: "outline", size: "sm" }))}><Pencil aria-hidden="true" /> Edit</Link> : null}
                <label className="flex items-center gap-2 text-sm">
                  <span className="text-muted-foreground">Status</span>
                  <select className="h-9 rounded-lg border bg-background px-2" value={p.status} disabled={setStatus.isPending || p.status === "REMOVED"} onChange={(e) => setStatus.mutate({ id: p.id, status: e.target.value as ResearchStatus })}>
                    {STATUSES.map((s) => <option key={s} value={s}>{RESEARCH_STATUS_LABELS[s]}</option>)}
                  </select>
                </label>
                <FormError error={setStatus.error} />
              </CardContent>
            </Card>
          ) : p.member ? (
            <Card>
              <CardContent className="space-y-2">
                <FormSuccess title="You are a member of this project" />
                <Button size="sm" variant="outline" disabled={leave.isPending} onClick={() => leave.mutate(p.id)}>Leave project</Button>
                <FormError error={leave.error} />
              </CardContent>
            </Card>
          ) : p.myApplication ? (
            <Card>
              <CardContent className="space-y-2">
                <FormSuccess title="Application pending">The lead will review it.</FormSuccess>
                <Button size="sm" variant="outline" disabled={withdraw.isPending} onClick={() => withdraw.mutate(p.id)}>Withdraw</Button>
                <FormError error={withdraw.error} />
              </CardContent>
            </Card>
          ) : p.canApply ? (
            <Card>
              <CardHeader><CardTitle>Join this project</CardTitle></CardHeader>
              <CardContent>
                <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); apply.mutate({ id: p.id, message: message || undefined }); }}>
                  <label htmlFor="apply-message" className="text-sm font-medium">Message to the lead (optional)</label>
                  <Textarea id="apply-message" rows={3} maxLength={2000} value={message} onChange={(e) => setMessage(e.target.value)} />
                  <Button type="submit" className="w-full" disabled={apply.isPending}>
                    {apply.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Apply to join
                  </Button>
                  <FormError error={apply.error} />
                </form>
              </CardContent>
            </Card>
          ) : (
            <p className="text-sm text-muted-foreground">This project is not recruiting right now.</p>
          )}
          {canModerate && !p.own ? <ModerationCard p={p} /> : null}
        </div>
      </div>
    </div>
  );
}

function ModerationCard({ p }: { p: ResearchProjectView }) {
  const remove = useModeratorRemoveResearch();
  const restore = useResearchAction("moderatorRestore");
  const [reason, setReason] = useState("");
  return (
    <Card>
      <CardHeader><CardTitle className="text-base">Moderation</CardTitle></CardHeader>
      <CardContent className="space-y-2">
        {p.status === "REMOVED" ? (
          <Button size="sm" variant="outline" disabled={restore.isPending} onClick={() => restore.mutate(p.id)}>Restore project</Button>
        ) : (
          <>
            <Textarea aria-label="Removal reason" rows={2} maxLength={1000} placeholder="Reason shown to the lead" value={reason} onChange={(e) => setReason(e.target.value)} />
            <Button size="sm" variant="destructive" disabled={!reason.trim() || remove.isPending} onClick={() => remove.mutate({ id: p.id, reason })}>Remove project</Button>
          </>
        )}
        <FormError error={remove.error ?? restore.error} />
      </CardContent>
    </Card>
  );
}
