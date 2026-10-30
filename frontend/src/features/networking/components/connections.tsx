"use client";

import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { MemberCardItem } from "@/features/profile/components/member-card";
import { MentorshipPanel } from "./mentorship";
import { formatDate } from "@/lib/api/types";
import {
  useBlocks,
  useConnections,
  useNetworkSummary,
  useRemoveConnection,
  useRequests,
  useRespondToRequest,
  useUnblockMember,
} from "../hooks";

export function Connections() {
  const { data: summary } = useNetworkSummary();
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Connections</h1>
        <p className="mt-1 text-muted-foreground">Your professional network inside the community.</p>
      </div>
      <Tabs defaultValue="connections">
        <TabsList className="w-full justify-start overflow-x-auto">
          <TabsTrigger value="connections">Connections{summary ? ` (${summary.connections})` : ""}</TabsTrigger>
          <TabsTrigger value="incoming">Requests{summary && summary.pendingIncoming > 0 ? ` (${summary.pendingIncoming})` : ""}</TabsTrigger>
          <TabsTrigger value="outgoing">Sent{summary && summary.pendingOutgoing > 0 ? ` (${summary.pendingOutgoing})` : ""}</TabsTrigger>
          <TabsTrigger value="blocked">Blocked{summary && summary.blocked > 0 ? ` (${summary.blocked})` : ""}</TabsTrigger>
          <TabsTrigger value="mentorship">Mentorship</TabsTrigger>
        </TabsList>
        <TabsContent value="connections"><ConnectionList /></TabsContent>
        <TabsContent value="incoming"><RequestList direction="incoming" /></TabsContent>
        <TabsContent value="outgoing"><RequestList direction="outgoing" /></TabsContent>
        <TabsContent value="blocked"><BlockedList /></TabsContent>
        <TabsContent value="mentorship"><MentorshipPanel /></TabsContent>
      </Tabs>
    </div>
  );
}

function ConnectionList() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useConnections(page);
  const remove = useRemoveConnection();
  if (isPending) return <Skeleton className="h-40 w-full" />;
  if (isError) return <FormError error={error} />;
  if (data.content.length === 0) return <EmptyState title="No connections yet" text="Find members in the directory and send a request." />;
  return (
    <div className="space-y-4">
      <FormError error={remove.error} />
      <div className="grid gap-4 md:grid-cols-2">
        {data.content.map((c) => (
          <MemberCardItem
            key={c.id}
            member={c.member}
            actions={
              <Button size="sm" variant="outline" disabled={remove.isPending} onClick={() => remove.mutate(c.member.userId)} title={`Connected ${formatDate(c.connectedAt)}`}>
                Remove
              </Button>
            }
          />
        ))}
      </div>
      <Pager page={data.page} onChange={setPage} />
    </div>
  );
}

function RequestList({ direction }: { direction: "incoming" | "outgoing" }) {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useRequests(direction, page);
  const accept = useRespondToRequest("accept");
  const decline = useRespondToRequest("decline");
  const cancel = useRespondToRequest("cancel");
  if (isPending) return <Skeleton className="h-40 w-full" />;
  if (isError) return <FormError error={error} />;
  if (data.content.length === 0) {
    return direction === "incoming"
      ? <EmptyState title="No pending requests" />
      : <EmptyState title="No sent requests" text="Requests you send appear here until they are answered." />;
  }
  const busy = accept.isPending || decline.isPending || cancel.isPending;
  return (
    <div className="space-y-4">
      <FormError error={accept.error ?? decline.error ?? cancel.error} />
      <div className="grid gap-4 md:grid-cols-2">
        {data.content.map((r) => (
          <div key={r.id} className="space-y-2">
            <MemberCardItem
              member={r.member}
              actions={
                direction === "incoming" ? (
                  <>
                    <Button size="sm" disabled={busy} onClick={() => accept.mutate(r.id)}>Accept</Button>
                    <Button size="sm" variant="outline" disabled={busy} onClick={() => decline.mutate(r.id)}>Decline</Button>
                  </>
                ) : (
                  <Button size="sm" variant="outline" disabled={busy} onClick={() => cancel.mutate(r.id)}>Cancel</Button>
                )
              }
            />
            {r.message ? <p className="rounded-lg bg-muted px-3 py-2 text-sm">“{r.message}”</p> : null}
          </div>
        ))}
      </div>
      <Pager page={data.page} onChange={setPage} />
    </div>
  );
}

function BlockedList() {
  const { data, isPending, isError, error } = useBlocks();
  const unblock = useUnblockMember();
  if (isPending) return <Skeleton className="h-40 w-full" />;
  if (isError) return <FormError error={error} />;
  if (data.length === 0) return <EmptyState title="You have not blocked anyone" text="Blocked members cannot see your profile or contact you." />;
  return (
    <div className="space-y-4">
      <FormError error={unblock.error} />
      <div className="grid gap-4 md:grid-cols-2">
        {data.map((m) => (
          <MemberCardItem key={m.userId} member={m} actions={<Button size="sm" variant="outline" disabled={unblock.isPending} onClick={() => unblock.mutate(m.userId)}>Unblock</Button>} />
        ))}
      </div>
    </div>
  );
}
