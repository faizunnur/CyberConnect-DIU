"use client";

import { Ban, Check, Loader2, UserMinus, UserPlus, X } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { RelationshipView } from "@/features/profile/types";
import { cn } from "@/lib/utils";
import { useBlockMember, useRemoveConnection, useRespondToRequest, useSendRequest } from "../hooks";

/** Connection and block controls for a member page, driven entirely by the server-provided relationship. */
export function ConnectButton({ userId, relationship }: { userId: string; relationship: RelationshipView }) {
  const [composing, setComposing] = useState(false);
  const [message, setMessage] = useState("");
  const [confirming, setConfirming] = useState<"remove" | "block" | null>(null);
  const send = useSendRequest();
  const accept = useRespondToRequest("accept");
  const decline = useRespondToRequest("decline");
  const cancel = useRespondToRequest("cancel");
  const remove = useRemoveConnection();
  const block = useBlockMember();
  const error = send.error ?? accept.error ?? decline.error ?? cancel.error ?? remove.error ?? block.error;
  const busy = send.isPending || accept.isPending || decline.isPending || cancel.isPending || remove.isPending || block.isPending;

  if (relationship.self) {
    return (
      <Link href="/settings/profile" className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
        Edit profile
      </Link>
    );
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2">
        {relationship.connected ? (
          confirming === "remove" ? (
            <>
              <Button size="sm" variant="destructive" disabled={busy} onClick={() => remove.mutate(userId, { onSettled: () => setConfirming(null) })}>
                Remove connection
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setConfirming(null)}>Keep</Button>
            </>
          ) : (
            <Button size="sm" variant="outline" onClick={() => setConfirming("remove")}>
              <UserMinus aria-hidden="true" /> Connected
            </Button>
          )
        ) : relationship.pendingIncoming && relationship.requestId ? (
          <>
            <Button size="sm" disabled={busy} onClick={() => accept.mutate(relationship.requestId!)}>
              <Check aria-hidden="true" /> Accept request
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => decline.mutate(relationship.requestId!)}>
              <X aria-hidden="true" /> Decline
            </Button>
          </>
        ) : relationship.pendingOutgoing && relationship.requestId ? (
          <Button size="sm" variant="outline" disabled={busy} onClick={() => cancel.mutate(relationship.requestId!)}>
            Request sent · cancel
          </Button>
        ) : composing ? null : (
          <Button size="sm" onClick={() => setComposing(true)}>
            <UserPlus aria-hidden="true" /> Connect
          </Button>
        )}
        {confirming === "block" ? (
          <>
            <Button size="sm" variant="destructive" disabled={busy} onClick={() => block.mutate(userId)}>
              Confirm block
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setConfirming(null)}>Cancel</Button>
          </>
        ) : (
          <Button size="sm" variant="ghost" className="text-muted-foreground" onClick={() => setConfirming("block")}>
            <Ban aria-hidden="true" /> Block
          </Button>
        )}
      </div>
      {composing ? (
        <form
          className="space-y-2 rounded-lg border p-3"
          onSubmit={(e) => {
            e.preventDefault();
            send.mutate({ userId, message: message || undefined }, { onSuccess: () => { setComposing(false); setMessage(""); } });
          }}
        >
          <label htmlFor="connect-message" className="text-sm font-medium">Add a note (optional)</label>
          <Textarea id="connect-message" maxLength={500} rows={3} value={message} onChange={(e) => setMessage(e.target.value)} placeholder="How do you know each other, or why you would like to connect." />
          <div className="flex gap-2">
            <Button type="submit" size="sm" disabled={busy}>
              {send.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Send request
            </Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setComposing(false)}>Cancel</Button>
          </div>
        </form>
      ) : null}
      <FormError error={error} />
    </div>
  );
}
