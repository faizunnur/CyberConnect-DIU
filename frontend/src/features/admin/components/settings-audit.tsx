"use client";

import { Loader2 } from "lucide-react";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useStepUpFresh } from "@/features/auth/components/step-up-banner";
import { formatDateTime } from "@/lib/api/types";
import { useAudit, useSettings, useUpdateSetting } from "../hooks";

export function SettingsEditor() {
  const { data, isPending, isError, error } = useSettings();
  const update = useUpdateSetting();
  const fresh = useStepUpFresh();
  const [drafts, setDrafts] = useState<Record<string, string>>({});

  if (isPending) return <Skeleton className="h-64 w-full" />;
  if (isError) return <FormError error={error} />;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Platform settings</h1>
        <p className="mt-1 text-sm text-muted-foreground">Values are JSON. Changes are audited and apply immediately.</p>
      </div>
      <FormError error={update.error} title="Could not save" />
      <ul className="divide-y rounded-lg border">
        {data.map((s) => {
          const draft = drafts[s.key] ?? s.value;
          const changed = draft !== s.value;
          return (
            <li key={s.key} className="grid gap-2 p-4 md:grid-cols-[1fr_260px_auto] md:items-center">
              <div>
                <p className="font-mono text-sm font-medium">{s.key}</p>
                <p className="text-xs text-muted-foreground">{s.description} · updated {formatDateTime(s.updatedAt)}</p>
              </div>
              <Input value={draft} onChange={(e) => setDrafts({ ...drafts, [s.key]: e.target.value })} className="font-mono" aria-label={`Value for ${s.key}`} />
              <Button size="sm" disabled={!changed || !fresh || update.isPending} onClick={() => update.mutate({ key: s.key, value: draft }, { onSuccess: () => setDrafts((d) => { const copy = { ...d }; delete copy[s.key]; return copy; }) })}>
                {update.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Save
              </Button>
            </li>
          );
        })}
      </ul>
      {!fresh ? <p className="text-xs text-muted-foreground">Confirm your identity above to change settings.</p> : null}
    </div>
  );
}

export function AuditLog() {
  const [filters, setFilters] = useState({ targetType: "", targetId: "", actorId: "", action: "" });
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useAudit({
    targetType: filters.targetType || undefined,
    targetId: filters.targetId || undefined,
    actorId: filters.actorId || undefined,
    action: filters.action || undefined,
    page,
    size: 50,
  });
  const set = (k: keyof typeof filters) => (e: React.ChangeEvent<HTMLInputElement>) => { setFilters({ ...filters, [k]: e.target.value.trim() }); setPage(0); };

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Audit history</h1>
        <p className="mt-1 text-sm text-muted-foreground">Append-only record of security-relevant actions. Newest first.</p>
      </div>
      <div className="grid gap-2 sm:grid-cols-4">
        <Input placeholder="Target type (e.g. USER)" value={filters.targetType} onChange={set("targetType")} />
        <Input placeholder="Target id (UUID)" value={filters.targetId} onChange={set("targetId")} />
        <Input placeholder="Actor id (UUID)" value={filters.actorId} onChange={set("actorId")} />
        <Input placeholder="Action (e.g. LOGIN_FAILED)" value={filters.action} onChange={set("action")} />
      </div>
      {isPending ? <Skeleton className="h-64 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title="No audit entries match" />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Time</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Actor</TableHead>
                <TableHead>Target</TableHead>
                <TableHead>Outcome</TableHead>
                <TableHead>Details</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="whitespace-nowrap">{formatDateTime(r.createdAt)}</TableCell>
                  <TableCell className="font-mono text-xs">{r.action}</TableCell>
                  <TableCell className="font-mono text-xs">{r.actorId ? r.actorId.slice(0, 8) : "system"}{r.actorRoles ? ` (${r.actorRoles})` : ""}</TableCell>
                  <TableCell className="font-mono text-xs">{r.targetType ?? ""} {r.targetId ? r.targetId.slice(0, 8) : ""}</TableCell>
                  <TableCell>{r.outcome.toLowerCase()}</TableCell>
                  <TableCell className="max-w-xs truncate font-mono text-xs" title={r.metadata ?? ""}>{r.metadata}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pager page={data.page} onChange={setPage} />
        </>
      )}
    </div>
  );
}
