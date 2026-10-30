"use client";

import { BadgeCheck, ShieldAlert } from "lucide-react";
import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useMe } from "../hooks";
import { AFFILIATION_LABELS } from "../types";

export function VerifiedRail() {
  const { data: me } = useMe();
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
          <p className="font-medium text-foreground">{me.fullName}</p>
          <ul className="mt-1">
            {me.affiliations.map((a) => (
              <li key={a.id}>
                {AFFILIATION_LABELS[a.type]} · {a.status.toLowerCase()}
              </li>
            ))}
          </ul>
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
