"use client";

import { Award } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDate } from "@/lib/api/types";
import { IMPACT_TYPE_LABELS, useMemberImpact } from "../api";

/** Confirmed impact on a member profile (CONFIRMED records only, computed server-side). */
export function MemberImpactCard({ userId }: { userId: string }) {
  const { data } = useMemberImpact(userId);
  if (!data || (data.asContributor === 0 && data.asBeneficiary === 0)) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Award className="size-4 text-primary" aria-hidden="true" /> Confirmed impact
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <div className="flex flex-wrap gap-1.5">
          <Badge variant="secondary">{data.asContributor} helped others</Badge>
          <Badge variant="outline">{data.asBeneficiary} received help</Badge>
          {(Object.keys(data.contributedByType) as (keyof typeof data.contributedByType)[]).filter((t) => data.contributedByType[t] > 0).map((t) => (
            <Badge key={t} variant="outline">{data.contributedByType[t]} × {IMPACT_TYPE_LABELS[t]}</Badge>
          ))}
        </div>
        <ul className="space-y-1 text-muted-foreground">
          {data.recent.slice(0, 5).map((r) => (
            <li key={r.id}>
              <span className="text-foreground">{r.title}</span> · {IMPACT_TYPE_LABELS[r.type]} · {formatDate(r.confirmedAt)}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
