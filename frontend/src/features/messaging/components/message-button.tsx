"use client";

import { Loader2, MessageSquare } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { useStartConversation } from "../api";

/** Opens (or continues) the direct conversation with a member. The server decides whether it becomes a request. */
export function MessageButton({ userId, name }: { userId: string; name: string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [body, setBody] = useState("");
  const start = useStartConversation();
  return (
    <>
      <Button size="sm" variant="outline" onClick={() => { start.reset(); setOpen(true); }}>
        <MessageSquare aria-hidden="true" /> Message
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Message {name}</DialogTitle>
            <DialogDescription>If you are not connected, this arrives as a message request they can accept or decline.</DialogDescription>
          </DialogHeader>
          <form
            id="message-form"
            onSubmit={(e) => {
              e.preventDefault();
              start.mutate({ userId, body }, { onSuccess: (conversation) => { setOpen(false); router.push(`/messages/${conversation.id}`); } });
            }}
          >
            <Textarea aria-label="Message" rows={4} maxLength={4000} value={body} onChange={(e) => setBody(e.target.value)} required />
            <FormError error={start.error} />
          </form>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" form="message-form" disabled={start.isPending || !body.trim()}>
              {start.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Send
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
