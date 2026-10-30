"use client";

import { Bookmark, Heart, Lock, MessageCircle, Pencil, Repeat2, ShieldAlert, Trash2 } from "lucide-react";
import Link from "next/link";
import { useState, type ReactNode } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { cn } from "@/lib/utils";
import { useDeletePost, useToggleBookmark, useToggleLike } from "../hooks";
import { INTENT_LABELS, relativeTime, type PostView } from "../types";
import { PostComposer } from "./post-composer";
import { ReportButton } from "./report-dialog";

/** Renders plain text with #hashtags turned into feed filter links; never interprets markup. */
export function PostBody({ text, className }: { text: string; className?: string }) {
  const parts = text.split(/(#[\p{L}\p{M}\p{N}_]{1,64})/u);
  return (
    <p className={cn("text-sm whitespace-pre-line break-words", className)}>
      {parts.map((part, i) =>
        /^#[\p{L}\p{M}\p{N}_]{1,64}$/u.test(part) ? (
          <Link key={i} href={`/home?tag=${encodeURIComponent(part.slice(1).toLowerCase())}`} className="text-primary hover:underline">
            {part}
          </Link>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </p>
  );
}

function AuthorLine({ post, compact }: { post: PostView; compact?: boolean }) {
  const author = post.author;
  return (
    <div className="flex items-center gap-3">
      <MemberAvatar name={author?.displayName ?? "?"} photoUrl={author?.photoUrl ?? null} size={compact ? "sm" : "lg"} />
      <div className="min-w-0 text-sm">
        {author ? (
          <Link href={`/members/${author.userId}`} className="font-semibold hover:underline">{author.displayName}</Link>
        ) : (
          <span className="font-semibold">Member</span>
        )}
        {author?.headline && !compact ? <p className="truncate text-xs text-muted-foreground">{author.headline}</p> : null}
        <p className="text-xs text-muted-foreground">
          <Link href={`/posts/${post.id}`} className="hover:underline">{relativeTime(post.createdAt)}</Link>
          {post.editedAt ? " · edited" : ""}
          {post.visibility === "CONNECTIONS_ONLY" ? (
            <span className="ml-1 inline-flex items-center gap-0.5"><Lock className="size-3" aria-hidden="true" /> connections</span>
          ) : null}
        </p>
      </div>
    </div>
  );
}

export function PostCard({ post, detail, children }: { post: PostView; detail?: boolean; children?: ReactNode }) {
  const like = useToggleLike();
  const bookmark = useToggleBookmark();
  const remove = useDeletePost();
  const [editing, setEditing] = useState(false);
  const [reposting, setReposting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const canRepost = post.status === "ACTIVE" && !post.repostOf && !post.repostUnavailable && post.visibility === "ALL_MEMBERS";

  if (editing) {
    return <PostComposer editing={post} onDone={() => setEditing(false)} autoFocus />;
  }

  return (
    <Card data-testid="post-card">
      <CardContent className="space-y-3">
        {post.status !== "ACTIVE" ? (
          <p className="flex items-center gap-2 rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">
            <ShieldAlert className="size-4 shrink-0" aria-hidden="true" />
            {post.status === "REMOVED" ? `Removed by moderation${post.removedReason ? `: ${post.removedReason}` : "."}` : "You deleted this post."}
          </p>
        ) : null}
        <div className="flex items-start justify-between gap-2">
          <AuthorLine post={post} />
          <Badge variant="secondary">{INTENT_LABELS[post.intent]}</Badge>
        </div>
        {post.body ? <PostBody text={post.body} /> : null}
        {post.repostOf ? (
          <div className="rounded-lg border p-3">
            <AuthorLine post={post.repostOf} compact />
            <PostBody text={post.repostOf.body} className="mt-2" />
            <p className="mt-1 text-xs text-muted-foreground">
              <Link href={`/posts/${post.repostOf.id}`} className="hover:underline">View original</Link>
            </p>
          </div>
        ) : post.repostUnavailable ? (
          <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">The original post is no longer available.</p>
        ) : null}

        {post.status === "ACTIVE" ? (
          <div className="flex flex-wrap items-center gap-1 text-sm">
            <Button type="button" variant="ghost" size="sm" aria-pressed={post.liked} aria-label={post.liked ? "Unlike" : "Like"} disabled={like.isPending} onClick={() => like.mutate({ id: post.id, liked: post.liked })}>
              <Heart className={cn(post.liked && "fill-current text-destructive")} aria-hidden="true" /> {post.likeCount}
            </Button>
            {detail ? (
              <span className="inline-flex items-center gap-1 px-2 text-muted-foreground"><MessageCircle className="size-4" aria-hidden="true" /> {post.commentCount}</span>
            ) : (
              <Link href={`/posts/${post.id}`} className="inline-flex h-8 items-center gap-1 rounded-lg px-2 text-muted-foreground hover:bg-accent hover:text-foreground">
                <MessageCircle className="size-4" aria-hidden="true" /> {post.commentCount}
              </Link>
            )}
            <Button type="button" variant="ghost" size="sm" disabled={!canRepost} title={canRepost ? "Repost with commentary" : "Only original, all-member posts can be reposted"} onClick={() => setReposting((v) => !v)}>
              <Repeat2 aria-hidden="true" /> {post.repostCount}
            </Button>
            <Button type="button" variant="ghost" size="sm" aria-pressed={post.bookmarked} aria-label={post.bookmarked ? "Remove bookmark" : "Bookmark"} disabled={bookmark.isPending} onClick={() => bookmark.mutate({ id: post.id, bookmarked: post.bookmarked })}>
              <Bookmark className={cn(post.bookmarked && "fill-current text-primary")} aria-hidden="true" />
            </Button>
            <span className="flex-1" />
            {post.own ? (
              confirmDelete ? (
                <>
                  <Button type="button" variant="destructive" size="sm" disabled={remove.isPending} onClick={() => remove.mutate(post.id, { onSettled: () => setConfirmDelete(false) })}>Delete</Button>
                  <Button type="button" variant="ghost" size="sm" onClick={() => setConfirmDelete(false)}>Keep</Button>
                </>
              ) : (
                <>
                  <Button type="button" variant="ghost" size="sm" aria-label="Edit post" onClick={() => setEditing(true)}><Pencil aria-hidden="true" /></Button>
                  <Button type="button" variant="ghost" size="sm" aria-label="Delete post" onClick={() => setConfirmDelete(true)}><Trash2 aria-hidden="true" /></Button>
                </>
              )
            ) : (
              <ReportButton targetType="POST" targetId={post.id} />
            )}
          </div>
        ) : null}
        <FormError error={like.error ?? bookmark.error ?? remove.error} />
        {reposting ? <PostComposer repostOf={post} onDone={() => setReposting(false)} autoFocus /> : null}
        {children}
      </CardContent>
    </Card>
  );
}
