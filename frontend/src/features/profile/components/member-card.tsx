"use client";

import { GraduationCap, MapPin } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { AFFILIATION_LABELS } from "@/features/auth/types";
import type { MemberCard as MemberCardData } from "../types";
import { MemberAvatar } from "./member-avatar";

export function MemberCardItem({ member, actions }: { member: MemberCardData; actions?: ReactNode }) {
  const subtitle = [member.organization, member.location].filter(Boolean).join(" · ");
  return (
    <Card data-testid="member-card">
      <CardContent className="flex gap-4">
        <MemberAvatar name={member.displayName} photoUrl={member.photoUrl} size="lg" className="size-14 text-lg" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="min-w-0">
              <Link href={`/members/${member.userId}`} className="font-semibold hover:underline">
                {member.displayName}
              </Link>
              {member.headline ? <p className="truncate text-sm text-muted-foreground">{member.headline}</p> : null}
            </div>
            {actions ? <div className="flex shrink-0 gap-2">{actions}</div> : null}
          </div>
          {subtitle ? (
            <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
              <MapPin className="size-3" aria-hidden="true" /> {subtitle}
            </p>
          ) : null}
          <div className="mt-2 flex flex-wrap gap-1.5">
            {member.affiliations.map((a) => (
              <Badge key={a} variant="secondary">
                {AFFILIATION_LABELS[a]}
              </Badge>
            ))}
            {member.mentorAvailable ? (
              <Badge variant="outline" className="border-success text-success">
                <GraduationCap aria-hidden="true" /> Mentor
              </Badge>
            ) : null}
            {member.connected ? <Badge variant="outline">Connected</Badge> : null}
          </div>
          {member.skills.length > 0 ? (
            <p className="mt-2 truncate text-xs text-muted-foreground">
              {member.skills.slice(0, 5).join(" · ")}
              {member.skills.length > 5 ? ` · +${member.skills.length - 5}` : ""}
            </p>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
