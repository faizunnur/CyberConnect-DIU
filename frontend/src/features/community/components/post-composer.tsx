"use client";

/* eslint-disable @next/next/no-img-element -- previews are local object URLs of files the member just picked. */
import { useQueryClient } from "@tanstack/react-query";
import { Award, CalendarDays, FileText, Globe2, Image as ImageIcon, Loader2, Lock, Repeat2, X } from "lucide-react";
import { useEffect, useRef, useState, type DragEvent } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useMe } from "@/features/auth/hooks";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { useMyProfile } from "@/features/profile/hooks";
import { ApiError } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import { communityApi } from "../api";
import { useCreatePost, useEditPost } from "../hooks";
import {
  DOCUMENT_ACCEPT,
  INTENTS,
  INTENT_LABELS,
  MAX_ATTACHMENT_BYTES,
  MAX_PHOTOS_PER_POST,
  PHOTO_ACCEPT,
  VISIBILITY_LABELS,
  formatBytes,
  type AttachmentView,
  type PostIntent,
  type PostView,
  type PostVisibility,
} from "../types";
import { DocumentIcon, PostAttachments, documentLabel } from "./post-attachments";

const MAX = 5000;
const pillSelect =
  "h-8 rounded-full border bg-background px-3 text-xs font-medium text-foreground hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring";

interface Pending {
  key: string;
  file: File;
  kind: "IMAGE" | "FILE";
  preview: string | null;
  status: "uploading" | "done" | "error";
  attachment?: AttachmentView;
  error?: string;
}

function isPhoto(file: File) {
  return file.type === "image/jpeg" || file.type === "image/png" || /\.(jpe?g|png)$/i.test(file.name);
}

/**
 * Social-style composer. Collapsed it is a "Start a post" bar with Photo / Document / Event / Achievement shortcuts;
 * expanded it takes text, up to four photos or one document (uploaded immediately, removable), intent and audience.
 * The same component edits a post (text, intent, audience) or reposts one with commentary.
 */
export function PostComposer({ repostOf, editing, onDone, autoFocus }: {
  repostOf?: PostView;
  editing?: PostView;
  onDone?: () => void;
  autoFocus?: boolean;
}) {
  const compact = !!(repostOf || editing);
  const [expanded, setExpanded] = useState(compact);
  const [body, setBody] = useState(editing?.body ?? "");
  const [intent, setIntent] = useState<PostIntent>(editing?.intent ?? repostOf?.intent ?? "TECHNICAL_DISCUSSION");
  const [visibility, setVisibility] = useState<PostVisibility>(editing?.visibility ?? "ALL_MEMBERS");
  const [pending, setPending] = useState<Pending[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const photoInput = useRef<HTMLInputElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const textRef = useRef<HTMLTextAreaElement>(null);
  const create = useCreatePost();
  const edit = useEditPost();
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const { data: profile } = useMyProfile();

  // Release object URLs when the composer goes away.
  const pendingRef = useRef(pending);
  useEffect(() => {
    pendingRef.current = pending;
  }, [pending]);
  useEffect(() => () => pendingRef.current.forEach((p) => p.preview && URL.revokeObjectURL(p.preview)), []);

  const uploading = pending.some((p) => p.status === "uploading");
  const ready = pending.filter((p) => p.status === "done" && p.attachment);
  const busy = create.isPending || edit.isPending;
  const hasContent = body.trim().length > 0 || ready.length > 0 || !!repostOf;
  const canSubmit = hasContent && body.length <= MAX && !busy && !uploading;
  const name = me?.fullName ?? "You";

  const addFiles = (files: FileList | File[] | null, want?: "IMAGE" | "FILE") => {
    if (!files || files.length === 0) return;
    setNotice(null);
    setExpanded(true);
    const current = pending.filter((p) => p.status !== "error");
    const next: Pending[] = [];
    for (const file of Array.from(files)) {
      const kind: "IMAGE" | "FILE" = isPhoto(file) ? "IMAGE" : "FILE";
      if (want && kind !== want) {
        setNotice(want === "IMAGE" ? "Choose JPEG or PNG photos." : "Choose a PDF, Word, PowerPoint or Excel file.");
        continue;
      }
      if (file.size > MAX_ATTACHMENT_BYTES) {
        setNotice(`${file.name} is larger than ${formatBytes(MAX_ATTACHMENT_BYTES)}.`);
        continue;
      }
      const all = [...current, ...next];
      const photos = all.filter((p) => p.kind === "IMAGE").length;
      const docs = all.filter((p) => p.kind === "FILE").length;
      if (kind === "FILE" && (docs > 0 || photos > 0)) {
        setNotice("A post can carry either photos or one document.");
        continue;
      }
      if (kind === "IMAGE" && (docs > 0 || photos >= MAX_PHOTOS_PER_POST)) {
        setNotice(docs > 0 ? "A post can carry either photos or one document." : `Up to ${MAX_PHOTOS_PER_POST} photos per post.`);
        continue;
      }
      next.push({
        key: `${file.name}-${file.size}-${Math.random().toString(36).slice(2)}`,
        file,
        kind,
        preview: kind === "IMAGE" ? URL.createObjectURL(file) : null,
        status: "uploading",
      });
    }
    if (next.length === 0) return;
    setPending((prev) => [...prev.filter((p) => p.status !== "error"), ...next]);
    for (const item of next) {
      communityApi
        .uploadAttachment(item.file)
        .then((attachment) =>
          setPending((prev) => prev.map((p) => (p.key === item.key ? { ...p, status: "done", attachment } : p))),
        )
        .catch((error: unknown) =>
          setPending((prev) =>
            prev.map((p) =>
              p.key === item.key
                ? { ...p, status: "error", error: error instanceof ApiError ? error.message : "Upload failed. Try again." }
                : p,
            ),
          ),
        );
    }
  };

  const removeItem = (item: Pending) => {
    if (item.preview) URL.revokeObjectURL(item.preview);
    if (item.attachment) void communityApi.discardAttachment(item.attachment.id).catch(() => undefined);
    setPending((prev) => prev.filter((p) => p.key !== item.key));
  };

  const reset = () => {
    pending.forEach((p) => p.preview && URL.revokeObjectURL(p.preview));
    setPending([]);
    setBody("");
    setNotice(null);
    setExpanded(false);
  };

  const cancel = () => {
    pending.forEach((p) => p.attachment && void communityApi.discardAttachment(p.attachment.id).catch(() => undefined));
    reset();
    onDone?.();
  };

  const submit = async () => {
    if (editing) {
      await edit.mutateAsync({ id: editing.id, intent, body, visibility });
    } else {
      await create.mutateAsync({
        intent,
        body,
        visibility,
        repostOfId: repostOf?.id,
        attachmentIds: ready.map((p) => p.attachment!.id),
      });
      reset();
      void queryClient.invalidateQueries({ queryKey: ["community"] });
    }
    onDone?.();
  };

  function openPicker(kind: "IMAGE" | "FILE") {
    (kind === "IMAGE" ? photoInput : fileInput).current?.click();
  }

  function onQuick(action: "photo" | "document" | "event" | "achievement") {
    if (action === "photo") return openPicker("IMAGE");
    if (action === "document") return openPicker("FILE");
    setIntent(action === "event" ? "EVENT" : "ACHIEVEMENT");
    setExpanded(true);
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    if (!compact) addFiles(e.dataTransfer.files);
  };

  const pickers = (
    <>
      <input ref={photoInput} type="file" accept={PHOTO_ACCEPT} multiple hidden onChange={(e) => { addFiles(e.target.files, "IMAGE"); e.target.value = ""; }} />
      <input ref={fileInput} type="file" accept={DOCUMENT_ACCEPT} hidden onChange={(e) => { addFiles(e.target.files, "FILE"); e.target.value = ""; }} />
    </>
  );

  // Collapsed: the familiar "Start a post" bar.
  if (!expanded) {
    const quick = [
      { action: "photo", label: "Photo", icon: ImageIcon, tone: "text-sky-600 dark:text-sky-400" },
      { action: "document", label: "Document", icon: FileText, tone: "text-amber-600 dark:text-amber-400" },
      { action: "event", label: "Event", icon: CalendarDays, tone: "text-rose-600 dark:text-rose-400" },
      { action: "achievement", label: "Achievement", icon: Award, tone: "text-emerald-600 dark:text-emerald-400" },
    ] as const;
    return (
      <Card className="py-3" onDragOver={(e) => { e.preventDefault(); setDragging(true); }} onDragLeave={() => setDragging(false)} onDrop={onDrop}>
        <CardContent className={cn("space-y-2 px-3 sm:px-4", dragging && "rounded-lg ring-2 ring-primary/50")}>
          {pickers}
          <div className="flex items-center gap-3">
            <MemberAvatar name={name} photoUrl={profile?.photoUrl ?? null} />
            <button
              type="button"
              onClick={() => setExpanded(true)}
              className="h-11 flex-1 rounded-full border px-4 text-left text-sm text-muted-foreground transition-colors hover:bg-accent"
            >
              Start a post
            </button>
          </div>
          <div className="grid grid-cols-4 gap-1">
            {quick.map((q) => (
              <button
                key={q.label}
                type="button"
                onClick={() => onQuick(q.action)}
                className="flex items-center justify-center gap-2 rounded-lg px-2 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
              >
                <q.icon className={cn("size-5 shrink-0", q.tone)} aria-hidden="true" />
                <span className="hidden sm:inline">{q.label}</span>
                <span className="sr-only sm:hidden">{q.label}</span>
              </button>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  const textareaId = editing ? `edit-${editing.id}` : repostOf ? `repost-${repostOf.id}` : "composer-body";
  return (
    <Card
      className={cn(compact && "border-dashed shadow-none")}
      onDragOver={(e) => { if (!compact) { e.preventDefault(); setDragging(true); } }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
    >
      <CardContent className={cn("space-y-3", dragging && "rounded-lg ring-2 ring-primary/50")}>
        {pickers}
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          {repostOf ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Repeat2 className="size-4" aria-hidden="true" /> Reposting {repostOf.author?.displayName ?? "a member"}’s post. Add your thoughts (optional).
            </p>
          ) : null}
          {!compact ? (
            <div className="flex items-center gap-3">
              <MemberAvatar name={name} photoUrl={profile?.photoUrl ?? null} size="lg" />
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold">{name}</p>
                <label className="mt-1 inline-flex items-center gap-1.5 text-xs text-muted-foreground">
                  {visibility === "ALL_MEMBERS" ? <Globe2 className="size-3.5" aria-hidden="true" /> : <Lock className="size-3.5" aria-hidden="true" />}
                  <span className="sr-only">Visible to</span>
                  <select className={pillSelect} value={visibility} onChange={(e) => setVisibility(e.target.value as PostVisibility)} aria-label="Who can see this post">
                    {(Object.keys(VISIBILITY_LABELS) as PostVisibility[]).map((v) => <option key={v} value={v}>{VISIBILITY_LABELS[v]}</option>)}
                  </select>
                </label>
              </div>
            </div>
          ) : null}

          <Label htmlFor={textareaId} className="sr-only">Post text</Label>
          <Textarea
            ref={textRef}
            id={textareaId}
            rows={compact ? 3 : 5}
            autoFocus={autoFocus ?? !compact}
            placeholder={repostOf ? "Why is this worth sharing?" : "What do you want to talk about? Use #hashtags so others can find it."}
            value={body}
            maxLength={MAX + 500}
            onChange={(e) => setBody(e.target.value)}
            onPaste={(e) => { if (!compact && e.clipboardData.files.length > 0) { e.preventDefault(); addFiles(e.clipboardData.files); } }}
            className="min-h-24 resize-y border-0 bg-transparent px-0 text-base shadow-none focus-visible:ring-0 md:text-[15px]"
          />

          {pending.length > 0 ? <PendingAttachments items={pending} onRemove={removeItem} /> : null}
          {editing && editing.attachments.length > 0 ? (
            <div className="space-y-1">
              <PostAttachments attachments={editing.attachments} />
              <p className="text-xs text-muted-foreground">Attachments cannot be changed after posting.</p>
            </div>
          ) : null}
          {notice ? <p className="text-sm text-destructive" role="alert">{notice}</p> : null}

          <div className="flex flex-wrap items-center gap-2 border-t pt-3">
            {!compact ? (
              <>
                <Button type="button" variant="ghost" size="icon" onClick={() => openPicker("IMAGE")} aria-label="Add photos" title="Add photos (up to 4)">
                  <ImageIcon className="text-sky-600 dark:text-sky-400" aria-hidden="true" />
                </Button>
                <Button type="button" variant="ghost" size="icon" onClick={() => openPicker("FILE")} aria-label="Attach a document" title="Attach a PDF, Word, PowerPoint or Excel file">
                  <FileText className="text-amber-600 dark:text-amber-400" aria-hidden="true" />
                </Button>
              </>
            ) : null}
            <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span className="sr-only">Post type</span>
              <select className={pillSelect} value={intent} onChange={(e) => setIntent(e.target.value as PostIntent)} aria-label="Post type">
                {INTENTS.map((i) => <option key={i} value={i}>{INTENT_LABELS[i]}</option>)}
              </select>
            </label>
            {compact ? (
              <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className="sr-only">Visible to</span>
                <select className={pillSelect} value={visibility} onChange={(e) => setVisibility(e.target.value as PostVisibility)} aria-label="Who can see this post">
                  {(Object.keys(VISIBILITY_LABELS) as PostVisibility[]).map((v) => <option key={v} value={v}>{VISIBILITY_LABELS[v]}</option>)}
                </select>
              </label>
            ) : null}
            <span className={cn("ml-auto text-xs", body.length > MAX ? "text-destructive" : "text-muted-foreground")} aria-live="polite">
              {body.length > MAX * 0.8 ? `${body.length}/${MAX}` : ""}
            </span>
            <Button type="button" variant="ghost" size="sm" onClick={cancel}>Cancel</Button>
            <Button type="submit" size="sm" className="rounded-full px-5" disabled={!canSubmit}>
              {busy || uploading ? <Loader2 className="animate-spin" aria-hidden="true" /> : null}
              {editing ? "Save" : repostOf ? "Repost" : uploading ? "Uploading…" : "Post"}
            </Button>
          </div>
          <FormError error={create.error ?? edit.error} />
        </form>
      </CardContent>
    </Card>
  );
}

function PendingAttachments({ items, onRemove }: { items: Pending[]; onRemove: (item: Pending) => void }) {
  const photos = items.filter((i) => i.kind === "IMAGE");
  const files = items.filter((i) => i.kind === "FILE");
  return (
    <div className="space-y-2">
      {photos.length > 0 ? (
        <ul className={cn("grid gap-2", photos.length === 1 ? "grid-cols-1" : "grid-cols-2 sm:grid-cols-4")}>
          {photos.map((item) => (
            <li key={item.key} className="relative overflow-hidden rounded-lg border bg-muted">
              <img
                src={item.preview ?? ""}
                alt={item.file.name}
                className={cn(photos.length === 1 ? "mx-auto h-auto max-h-[min(70vh,560px)] w-auto max-w-full object-contain" : "aspect-square w-full object-cover")}
              />
              <StatusOverlay item={item} />
              <RemoveButton item={item} onRemove={onRemove} />
            </li>
          ))}
        </ul>
      ) : null}
      {files.map((item) => {
        return (
          <div key={item.key} className="relative flex items-center gap-3 rounded-lg border bg-muted/40 p-3 pr-12">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
              {item.status === "uploading" ? <Loader2 className="size-5 animate-spin" aria-hidden="true" /> : <DocumentIcon contentType={item.attachment?.contentType ?? ""} className="size-5" />}
            </span>
            <span className="min-w-0">
              <span className="block truncate text-sm font-medium">{item.file.name}</span>
              <span className={cn("block text-xs", item.status === "error" ? "text-destructive" : "text-muted-foreground")}>
                {item.status === "error" ? item.error : item.status === "uploading" ? "Uploading…" : `${documentLabel(item.attachment!.contentType)} · ${formatBytes(item.attachment!.sizeBytes)}`}
              </span>
            </span>
            <RemoveButton item={item} onRemove={onRemove} />
          </div>
        );
      })}
    </div>
  );
}

function StatusOverlay({ item }: { item: Pending }) {
  if (item.status === "done") return null;
  return (
    <div className={cn("absolute inset-0 flex items-center justify-center p-2 text-center text-xs", item.status === "error" ? "bg-destructive/80 text-white" : "bg-black/40 text-white")}>
      {item.status === "uploading" ? <Loader2 className="size-6 animate-spin" aria-label="Uploading" /> : item.error}
    </div>
  );
}

function RemoveButton({ item, onRemove }: { item: Pending; onRemove: (item: Pending) => void }) {
  return (
    <button
      type="button"
      onClick={() => onRemove(item)}
      aria-label={`Remove ${item.file.name}`}
      className="absolute top-2 right-2 rounded-full bg-black/60 p-1 text-white transition-colors hover:bg-black/80"
    >
      <X className="size-4" aria-hidden="true" />
    </button>
  );
}
