"use client";

import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { useStepUpFresh } from "@/features/auth/components/step-up-banner";
import { useMe } from "@/features/auth/hooks";
import { hasAuthority } from "@/features/auth/routing";
import { AFFILIATION_LABELS, STATUS_LABELS, type RoleName } from "@/features/auth/types";
import { useSuspend, useUnsuspend } from "@/features/moderation/hooks";
import { formatDateTime } from "@/lib/api/types";
import { useMember, useMemberAction, useMembers, useReplaceRoles } from "../hooks";

export function MemberList() {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [role, setRole] = useState("");
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useMembers({ q: q || undefined, status: status || undefined, role: role || undefined, page, size: 25 });

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Members</h1>
        <p className="mt-1 text-sm text-muted-foreground">Accounts, roles and lifecycle actions.</p>
      </div>
      <div className="flex flex-wrap gap-3 text-sm">
        <Input placeholder="Search name or email" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} className="w-64" />
        <select className="h-9 rounded-lg border bg-background px-2" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">Any status</option>
          {Object.entries(STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select className="h-9 rounded-lg border bg-background px-2" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}>
          <option value="">Any role</option>
          <option value="MODERATOR">Moderator</option>
          <option value="ADMIN">Admin</option>
          <option value="SUPER_ADMIN">Super admin</option>
        </select>
      </div>
      {isPending ? <Skeleton className="h-64 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title="No members match" />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Roles</TableHead>
                <TableHead>MFA</TableHead>
                <TableHead>Joined</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((u) => (
                <TableRow key={u.id}>
                  <TableCell><Link href={`/admin/members/${u.id}`} className="font-medium text-primary underline-offset-4 hover:underline">{u.fullName}</Link></TableCell>
                  <TableCell className="text-muted-foreground">{u.email}</TableCell>
                  <TableCell><Badge variant={u.accountStatus === "VERIFIED" ? "default" : "secondary"}>{STATUS_LABELS[u.accountStatus]}</Badge></TableCell>
                  <TableCell>{u.roles.filter((r) => r !== "MEMBER").join(", ") || "member"}</TableCell>
                  <TableCell>{u.mfaEnabled ? "on" : "off"}</TableCell>
                  <TableCell>{formatDateTime(u.createdAt)}</TableCell>
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

const ASSIGNABLE: RoleName[] = ["MODERATOR", "ADMIN", "SUPER_ADMIN"];

export function MemberDetail({ userId }: { userId: string }) {
  const { data, isPending, isError, error } = useMember(userId);
  const { data: me } = useMe();
  const fresh = useStepUpFresh();
  const replaceRoles = useReplaceRoles();
  const suspend = useSuspend();
  const unsuspend = useUnsuspend();
  const deactivate = useMemberAction("deactivate");
  const reactivate = useMemberAction("reactivate");
  const reopen = useMemberAction("reopenVerification");
  const [roles, setRoles] = useState<RoleName[] | null>(null);
  const [reason, setReason] = useState("");

  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) return <FormError error={error} title="Could not load the member" />;

  const user = data.user;
  const selected = roles ?? user.roles.filter((r) => r !== "MEMBER");
  const isSelf = me?.id === user.id;
  const canAssign = hasAuthority(me, "ROLE_ASSIGN");
  const canManage = hasAuthority(me, "MEMBER_MANAGE");
  const canModerate = hasAuthority(me, "MODERATION_ACTION");
  const canReopen = hasAuthority(me, "VERIFICATION_REOPEN");
  const anyError = replaceRoles.error ?? suspend.error ?? unsuspend.error ?? deactivate.error ?? reactivate.error ?? reopen.error;
  const busy = replaceRoles.isPending || suspend.isPending || unsuspend.isPending || deactivate.isPending || reactivate.isPending || reopen.isPending;

  return (
    <div className="space-y-6">
      <div>
        <Link href="/admin/members" className="text-sm text-muted-foreground hover:text-foreground">← Members</Link>
        <h1 className="text-2xl font-semibold">{user.fullName}</h1>
        <p className="text-sm text-muted-foreground">{user.email} · joined {formatDateTime(user.createdAt)} · last sign-in {formatDateTime(user.lastLoginAt)}</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Account</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <p>Status: <Badge variant={user.accountStatus === "VERIFIED" ? "default" : "secondary"}>{STATUS_LABELS[user.accountStatus]}</Badge></p>
            <p>Email verified: {user.emailVerified ? "yes" : "no"} · MFA: {user.mfaEnabled ? "enabled" : "not enabled"}</p>
            {user.verificationLocked ? <p className="text-destructive">Verification locked after a denied appeal.</p> : null}
            <p className="font-medium">Affiliations</p>
            <ul className="list-disc pl-5">
              {data.affiliations.map((a) => (
                <li key={a.id}>{AFFILIATION_LABELS[a.type]} · {a.status.toLowerCase()}{a.verifiedAt ? ` (${formatDateTime(a.verifiedAt)})` : ""}</li>
              ))}
            </ul>
          </CardContent>
        </Card>

        {canAssign ? (
          <Card>
            <CardHeader>
              <CardTitle>Roles</CardTitle>
              <CardDescription>MEMBER is implicit. Changing roles signs the member out everywhere.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {ASSIGNABLE.map((r) => (
                <label key={r} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 accent-primary"
                    checked={selected.includes(r)}
                    disabled={isSelf || !fresh}
                    onChange={(e) => setRoles(e.target.checked ? [...selected, r] : selected.filter((x) => x !== r))}
                  />
                  {r.replace("_", " ").toLowerCase()}
                </label>
              ))}
              {isSelf ? <p className="text-xs text-muted-foreground">You cannot change your own roles.</p> : null}
              <Button size="sm" disabled={isSelf || !fresh || busy || roles === null} onClick={() => replaceRoles.mutate({ id: user.id, roles: selected }, { onSuccess: () => setRoles(null) })}>
                {replaceRoles.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : null} Save roles
              </Button>
            </CardContent>
          </Card>
        ) : null}
      </div>

      {!isSelf && (canManage || canModerate || canReopen) ? (
        <Card>
          <CardHeader>
            <CardTitle>Lifecycle actions</CardTitle>
            <CardDescription>Every action requires a reason and is recorded in the audit trail.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <label className="text-sm font-medium" htmlFor="action-reason">Reason</label>
            <Textarea id="action-reason" value={reason} onChange={(e) => setReason(e.target.value)} rows={2} maxLength={1000} />
            <FormError error={anyError} title="Action failed" />
            <div className="flex flex-wrap gap-2">
              {canModerate && user.accountStatus === "VERIFIED" ? (
                <Button variant="destructive" size="sm" disabled={!fresh || !reason.trim() || busy} onClick={() => suspend.mutate({ userId: user.id, reason })}>Suspend</Button>
              ) : null}
              {canModerate && user.accountStatus === "SUSPENDED" ? (
                <Button size="sm" disabled={!fresh || !reason.trim() || busy} onClick={() => unsuspend.mutate({ userId: user.id, reason })}>Lift suspension</Button>
              ) : null}
              {canManage && user.accountStatus !== "DEACTIVATED" ? (
                <Button variant="outline" size="sm" disabled={!fresh || !reason.trim() || busy} onClick={() => deactivate.mutate({ id: user.id, reason })}>Deactivate</Button>
              ) : null}
              {canManage && user.accountStatus === "DEACTIVATED" ? (
                <Button size="sm" disabled={!fresh || !reason.trim() || busy} onClick={() => reactivate.mutate({ id: user.id, reason })}>Reactivate</Button>
              ) : null}
              {canReopen && user.verificationLocked ? (
                <Button variant="outline" size="sm" disabled={!fresh || !reason.trim() || busy} onClick={() => reopen.mutate({ id: user.id, reason })}>Reopen verification</Button>
              ) : null}
            </div>
            {!fresh ? <p className="text-xs text-muted-foreground">Confirm your identity above to enable actions.</p> : null}
          </CardContent>
        </Card>
      ) : null}

      {data.moderationHistory.length ? (
        <Card>
          <CardHeader>
            <CardTitle>Moderation history</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="divide-y text-sm">
              {data.moderationHistory.map((h) => (
                <li key={h.id} className="py-2">
                  <span className="font-medium">{h.action.replace(/_/g, " ").toLowerCase()}</span> · {formatDateTime(h.createdAt)}
                  {h.expiresAt ? ` · until ${formatDateTime(h.expiresAt)}` : ""}
                  <p className="text-muted-foreground">{h.reason}</p>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
