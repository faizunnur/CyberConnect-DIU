"use client";

import { Archive, ArchiveRestore, Loader2, Send, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState } from "@/components/shared/pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { ReportButton } from "@/features/community/components/report-dialog";
import { relativeTime } from "@/features/community/types";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { isApiError } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import {
  useConversation,
  useConversationAction,
  useConversations,
  useDeleteMessage,
  useHistory,
  useSendMessage,
  type Box,
  type ConversationView,
} from "../api";

/** Two-pane messaging: list (inbox / requests / archived) on the left, the selected thread on the right. */
export function Messages({ conversationId }: { conversationId?: string }) {
  const params = useSearchParams();
  const box = (params.get("box") as Box | null) ?? "inbox";
  return (
    <div className="grid gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
      <aside className={cn("space-y-3", conversationId && "hidden lg:block")}>
        <div>
          <h1 className="text-2xl font-semibold">Messages</h1>
          <p className="text-sm text-muted-foreground">Direct conversations with verified members.</p>
        </div>
        <nav className="flex gap-1" aria-label="Mailboxes">
          {(["inbox", "requests", "archived"] as Box[]).map((b) => (
            <Link key={b} href={`/messages?box=${b}`} aria-current={box === b ? "page" : undefined} className={cn("rounded-full border px-3 py-1 text-xs font-medium capitalize", box === b ? "border-primary bg-primary text-primary-foreground" : "hover:bg-accent")}>
              {b}
            </Link>
          ))}
        </nav>
        <ConversationList box={box} selected={conversationId} />
      </aside>
      <section className="min-h-[60vh]">
        {conversationId ? <Thread id={conversationId} box={box} /> : (
          <div className="hidden h-full items-center justify-center rounded-xl border border-dashed p-8 text-center text-sm text-muted-foreground lg:flex">
            Select a conversation, or start one from a member profile.
          </div>
        )}
      </section>
    </div>
  );
}

function ConversationList({ box, selected }: { box: Box; selected?: string }) {
  const { data, isPending, isError, error } = useConversations(box, 0);
  if (isPending) return <Skeleton className="h-64 w-full" />;
  if (isError) return <FormError error={error} />;
  if (data.content.length === 0) {
    return <EmptyState title={box === "inbox" ? "No conversations yet" : box === "requests" ? "No message requests" : "Nothing archived"} text={box === "inbox" ? "Open a member profile and choose Message." : undefined} />;
  }
  return (
    <ul className="divide-y rounded-xl border bg-card">
      {data.content.map((c) => (
        <li key={c.id}>
          <Link href={`/messages/${c.id}?box=${box}`} className={cn("flex items-center gap-3 px-3 py-2.5 hover:bg-accent/60", selected === c.id && "bg-accent")}>
            <MemberAvatar name={c.other?.displayName ?? "?"} photoUrl={c.other?.photoUrl ?? null} />
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between gap-2">
                <p className={cn("truncate text-sm", c.unreadCount > 0 && "font-semibold")}>{c.other?.displayName ?? "Member"}</p>
                {c.lastMessageAt ? <span className="shrink-0 text-xs text-muted-foreground">{relativeTime(c.lastMessageAt)}</span> : null}
              </div>
              <p className="truncate text-xs text-muted-foreground">{c.lastMessageMine ? "You: " : ""}{c.lastMessagePreview ?? ""}</p>
            </div>
            {c.unreadCount > 0 ? <Badge className="shrink-0">{c.unreadCount}</Badge> : null}
          </Link>
        </li>
      ))}
    </ul>
  );
}

function Thread({ id, box }: { id: string; box: Box }) {
  const router = useRouter();
  const { data: conversation, isPending, isError, error } = useConversation(id);
  const history = useHistory(id);
  const send = useSendMessage();
  const read = useConversationAction("read");
  const accept = useConversationAction("accept");
  const decline = useConversationAction("decline");
  const archive = useConversationAction("archive");
  const unarchive = useConversationAction("unarchive");
  const remove = useDeleteMessage();
  const [draft, setDraft] = useState("");
  const bottom = useRef<HTMLDivElement>(null);
  const unread = conversation?.unreadCount ?? 0;
  const readMutate = read.mutate;

  useEffect(() => {
    if (unread > 0) readMutate(id);
  }, [id, unread, readMutate]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: "end" });
  }, [history.data]);

  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) {
    if (isApiError(error) && error.status === 404) return <EmptyState title="Conversation not found" />;
    return <FormError error={error} />;
  }
  const c: ConversationView = conversation;
  const messages = [...(history.data ?? [])].reverse();
  const busy = accept.isPending || decline.isPending || archive.isPending || unarchive.isPending;

  return (
    <div className="flex h-[70vh] flex-col rounded-xl border bg-card">
      <header className="flex items-center justify-between gap-2 border-b px-4 py-3">
        <div className="flex items-center gap-3">
          <Link href={`/messages?box=${box}`} className="text-sm text-muted-foreground hover:text-foreground lg:hidden">←</Link>
          {c.other ? (
            <>
              <MemberAvatar name={c.other.displayName} photoUrl={c.other.photoUrl} />
              <div>
                <Link href={`/members/${c.other.userId}`} className="font-medium hover:underline">{c.other.displayName}</Link>
                {c.other.headline ? <p className="text-xs text-muted-foreground">{c.other.headline}</p> : null}
              </div>
            </>
          ) : <span className="font-medium">Member</span>}
        </div>
        <div className="flex gap-1">
          {c.state === "ARCHIVED" ? (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => unarchive.mutate(id)}><ArchiveRestore aria-hidden="true" /> Unarchive</Button>
          ) : c.state === "ACTIVE" ? (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => archive.mutate(id, { onSuccess: () => router.push("/messages") })}><Archive aria-hidden="true" /> Archive</Button>
          ) : null}
        </div>
      </header>

      {c.state === "REQUEST" ? (
        <div className="flex flex-wrap items-center justify-between gap-2 border-b bg-muted/60 px-4 py-2 text-sm">
          <span>{c.other?.displayName ?? "This member"} is not a connection. Accept to reply, or decline.</span>
          <div className="flex gap-2">
            <Button size="sm" disabled={busy} onClick={() => accept.mutate(id)}>Accept</Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => decline.mutate(id, { onSuccess: () => router.push("/messages?box=requests") })}>Decline</Button>
          </div>
        </div>
      ) : null}

      <div className="flex-1 space-y-3 overflow-y-auto px-4 py-3">
        {history.isPending ? <Skeleton className="h-24 w-full" /> : history.isError ? <FormError error={history.error} /> : messages.length === 0 ? (
          <p className="text-center text-sm text-muted-foreground">No messages yet.</p>
        ) : messages.map((m) => (
          <div key={m.id} className={cn("flex", m.mine ? "justify-end" : "justify-start")}>
            <div className={cn("group max-w-[80%] rounded-2xl px-3 py-2 text-sm", m.mine ? "bg-primary text-primary-foreground" : "bg-muted")}>
              {m.deleted ? <p className="italic opacity-70">Message removed</p> : <p className="whitespace-pre-line break-words">{m.body}</p>}
              <div className={cn("mt-1 flex items-center gap-2 text-[10px] opacity-70", m.mine ? "justify-end" : "justify-start")}>
                <span>{relativeTime(m.createdAt)}</span>
                {!m.deleted && m.mine ? (
                  <button type="button" className="inline-flex items-center gap-0.5 hover:underline" aria-label="Delete message" disabled={remove.isPending} onClick={() => remove.mutate(m.id)}>
                    <Trash2 className="size-3" aria-hidden="true" />
                  </button>
                ) : null}
                {!m.deleted && !m.mine ? <ReportButton targetType="MESSAGE" targetId={m.id} size="xs" label="Report" /> : null}
              </div>
            </div>
          </div>
        ))}
        <div ref={bottom} />
      </div>

      <form
        className="border-t p-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (!draft.trim()) return;
          send.mutate({ id, body: draft }, { onSuccess: () => setDraft("") });
        }}
      >
        {c.canSend ? (
          <div className="flex items-end gap-2">
            <Textarea
              aria-label="Message"
              rows={2}
              maxLength={4000}
              value={draft}
              placeholder="Write a message"
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  if (draft.trim()) send.mutate({ id, body: draft }, { onSuccess: () => setDraft("") });
                }
              }}
            />
            <Button type="submit" size="icon" aria-label="Send" disabled={!draft.trim() || send.isPending}>
              {send.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <Send aria-hidden="true" />}
            </Button>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            {c.otherState === "DECLINED" ? "This member declined your message request." : c.otherState === "REQUEST" ? "Wait for the member to accept your request before sending more." : "You cannot send messages in this conversation."}
          </p>
        )}
        <FormError error={send.error ?? remove.error ?? accept.error ?? decline.error} />
      </form>
    </div>
  );
}
