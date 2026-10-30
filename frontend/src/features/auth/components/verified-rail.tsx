"use client";

import { BadgeCheck, ShieldAlert, UserPlus } from "lucide-react";
import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useNetworkSummary } from "@/features/networking/hooks";
import { MemberAvatar } from "@/features/profile/components/member-avatar";
import { useMyProfile } from "@/features/profile/hooks";
import { useMe } from "../hooks";
import { AFFILIATION_LABELS } from "../types";

export function VerifiedRail() {
  const { data: me } = useMe();
  const { data: profile } = useMyProfile();
  const { data: network } = useNetworkSummary();
  if (!me) return null;
  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <BadgeCheck className="size-4 text-success" aria-hidden="true" /> Verified member
          </CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          <div className="flex items-center gap-3">
            <MemberAvatar name={profile?.displayName ?? me.fullName} photoUrl={profile?.photoUrl ?? null} size="lg" />
            <div className="min-w-0">
              <p className="truncate font-medium text-foreground">{profile?.displayName ?? me.fullName}</p>
              {profile?.headline ? <p className="truncate text-xs">{profile.headline}</p> : null}
            </div>
          </div>
          <ul className="mt-2">
            {me.affiliations.map((a) => (
              <li key={a.id}>
                {AFFILIATION_LABELS[a.type]} · {a.status.toLowerCase()}
              </li>
            ))}
          </ul>
          <div className="mt-3 flex flex-wrap gap-x-3 gap-y-1">
            <Link href={`/members/${me.id}`} className="text-primary underline-offset-4 hover:underline">View profile</Link>
            <Link href="/settings/profile" className="text-primary underline-offset-4 hover:underline">Edit profile</Link>
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <UserPlus className="size-4 text-primary" aria-hidden="true" /> Network
          </CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          <p>
            <span className="font-medium text-foreground">{network?.connections ?? 0}</span> connection{network?.connections === 1 ? "" : "s"}
          </p>
          {network && network.pendingIncoming > 0 ? (
            <p>
              <Link href="/connections" className="text-primary underline-offset-4 hover:underline">
                {network.pendingIncoming} pending request{network.pendingIncoming === 1 ? "" : "s"}
              </Link>
            </p>
          ) : (
            <p>
              <Link href="/members" className="text-primary underline-offset-4 hover:underline">Find members</Link>
            </p>
          )}
        </CardContent>
      </Card>
      {!me.mfaEnabled ? (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <ShieldAlert className="size-4 text-warning" aria-hidden="true" /> Protect your account
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            Add an authenticator app for multi-factor authentication.{" "}
            <Link href="/settings/security" className="text-primary underline-offset-4 hover:underline">
              Set up MFA
            </Link>
          </CardContent>
        </Card>
      ) : null}
    </>
  );
}
