"use client";

import { Award, Loader2, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { formatDate } from "@/lib/api/types";
import {
  IMPACT_STATUS_LABELS,
  IMPACT_TYPE_LABELS,
  useDisputeImpact,
  useImpactAction,
  useImpactSources,
  useImpactStats,
  useMyImpact,
  useProposeImpact,
  type ImpactRecord,
  type ImpactStatus,
  type SourceOption,
} from "../api";

export function ImpactPage() {
  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Impact</h1>
        <p className="mt-1 text-muted-foreground">
          Outcomes that happened through the community: a job or internship, a research collaboration, a mentorship. Both members confirm; only confirmed records count.
        </p>
      </div>
      <CommunityStatsCard />
      <Tabs defaultValue="records">
        <TabsList>
          <TabsTrigger value="records">My records</TabsTrigger>
          <TabsTrigger value="propose">Record an outcome</TabsTrigger>
        </TabsList>
        <TabsContent value="records"><MyRecords /></TabsContent>
        <TabsContent value="propose"><Propose /></TabsContent>
      </Tabs>
    </div>
  );
}

function CommunityStatsCard() {
  const { data } = useImpactStats();
  if (!data) return null;
  return (
    <Card>
      <CardContent className="grid gap-4 sm:grid-cols-4">
        <Stat label="Confirmed outcomes" value={data.confirmed} />
        <Stat label="Jobs and internships" value={data.byType.JOB_REFERRAL} />
        <Stat label="Research collaborations" value={data.byType.RESEARCH} />
        <Stat label="Mentorships" value={data.byType.MENTORSHIP} />
      </CardContent>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <p className="text-2xl font-semibold">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  );
}

export function ImpactRecordCard({ r, moderator, children }: { r: ImpactRecord; moderator?: boolean; children?: React.ReactNode }) {
  return (
    <Card data-testid="impact-record">
      <CardContent className="space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="font-semibold">{r.title}</p>
            <p className="text-xs text-muted-foreground">
              {IMPACT_TYPE_LABELS[r.type]}{r.sourceTitle ? ` · ${r.sourceTitle}` : ""} · proposed {formatDate(r.createdAt)}
            </p>
          </div>
          <Badge variant={r.status === "CONFIRMED" ? "default" : r.status === "DISPUTED" ? "destructive" : "secondary"}>{IMPACT_STATUS_LABELS[r.status]}</Badge>
        </div>
        {r.description ? <p className="text-sm whitespace-pre-line">{r.description}</p> : null}
        <div className="grid gap-2 text-sm sm:grid-cols-2">
          <Party label="Beneficiary" member={r.beneficiary} confirmedAt={r.beneficiaryConfirmedAt} />
          <Party label="Contributor" member={r.contributor} confirmedAt={r.contributorConfirmedAt} />
        </div>
        {r.disputeReason ? <p className="rounded-lg bg-destructive/10 px-3 py-2 text-sm">Disputed: {r.disputeReason}</p> : null}
        {r.resolutionNote ? <p className="rounded-lg bg-muted px-3 py-2 text-sm">Moderator: {r.resolutionNote}</p> : null}
        {moderator ? (
          <ol className="space-y-1 text-xs text-muted-foreground">
            {r.trail.map((t, i) => (
              <li key={i}>{formatDate(t.createdAt)} · {t.actor?.displayName ?? t.role.toLowerCase()} · {t.decision.toLowerCase().replace("_", " ")}{t.note ? ` — ${t.note}` : ""}</li>
            ))}
          </ol>
        ) : null}
        {children}
      </CardContent>
    </Card>
  );
}

function Party({ label, member, confirmedAt }: { label: string; member: ImpactRecord["beneficiary"]; confirmedAt: string | null }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border p-2">
      {member ? <MemberAvatar name={member.displayName} photoUrl={member.photoUrl} size="sm" /> : null}
      <div className="min-w-0">
        <p className="text-xs text-muted-foreground">{label}</p>
        {member ? <Link href={`/members/${member.userId}`} className="font-medium hover:underline">{member.displayName}</Link> : <span>Member</span>}
      </div>
      <span className="ml-auto text-xs text-muted-foreground">{confirmedAt ? <><ShieldCheck className="inline size-3 text-success" aria-hidden="true" /> confirmed</> : "pending"}</span>
    </div>
  );
}

function MyRecords() {
  const [status, setStatus] = useState<ImpactStatus | "">("");
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useMyImpact(status, page);
  const confirm = useImpactAction("confirm");
  const withdraw = useImpactAction("withdraw");
  const dispute = useDisputeImpact();
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const busy = confirm.isPending || withdraw.isPending || dispute.isPending;
  return (
    <div className="space-y-4">
      <label className="flex items-center gap-2 text-sm">
        <span className="text-muted-foreground">Status</span>
        <select className="h-9 rounded-lg border bg-background px-2" value={status} onChange={(e) => { setStatus(e.target.value as ImpactStatus | ""); setPage(0); }}>
          <option value="">All</option>
          {(Object.keys(IMPACT_STATUS_LABELS) as ImpactStatus[]).map((s) => <option key={s} value={s}>{IMPACT_STATUS_LABELS[s]}</option>)}
        </select>
      </label>
      <FormError error={confirm.error ?? withdraw.error ?? dispute.error} />
      {isPending ? <Skeleton className="h-40 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title="No impact records yet" text="Record an outcome from an accepted application, a research project or a completed mentorship." />
      ) : (
        <>
          <div className="space-y-4">
            {data.content.map((r) => (
              <ImpactRecordCard key={r.id} r={r}>
                {r.awaitingMe ? (
                  <div className="space-y-2 rounded-lg border p-3">
                    <p className="text-sm font-medium">Does this match what happened?</p>
                    <div className="flex flex-wrap gap-2">
                      <Button size="sm" disabled={busy} onClick={() => confirm.mutate(r.id)}>Confirm</Button>
                      <Input aria-label="Reason for disputing" placeholder="Reason, if you dispute it" className="min-w-48 flex-1" value={reasons[r.id] ?? ""} onChange={(e) => setReasons({ ...reasons, [r.id]: e.target.value })} />
                      <Button size="sm" variant="outline" disabled={busy || !(reasons[r.id] ?? "").trim()} onClick={() => dispute.mutate({ id: r.id, reason: reasons[r.id] })}>Dispute</Button>
                    </div>
                  </div>
                ) : r.status === "PENDING_CONFIRMATION" ? (
                  <div className="flex items-center justify-between gap-2 text-sm text-muted-foreground">
                    <span>Waiting for the other member to confirm.</span>
                    <Button size="sm" variant="ghost" disabled={busy} onClick={() => withdraw.mutate(r.id)}>Withdraw</Button>
                  </div>
                ) : null}
              </ImpactRecordCard>
            ))}
          </div>
          <Pager page={data.page} onChange={setPage} />
        </>
      )}
    </div>
  );
}

function Propose() {
  const { data, isPending, isError, error } = useImpactSources();
  const propose = useProposeImpact();
  const [selected, setSelected] = useState<SourceOption | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  if (isPending) return <Skeleton className="h-40 w-full" />;
  if (isError) return <FormError error={error} />;
  const available = data.filter((s) => !s.alreadyRecorded);
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Choose what happened</CardTitle>
          <CardDescription>Only on-platform outcomes qualify: an accepted application, an active or completed research project you are part of, or a completed mentorship.</CardDescription>
        </CardHeader>
        <CardContent>
          {available.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nothing to record yet{data.length > 0 ? " (your eligible outcomes already have records)" : ""}.</p>
          ) : (
            <ul className="space-y-2">
              {available.map((s) => {
                const key = `${s.type}:${s.sourceId}:${s.counterpart?.userId}`;
                const active = selected && `${selected.type}:${selected.sourceId}:${selected.counterpart?.userId}` === key;
                return (
                  <li key={key}>
                    <button type="button" aria-pressed={!!active} onClick={() => setSelected(s)} className={`flex w-full items-center gap-3 rounded-lg border p-3 text-left text-sm hover:bg-accent ${active ? "border-primary bg-accent" : ""}`}>
                      <Award className="size-4 shrink-0 text-primary" aria-hidden="true" />
                      <span className="min-w-0 flex-1">
                        <span className="block font-medium">{s.title}</span>
                        <span className="block text-xs text-muted-foreground">{IMPACT_TYPE_LABELS[s.type]} · with {s.counterpart?.displayName ?? "a member"} · you are the {s.myRole.toLowerCase()}</span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </CardContent>
      </Card>
      {selected ? (
        <Card>
          <CardHeader><CardTitle>Describe the outcome</CardTitle></CardHeader>
          <CardContent>
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault();
                propose.mutate(
                  { type: selected.type, sourceId: selected.sourceId, counterpartId: selected.counterpart?.userId, title, description: description || undefined },
                  { onSuccess: () => { setSelected(null); setTitle(""); setDescription(""); } },
                );
              }}
            >
              <div className="space-y-1.5">
                <Label htmlFor="impact-title">Title</Label>
                <Input id="impact-title" maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Hired as SOC intern after the referral" required />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="impact-description">Details (optional)</Label>
                <Textarea id="impact-description" rows={3} maxLength={2000} value={description} onChange={(e) => setDescription(e.target.value)} />
              </div>
              <FormError error={propose.error} />
              <Button type="submit" disabled={propose.isPending || !title.trim()}>
                {propose.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Send for confirmation
              </Button>
            </form>
          </CardContent>
        </Card>
      ) : null}
      {propose.isSuccess ? <FormSuccess title="Sent for confirmation">The other member will be notified.</FormSuccess> : null}
    </div>
  );
}
