"use client";

import { Search, X } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { useFeed } from "../hooks";
import { INTENTS, INTENT_LABELS, type PostIntent } from "../types";
import { PostCard } from "./post-card";
import { PostComposer } from "./post-composer";

export function Feed() {
  const params = useSearchParams();
  const tag = params.get("tag") ?? undefined;
  const [intent, setIntent] = useState<PostIntent | undefined>(undefined);
  const [draftQuery, setDraftQuery] = useState("");
  const [q, setQ] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useFeed({ intent, tag, q, page, size: 20 });

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Community feed</h1>
        <p className="mt-1 text-muted-foreground">Technical discussion, achievements, events and opportunities from verified members.</p>
      </div>
      <PostComposer />

      <div className="space-y-3">
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setQ(draftQuery.trim() || undefined);
            setPage(0);
          }}
        >
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute top-2.5 left-2.5 size-4 text-muted-foreground" aria-hidden="true" />
            <Input aria-label="Search posts" placeholder="Search posts" className="pl-8" value={draftQuery} onChange={(e) => setDraftQuery(e.target.value)} />
          </div>
          <Button type="submit" variant="outline">Search</Button>
        </form>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter by intent">
          <FilterChip active={!intent} onClick={() => { setIntent(undefined); setPage(0); }}>All</FilterChip>
          {INTENTS.map((i) => (
            <FilterChip key={i} active={intent === i} onClick={() => { setIntent(intent === i ? undefined : i); setPage(0); }}>
              {INTENT_LABELS[i]}
            </FilterChip>
          ))}
        </div>
        {tag || q ? (
          <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            {tag ? (
              <Link href="/home" className="inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 hover:bg-accent">
                #{tag} <X className="size-3" aria-hidden="true" />
              </Link>
            ) : null}
            {q ? (
              <button type="button" className="inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 hover:bg-accent" onClick={() => { setQ(undefined); setDraftQuery(""); setPage(0); }}>
                “{q}” <X className="size-3" aria-hidden="true" />
              </button>
            ) : null}
          </div>
        ) : null}
      </div>

      {isPending ? (
        <div className="space-y-4">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-40 w-full" />)}</div>
      ) : isError ? (
        <FormError error={error} title="Could not load the feed" />
      ) : data.content.length === 0 ? (
        <EmptyState title="Nothing here yet" text={tag || q || intent ? "Try a different filter." : "Be the first to post."} />
      ) : (
        <div className="space-y-4">
          {data.content.map((post) => <PostCard key={post.id} post={post} />)}
          <Pager page={data.page} onChange={setPage} />
        </div>
      )}
    </div>
  );
}

function FilterChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
        active ? "border-primary bg-primary text-primary-foreground" : "hover:bg-accent",
      )}
    >
      {children}
    </button>
  );
}
