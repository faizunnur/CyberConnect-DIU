"use client";

import { Loader2, Repeat2 } from "lucide-react";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useCreatePost, useEditPost } from "../hooks";
import { INTENTS, INTENT_LABELS, VISIBILITY_LABELS, type PostIntent, type PostView, type PostVisibility } from "../types";

const MAX = 5000;
const selectClass = "h-9 rounded-lg border bg-background px-2 text-sm";

/** Creates a post, reposts one with commentary, or edits an existing post (when {@code editing} is set). */
export function PostComposer({ repostOf, editing, onDone, autoFocus }: {
  repostOf?: PostView;
  editing?: PostView;
  onDone?: () => void;
  autoFocus?: boolean;
}) {
  const [body, setBody] = useState(editing?.body ?? "");
  const [intent, setIntent] = useState<PostIntent>(editing?.intent ?? repostOf?.intent ?? "TECHNICAL_DISCUSSION");
  const [visibility, setVisibility] = useState<PostVisibility>(editing?.visibility ?? "ALL_MEMBERS");
  const create = useCreatePost();
  const edit = useEditPost();
  const busy = create.isPending || edit.isPending;
  const canSubmit = (body.trim().length > 0 || !!repostOf) && body.length <= MAX && !busy;

  const submit = async () => {
    if (editing) {
      await edit.mutateAsync({ id: editing.id, intent, body, visibility });
    } else {
      await create.mutateAsync({ intent, body, visibility, repostOfId: repostOf?.id });
      setBody("");
    }
    onDone?.();
  };

  return (
    <Card>
      <CardContent>
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          {repostOf ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Repeat2 className="size-4" aria-hidden="true" /> Reposting {repostOf.author?.displayName ?? "a member"}’s post. Add your commentary (optional).
            </p>
          ) : null}
          <Label htmlFor={editing ? `edit-${editing.id}` : "composer-body"} className="sr-only">Post text</Label>
          <Textarea
            id={editing ? `edit-${editing.id}` : "composer-body"}
            rows={editing || repostOf ? 3 : 4}
            autoFocus={autoFocus}
            placeholder={repostOf ? "Why is this worth sharing?" : "Share a technical insight, an achievement, an event or ask the community for help. Use #hashtags to make it findable."}
            value={body}
            maxLength={MAX + 500}
            onChange={(e) => setBody(e.target.value)}
          />
          <div className="flex flex-wrap items-center gap-2">
            <label className="flex items-center gap-2 text-sm">
              <span className="text-muted-foreground">Intent</span>
              <select className={selectClass} value={intent} onChange={(e) => setIntent(e.target.value as PostIntent)} aria-label="Post intent">
                {INTENTS.map((i) => <option key={i} value={i}>{INTENT_LABELS[i]}</option>)}
              </select>
            </label>
            <label className="flex items-center gap-2 text-sm">
              <span className="text-muted-foreground">Visible to</span>
              <select className={selectClass} value={visibility} onChange={(e) => setVisibility(e.target.value as PostVisibility)} aria-label="Post visibility">
                {(Object.keys(VISIBILITY_LABELS) as PostVisibility[]).map((v) => <option key={v} value={v}>{VISIBILITY_LABELS[v]}</option>)}
              </select>
            </label>
            <span className={`ml-auto text-xs ${body.length > MAX ? "text-destructive" : "text-muted-foreground"}`} aria-live="polite">
              {body.length}/{MAX}
            </span>
            {onDone ? <Button type="button" variant="ghost" size="sm" onClick={onDone}>Cancel</Button> : null}
            <Button type="submit" size="sm" disabled={!canSubmit}>
              {busy ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} {editing ? "Save" : repostOf ? "Repost" : "Post"}
            </Button>
          </div>
          <FormError error={create.error ?? edit.error} />
        </form>
      </CardContent>
    </Card>
  );
}
