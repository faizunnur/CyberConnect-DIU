"use client";

import { CheckCheck } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { relativeTime } from "@/features/community/types";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { cn } from "@/lib/utils";
import { useMarkAllRead, useMarkRead, useNotifications, type NotificationView } from "../api";

export function NotificationsPage() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useNotifications(unreadOnly, page);
  const markAll = useMarkAllRead();
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Notifications</h1>
          <p className="mt-1 text-muted-foreground">What happened around your profile, posts, applications and network.</p>
        </div>
        <div className="flex gap-2">
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="size-4 accent-primary" checked={unreadOnly} onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }} /> Unread only
          </label>
          <Button variant="outline" size="sm" disabled={markAll.isPending} onClick={() => markAll.mutate()}>
            <CheckCheck aria-hidden="true" /> Mark all read
          </Button>
        </div>
      </div>
      <FormError error={markAll.error} />
      {isPending ? <Skeleton className="h-64 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title={unreadOnly ? "Nothing unread" : "No notifications yet"} />
      ) : (
        <>
          <ul className="divide-y rounded-xl border bg-card">
            {data.content.map((n) => <Item key={n.id} n={n} />)}
          </ul>
          <Pager page={data.page} onChange={setPage} />
        </>
      )}
    </div>
  );
}

function Item({ n }: { n: NotificationView }) {
  const markRead = useMarkRead();
  const unread = !n.readAt;
  const content = (
    <div className="flex items-start gap-3">
      {n.actor ? <MemberAvatar name={n.actor.displayName} photoUrl={n.actor.photoUrl} /> : <div className="size-8 shrink-0 rounded-full bg-muted" aria-hidden="true" />}
      <div className="min-w-0 flex-1">
        <p className={cn("text-sm", unread && "font-semibold")}>{n.title}</p>
        {n.body ? <p className="truncate text-sm text-muted-foreground">{n.body}</p> : null}
        <p className="text-xs text-muted-foreground">{relativeTime(n.createdAt)}</p>
      </div>
      {unread ? <span className="mt-2 size-2 shrink-0 rounded-full bg-primary" aria-label="Unread" /> : null}
    </div>
  );
  return (
    <li className={cn("px-4 py-3", unread && "bg-accent/40")}>
      {n.link ? (
        <Link href={n.link} className="block" onClick={() => { if (unread) markRead.mutate(n.id); }}>{content}</Link>
      ) : (
        <button type="button" className="block w-full text-left" onClick={() => { if (unread) markRead.mutate(n.id); }}>{content}</button>
      )}
    </li>
  );
}
