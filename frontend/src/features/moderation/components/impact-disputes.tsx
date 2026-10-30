"use client";

import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useImpactDisputes, useResolveImpact } from "@/features/impact/api";
import { ImpactRecordCard } from "@/features/impact/components/impact-page";

export function ImpactDisputes() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useImpactDisputes(page);
  const resolve = useResolveImpact();
  const [notes, setNotes] = useState<Record<string, string>>({});
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Impact disputes</h1>
        <p className="mt-1 text-sm text-muted-foreground">One member proposed an outcome and the other disputed it. Decide from the trail and the dispute reason; the decision is audited.</p>
      </div>
      <FormError error={resolve.error} />
      {isPending ? <Skeleton className="h-64 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title="No open disputes" />
      ) : (
        <>
          <div className="space-y-4">
            {data.content.map((r) => (
              <ImpactRecordCard key={r.id} r={r} moderator>
                <div className="space-y-2 rounded-lg border p-3">
                  <Textarea aria-label="Resolution note" rows={2} maxLength={2000} placeholder="Note for both members and the audit trail" value={notes[r.id] ?? ""} onChange={(e) => setNotes({ ...notes, [r.id]: e.target.value })} />
                  <div className="flex gap-2">
                    <Button size="sm" disabled={resolve.isPending} onClick={() => resolve.mutate({ id: r.id, confirmed: true, note: notes[r.id] || undefined })}>Confirm record</Button>
                    <Button size="sm" variant="outline" disabled={resolve.isPending} onClick={() => resolve.mutate({ id: r.id, confirmed: false, note: notes[r.id] || undefined })}>Reject record</Button>
                  </div>
                </div>
              </ImpactRecordCard>
            ))}
          </div>
          <Pager page={data.page} onChange={setPage} />
        </>
      )}
    </div>
  );
}
