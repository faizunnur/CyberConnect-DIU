"use client";

import { Hash } from "lucide-react";
import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useTrending } from "../hooks";

export function TrendingRail() {
  const { data } = useTrending();
  if (!data || data.length === 0) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Hash className="size-4 text-primary" aria-hidden="true" /> Trending this week
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="space-y-1 text-sm">
          {data.map((t) => (
            <li key={t.tag} className="flex items-center justify-between gap-2">
              <Link href={`/home?tag=${encodeURIComponent(t.tag)}`} className="truncate text-primary underline underline-offset-4 decoration-primary/40 hover:decoration-primary">#{t.tag}</Link>
              <span className="text-xs text-muted-foreground">{t.posts}</span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
