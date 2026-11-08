"use client";

import {
  Bookmark,
  Ellipsis,
  Flag,
  Globe2,
  Heart,
  Link2,
  Lock,
  MessageCircle,
  Pencil,
  Repeat2,
  ShieldAlert,
  Trash2,
} from "lucide-react";
import Link from "next/link";
import { useState, type ReactNode } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { cn } from "@/lib/utils";
import { useDeletePost, useToggleBookmark, useToggleLike } from "../hooks";
import { INTENT_LABELS, relativeTime, type PostView } from "../types";
import { PostAttachments } from "./post-attachments";
import { PostComposer } from "./post-composer";
import { ReportButton } from "./report-dialog";

const COLLAPSE_AT = 320;

/** Renders plain text with #hashtags turned into feed filter links; never interprets markup. */
export function PostBody({ text, className, collapsible }: { text: string; className?: string; collapsible?: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const long = collapsible && (text.length > COLLAPSE_AT || text.split("\n").length > 6);
  const shown = long && !expanded ? text.slice(0, COLLAPSE_AT).replace(/\s+\S*$/, "") : text;
  const parts = shown.split(/(#[\p{L}\p{M}\p{N}_]{1,64})/u);
  return (
    <div className={cn("text-[15px] leading-relaxed", className)}>
      <p className={cn("whitespace-pre-line break-words", long && !expanded && "line-clamp-6")}>
        {parts.map((part, i) =>
          /^#[\p{L}\p{M}\p{N}_]{1,64}$/u.test(part) ? (
            <Link key={i} href={`/home?tag=${encodeURIComponent(part.slice(1).toLowerCase())}`} className="font-medium text-primary hover:underline">
              {part}
            </Link>
          ) : (
            <span key={i}>{part}</span>
          ),
        )}
        {long && !expanded ? "… " : null}
        {long && !expanded ? (
          <button type="button" onClick={() => setExpanded(true)} className="font-medium text-muted-foreground hover:text-primary hover:underline">
            see more
          </button>
        ) : null}
      </p>
    </div>
  );
}

function AuthorLine({ post, compact }: { post: PostView; compact?: boolean }) {
  const author = post.author;
  return (
    <div className="flex min-w-0 items-center gap-3">
      <MemberAvatar name={author?.displayName ?? "?"} photoUrl={author?.photoUrl ?? null} size={compact ? "sm" : "lg"} />
      <div className="min-w-0 text-sm leading-tight">
        {author ? (
          <Link href={`/members/${author.userId}`} className="font-semibold hover:text-primary hover:underline">{author.displayName}</Link>
        ) : (
          <span className="font-semibold">Member</span>
        )}
        {author?.headline && !compact ? <p className="mt-0.5 truncate text-xs text-muted-foreground">{author.headline}</p> : null}
        <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
          <Link href={`/posts/${post.id}`} className="hover:underline" title={new Date(post.createdAt).toLocaleString()}>
            {relativeTime(post.createdAt)}
          </Link>
          {post.editedAt ? <span>· edited</span> : null}
          <span aria-hidden="true">·</span>
          {post.visibility === "CONNECTIONS_ONLY" ? (
            <Lock className="size-3" aria-label="Visible to connections" />
          ) : (
            <Globe2 className="size-3" aria-label="Visible to all verified members" />
          )}
        </p>
      </div>
    </div>
  );
}

function ActionButton({ icon: Icon, label, active, activeClass, onClick, disabled, title, href }: {
  icon: typeof Heart;
  label: string;
  active?: boolean;
  activeClass?: string;
  onClick?: () => void;
  disabled?: boolean;
  title?: string;
  href?: string;
}) {
  const className = cn(
    "flex flex-1 items-center justify-center gap-2 rounded-lg px-2 py-2.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-foreground disabled:pointer-events-none disabled:opacity-50",
    active && activeClass,
  );
  const content = (
    <>
      <Icon className={cn("size-5 shrink-0", active && "fill-current")} aria-hidden="true" />
      <span className="hidden sm:inline">{label}</span>
      <span className="sr-only sm:hidden">{label}</span>
    </>
  );
  return href ? (
    <Link href={href} className={className}>{content}</Link>
  ) : (
    <button type="button" className={className} onClick={onClick} disabled={disabled} aria-pressed={active} title={title}>
      {content}
    </button>
  );
}

export function PostCard({ post, detail, children }: { post: PostView; detail?: boolean; children?: ReactNode }) {
  const like = useToggleLike();
  const bookmark = useToggleBookmark();
  const remove = useDeletePost();
  const [editing, setEditing] = useState(false);
  const [reposting, setReposting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [reporting, setReporting] = useState(false);
  const [copied, setCopied] = useState(false);
  const canRepost = post.status === "ACTIVE" && !post.repostOf && !post.repostUnavailable && post.visibility === "ALL_MEMBERS";
  const active = post.status === "ACTIVE";

  if (editing) {
    return <PostComposer editing={post} onDone={() => setEditing(false)} autoFocus />;
  }

  const copyLink = () => {
    void navigator.clipboard?.writeText(`${window.location.origin}/posts/${post.id}`).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  const counts = [
    post.likeCount > 0 ? `${post.likeCount} ${post.likeCount === 1 ? "like" : "likes"}` : null,
    post.commentCount > 0 ? `${post.commentCount} ${post.commentCount === 1 ? "comment" : "comments"}` : null,
    post.repostCount > 0 ? `${post.repostCount} ${post.repostCount === 1 ? "repost" : "reposts"}` : null,
  ].filter(Boolean);

  return (
    <Card data-testid="post-card" className="gap-0 overflow-hidden py-0">
      {post.status !== "ACTIVE" ? (
        <p className="flex items-center gap-2 bg-muted px-4 py-2 text-sm text-muted-foreground">
          <ShieldAlert className="size-4 shrink-0" aria-hidden="true" />
          {post.status === "REMOVED" ? `Removed by moderation${post.removedReason ? `: ${post.removedReason}` : "."}` : "You deleted this post."}
        </p>
      ) : null}

      <div className="flex items-start justify-between gap-2 px-4 pt-4">
        <AuthorLine post={post} />
        <div className="flex shrink-0 items-center gap-1">
          <Badge variant="secondary" className="hidden sm:inline-flex">{INTENT_LABELS[post.intent]}</Badge>
          {active ? (
            <DropdownMenu>
              <DropdownMenuTrigger render={<Button type="button" variant="ghost" size="icon-sm" aria-label="More actions" />}>
                <Ellipsis aria-hidden="true" />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-48">
                <DropdownMenuItem onClick={() => bookmark.mutate({ id: post.id, bookmarked: post.bookmarked })}>
                  <Bookmark aria-hidden="true" /> {post.bookmarked ? "Remove from saved" : "Save"}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={copyLink}>
                  <Link2 aria-hidden="true" /> Copy link
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                {post.own ? (
                  <>
                    <DropdownMenuItem onClick={() => setEditing(true)}>
                      <Pencil aria-hidden="true" /> Edit post
                    </DropdownMenuItem>
                    <DropdownMenuItem variant="destructive" onClick={() => setConfirmDelete(true)}>
                      <Trash2 aria-hidden="true" /> Delete post
                    </DropdownMenuItem>
                  </>
                ) : (
                  <DropdownMenuItem onClick={() => setReporting(true)}>
                    <Flag aria-hidden="true" /> Report post
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
      </div>

      {copied ? <p className="px-4 pt-2 text-xs text-success" role="status">Link copied</p> : null}
      {confirmDelete ? (
        <div className="mx-4 mt-3 flex flex-wrap items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm">
          <span className="flex-1">Delete this post? Its photos and files are removed too.</span>
          <Button type="button" variant="destructive" size="sm" disabled={remove.isPending} onClick={() => remove.mutate(post.id, { onSettled: () => setConfirmDelete(false) })}>Delete</Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setConfirmDelete(false)}>Keep</Button>
        </div>
      ) : null}

      <div className="space-y-3 px-4 pt-3 pb-2">
        {post.body ? <PostBody text={post.body} collapsible={!detail} /> : null}
        {post.repostOf ? (
          <div className="overflow-hidden rounded-lg border">
            <div className="space-y-2 p-3">
              <AuthorLine post={post.repostOf} compact />
              {post.repostOf.body ? <PostBody text={post.repostOf.body} collapsible /> : null}
            </div>
            <PostAttachments attachments={post.repostOf.attachments ?? []} className="px-3 pb-3" />
            <p className="border-t px-3 py-2 text-xs text-muted-foreground">
              <Link href={`/posts/${post.repostOf.id}`} className="hover:underline">View original post</Link>
            </p>
          </div>
        ) : post.repostUnavailable ? (
          <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">The original post is no longer available.</p>
        ) : null}
      </div>

      <PostAttachments attachments={post.attachments ?? []} className="px-4 pb-2" />

      {counts.length > 0 ? (
        <p className="flex items-center gap-1.5 px-4 pb-2 text-xs text-muted-foreground">
          {post.likeCount > 0 ? (
            <span className="flex size-4 items-center justify-center rounded-full bg-destructive text-white" aria-hidden="true">
              <Heart className="size-2.5 fill-current" />
            </span>
          ) : null}
          {detail ? counts.join(" · ") : <Link href={`/posts/${post.id}`} className="hover:text-primary hover:underline">{counts.join(" · ")}</Link>}
        </p>
      ) : null}

      {active ? (
        <div className="mx-2 flex items-center border-t py-1">
          <ActionButton
            icon={Heart}
            label="Like"
            active={post.liked}
            activeClass="text-destructive hover:text-destructive"
            disabled={like.isPending}
            onClick={() => like.mutate({ id: post.id, liked: post.liked })}
          />
          <ActionButton icon={MessageCircle} label="Comment" href={detail ? undefined : `/posts/${post.id}`} onClick={detail ? () => document.getElementById("comment-body")?.focus() : undefined} />
          <ActionButton
            icon={Repeat2}
            label="Repost"
            active={reposting}
            activeClass="text-primary"
            disabled={!canRepost}
            title={canRepost ? "Repost with your thoughts" : "Only original posts shared with all members can be reposted"}
            onClick={() => setReposting((v) => !v)}
          />
          <ActionButton
            icon={Bookmark}
            label={post.bookmarked ? "Saved" : "Save"}
            active={post.bookmarked}
            activeClass="text-primary hover:text-primary"
            disabled={bookmark.isPending}
            onClick={() => bookmark.mutate({ id: post.id, bookmarked: post.bookmarked })}
          />
        </div>
      ) : null}

      <div className="px-4 empty:hidden">
        <FormError error={like.error ?? bookmark.error ?? remove.error} />
      </div>
      {reposting ? (
        <div className="px-4 pb-4">
          <PostComposer repostOf={post} onDone={() => setReposting(false)} autoFocus />
        </div>
      ) : null}
      {children ? <div className="border-t px-4 py-4">{children}</div> : null}
      {!post.own && active ? (
        <ReportButton targetType="POST" targetId={post.id} open={reporting} onOpenChange={setReporting} hideTrigger />
      ) : null}
    </Card>
  );
}
