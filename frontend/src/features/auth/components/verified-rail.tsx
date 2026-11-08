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
  const name = profile?.displayName ?? me.fullName;
  return (
    <>
      <Card className="gap-0 overflow-hidden py-0">
        <div className="h-16 bg-gradient-to-r from-navy via-primary/80 to-primary" aria-hidden="true" />
        <div className="-mt-9 flex flex-col items-center px-4 pb-4 text-center">
          <Link href={`/members/${me.id}`} className="rounded-full ring-4 ring-card" aria-label="View your profile">
            <MemberAvatar name={name} photoUrl={profile?.photoUrl ?? null} size="lg" className="size-16 text-lg" />
          </Link>
          <Link href={`/members/${me.id}`} className="mt-2 flex items-center gap-1 font-semibold hover:underline">
            {name}
            <BadgeCheck className="size-4 shrink-0 text-primary" aria-label="Verified member" />
          </Link>
          {profile?.headline ? <p className="mt-0.5 line-clamp-2 text-xs text-muted-foreground">{profile.headline}</p> : null}
          <p className="mt-1 text-xs text-muted-foreground">
            {me.affiliations.map((a) => AFFILIATION_LABELS[a.type]).join(" · ")}
          </p>
        </div>
        <div className="border-t text-sm">
          <Link href="/connections" className="flex items-center justify-between px-4 py-2.5 transition-colors hover:bg-accent">
            <span className="text-muted-foreground">Connections</span>
            <span className="font-semibold text-primary">{network?.connections ?? 0}</span>
          </Link>
          {network && network.pendingIncoming > 0 ? (
            <Link href="/connections" className="flex items-center justify-between px-4 py-2.5 transition-colors hover:bg-accent">
              <span className="text-muted-foreground">Invitations</span>
              <span className="font-semibold text-primary">{network.pendingIncoming}</span>
            </Link>
          ) : (
            <Link href="/members" className="flex items-center gap-2 px-4 py-2.5 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground">
              <UserPlus className="size-4 text-primary" aria-hidden="true" /> Grow your network
            </Link>
          )}
        </div>
        <div className="flex gap-2 border-t p-3">
          <Link href={`/members/${me.id}`} className="flex-1 rounded-full border border-primary px-3 py-1.5 text-center text-sm font-medium text-primary transition-colors hover:bg-primary/10">
            View profile
          </Link>
          <Link href="/settings/profile" className="flex-1 rounded-full border px-3 py-1.5 text-center text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-foreground">
            Edit
          </Link>
        </div>
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
            <Link href="/settings/security" className="text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">
              Set up MFA
            </Link>
          </CardContent>
        </Card>
      ) : null}
    </>
  );
}
