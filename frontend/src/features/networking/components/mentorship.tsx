"use client";

import { GraduationCap, Loader2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { formatDate } from "@/lib/api/types";
import {
  MENTORSHIP_STATUS_LABELS,
  useMentorshipAction,
  useMentorships,
  useRequestMentorship,
  useRespondMentorship,
  type MentorshipRequestView,
} from "../mentorship";

/** "Request mentorship" control for a member page; shown only when the profile advertises availability. */
export function RequestMentorshipButton({ mentorId, mentorName }: { mentorId: string; mentorName: string }) {
  const [open, setOpen] = useState(false);
  const [topic, setTopic] = useState("");
  const [message, setMessage] = useState("");
  const request = useRequestMentorship();
  return (
    <>
      <Button size="sm" variant="outline" onClick={() => { request.reset(); setOpen(true); }}>
        <GraduationCap aria-hidden="true" /> Request mentorship
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Ask {mentorName} for mentorship</DialogTitle>
            <DialogDescription>Say what you would like help with. They can accept or decline, and either of you can mark it complete later.</DialogDescription>
          </DialogHeader>
          {request.isSuccess ? (
            <FormSuccess title="Request sent">You will find it under Connections → Mentorship.</FormSuccess>
          ) : (
            <form
              id="mentorship-form"
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault();
                request.mutate({ mentorId, topic, message });
              }}
            >
              <div className="space-y-1.5">
                <Label htmlFor="mentorship-topic">Topic</Label>
                <Input id="mentorship-topic" maxLength={160} value={topic} onChange={(e) => setTopic(e.target.value)} placeholder="e.g. Breaking into DFIR" required />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="mentorship-message">Message</Label>
                <Textarea id="mentorship-message" rows={4} maxLength={2000} value={message} onChange={(e) => setMessage(e.target.value)} required />
              </div>
              <FormError error={request.error} />
            </form>
          )}
          <DialogFooter>
            {request.isSuccess ? <Button type="button" onClick={() => setOpen(false)}>Close</Button> : (
              <>
                <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
                <Button type="submit" form="mentorship-form" disabled={request.isPending || !topic.trim() || !message.trim()}>
                  {request.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Send request
                </Button>
              </>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

export function MentorshipPanel() {
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <MentorshipList role="mentee" title="Mentors I asked" />
      <MentorshipList role="mentor" title="Members asking me" />
    </div>
  );
}

function MentorshipList({ role, title }: { role: "mentee" | "mentor"; title: string }) {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useMentorships(role, page);
  const respond = useRespondMentorship();
  const complete = useMentorshipAction("complete");
  const cancel = useMentorshipAction("cancel");
  const busy = respond.isPending || complete.isPending || cancel.isPending;
  return (
    <section aria-labelledby={`mentorship-${role}`} className="space-y-3">
      <h2 id={`mentorship-${role}`} className="text-lg font-semibold">{title}</h2>
      {isPending ? <Skeleton className="h-32 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title={role === "mentee" ? "No mentorship requests yet" : "Nobody has asked you yet"} text={role === "mentee" ? "Find a mentor in the directory (filter “Available for mentoring”)." : "Turn on “Available for mentoring” in your profile to be found."} />
      ) : (
        <ul className="space-y-3">
          {data.content.map((r) => <Item key={r.id} r={r} role={role} busy={busy} onRespond={(accept) => respond.mutate({ id: r.id, accept })} onComplete={() => complete.mutate(r.id)} onCancel={() => cancel.mutate(r.id)} />)}
        </ul>
      )}
      <FormError error={respond.error ?? complete.error ?? cancel.error} />
      {data ? <Pager page={data.page} onChange={setPage} /> : null}
    </section>
  );
}

function Item({ r, role, busy, onRespond, onComplete, onCancel }: {
  r: MentorshipRequestView;
  role: "mentee" | "mentor";
  busy: boolean;
  onRespond: (accept: boolean) => void;
  onComplete: () => void;
  onCancel: () => void;
}) {
  const other = role === "mentee" ? r.mentor : r.mentee;
  return (
    <li className="space-y-2 rounded-lg border p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          {other ? (
            <>
              <MemberAvatar name={other.displayName} photoUrl={other.photoUrl} />
              <Link href={`/members/${other.userId}`} className="font-medium hover:underline">{other.displayName}</Link>
            </>
          ) : <span>Member</span>}
          <span className="text-xs text-muted-foreground">· {formatDate(r.createdAt)}</span>
        </div>
        <Badge variant={r.status === "ACCEPTED" ? "default" : "secondary"}>{MENTORSHIP_STATUS_LABELS[r.status]}</Badge>
      </div>
      <p className="font-medium">{r.topic}</p>
      <p className="whitespace-pre-line text-muted-foreground">{r.message}</p>
      {r.responseNote ? <p className="rounded-lg bg-muted px-3 py-2">“{r.responseNote}”</p> : null}
      <div className="flex flex-wrap gap-2">
        {role === "mentor" && r.status === "PENDING" ? (
          <>
            <Button size="xs" disabled={busy} onClick={() => onRespond(true)}>Accept</Button>
            <Button size="xs" variant="outline" disabled={busy} onClick={() => onRespond(false)}>Decline</Button>
          </>
        ) : null}
        {role === "mentee" && r.status === "PENDING" ? <Button size="xs" variant="outline" disabled={busy} onClick={onCancel}>Cancel request</Button> : null}
        {r.status === "ACCEPTED" ? <Button size="xs" variant="outline" disabled={busy} onClick={onComplete}>Mark complete</Button> : null}
      </div>
    </li>
  );
}
