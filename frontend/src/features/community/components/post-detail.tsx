"use client";

import { Loader2, Pencil, Reply, Trash2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { isApiError } from "@/lib/api/client";
import { useAddComment, useComments, useDeleteComment, useEditComment, usePost } from "../hooks";
import { relativeTime, type CommentView } from "../types";
import { PostBody, PostCard } from "./post-card";
import { ReportButton } from "./report-dialog";

export function PostDetail({ postId }: { postId: string }) {
  const { data, isPending, isError, error } = usePost(postId);
  if (isPending) return <Skeleton className="h-64 w-full" />;
  if (isError) {
    if (isApiError(error) && error.status === 404) {
      return (
        <div className="space-y-2">
          <Link href="/home" className="text-sm text-muted-foreground hover:text-foreground">← Feed</Link>
          <h1 className="text-2xl font-semibold">Post not found</h1>
          <p className="text-muted-foreground">This post does not exist or is not available to you.</p>
        </div>
      );
    }
    return <FormError error={error} title="Could not load the post" />;
  }
  return (
    <div className="space-y-4">
      <Link href="/home" className="text-sm text-muted-foreground hover:text-foreground">← Feed</Link>
      <PostCard post={data} detail />
      {data.status === "ACTIVE" ? <Comments postId={postId} /> : null}
    </div>
  );
}

function Comments({ postId }: { postId: string }) {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useComments(postId, page);
  return (
    <section aria-labelledby="comments-heading" className="space-y-4">
      <h2 id="comments-heading" className="text-lg font-semibold">Comments</h2>
      <CommentForm postId={postId} placeholder="Add a comment" />
      {isPending ? <Skeleton className="h-24 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <p className="text-sm text-muted-foreground">No comments yet.</p>
      ) : (
        <ul className="space-y-4">
          {data.content.map((c) => <CommentItem key={c.id} comment={c} postId={postId} />)}
        </ul>
      )}
      {data && data.page.totalPages > 1 ? (
        <div className="flex gap-2 text-sm">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
          <Button variant="outline" size="sm" disabled={page + 1 >= data.page.totalPages} onClick={() => setPage(page + 1)}>Next</Button>
        </div>
      ) : null}
    </section>
  );
}

function CommentForm({ postId, parentId, placeholder, onDone, autoFocus }: {
  postId: string;
  parentId?: string;
  placeholder: string;
  onDone?: () => void;
  autoFocus?: boolean;
}) {
  const [body, setBody] = useState("");
  const add = useAddComment();
  return (
    <form
      className="space-y-2"
      onSubmit={(e) => {
        e.preventDefault();
        add.mutate({ postId, body, parentId }, { onSuccess: () => { setBody(""); onDone?.(); } });
      }}
    >
      <Textarea aria-label={placeholder} placeholder={placeholder} rows={2} maxLength={2000} value={body} autoFocus={autoFocus} onChange={(e) => setBody(e.target.value)} />
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={!body.trim() || add.isPending}>
          {add.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} {parentId ? "Reply" : "Comment"}
        </Button>
        {onDone ? <Button type="button" size="sm" variant="ghost" onClick={onDone}>Cancel</Button> : null}
      </div>
      <FormError error={add.error} />
    </form>
  );
}

function CommentItem({ comment, postId, isReply }: { comment: CommentView; postId: string; isReply?: boolean }) {
  const [replying, setReplying] = useState(false);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(comment.body ?? "");
  const edit = useEditComment();
  const remove = useDeleteComment();
  const placeholder = comment.status !== "ACTIVE";

  return (
    <li className={isReply ? "" : "rounded-lg border p-3"}>
      <div className="flex items-start gap-3">
        {placeholder ? (
          <div className="size-8 shrink-0 rounded-full bg-muted" aria-hidden="true" />
        ) : (
          <MemberAvatar name={comment.author?.displayName ?? "?"} photoUrl={comment.author?.photoUrl ?? null} />
        )}
        <div className="min-w-0 flex-1 space-y-1">
          <p className="text-xs text-muted-foreground">
            {placeholder ? (
              <span>{comment.status === "REMOVED" ? "Removed by moderation" : "Comment deleted"}</span>
            ) : comment.author ? (
              <Link href={`/members/${comment.author.userId}`} className="font-semibold text-foreground hover:underline">{comment.author.displayName}</Link>
            ) : (
              <span className="font-semibold text-foreground">Member</span>
            )}
            {" · "}{relativeTime(comment.createdAt)}{comment.editedAt ? " · edited" : ""}
          </p>
          {editing ? (
            <form
              className="space-y-2"
              onSubmit={(e) => {
                e.preventDefault();
                edit.mutate({ id: comment.id, body: draft }, { onSuccess: () => setEditing(false) });
              }}
            >
              <Textarea aria-label="Edit comment" rows={2} maxLength={2000} value={draft} onChange={(e) => setDraft(e.target.value)} autoFocus />
              <div className="flex gap-2">
                <Button type="submit" size="sm" disabled={!draft.trim() || edit.isPending}>Save</Button>
                <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(false)}>Cancel</Button>
              </div>
              <FormError error={edit.error} />
            </form>
          ) : comment.body ? (
            <PostBody text={comment.body} />
          ) : null}
          {!placeholder && !editing ? (
            <div className="flex flex-wrap items-center gap-1">
              {!isReply ? (
                <Button type="button" variant="ghost" size="xs" onClick={() => setReplying((v) => !v)}>
                  <Reply aria-hidden="true" /> Reply
                </Button>
              ) : null}
              {comment.own ? (
                <>
                  <Button type="button" variant="ghost" size="xs" aria-label="Edit comment" onClick={() => { setDraft(comment.body ?? ""); setEditing(true); }}><Pencil aria-hidden="true" /></Button>
                  <Button type="button" variant="ghost" size="xs" aria-label="Delete comment" disabled={remove.isPending} onClick={() => remove.mutate(comment.id)}><Trash2 aria-hidden="true" /></Button>
                </>
              ) : (
                <ReportButton targetType="COMMENT" targetId={comment.id} size="xs" />
              )}
            </div>
          ) : null}
          <FormError error={remove.error} />
          {replying ? <CommentForm postId={postId} parentId={comment.id} placeholder="Write a reply" onDone={() => setReplying(false)} autoFocus /> : null}
          {comment.replies.length > 0 ? (
            <ul className="mt-3 space-y-3 border-l pl-3">
              {comment.replies.map((r) => <CommentItem key={r.id} comment={r} postId={postId} isReply />)}
            </ul>
          ) : null}
        </div>
      </div>
    </li>
  );
}
