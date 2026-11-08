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
    <div className="space-y-4">
      <h1 className="sr-only">Community feed</h1>
      <PostComposer />

      <div className="space-y-2">
        <div className="flex items-center gap-3">
          <span className="h-px flex-1 bg-border" aria-hidden="true" />
          <form
            className="flex w-full max-w-sm items-center"
            role="search"
            onSubmit={(e) => {
              e.preventDefault();
              setQ(draftQuery.trim() || undefined);
              setPage(0);
            }}
          >
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
              <Input aria-label="Search posts" placeholder="Search posts" className="h-9 rounded-full bg-card pl-9" value={draftQuery} onChange={(e) => setDraftQuery(e.target.value)} />
            </div>
            <Button type="submit" variant="ghost" size="sm" className="sr-only">Search</Button>
          </form>
        </div>
        <div className="-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-1 [scrollbar-width:none]" role="group" aria-label="Filter by post type">
          <FilterChip active={!intent} onClick={() => { setIntent(undefined); setPage(0); }}>All posts</FilterChip>
          {INTENTS.map((i) => (
            <FilterChip key={i} active={intent === i} onClick={() => { setIntent(intent === i ? undefined : i); setPage(0); }}>
              {INTENT_LABELS[i]}
            </FilterChip>
          ))}
        </div>
        {tag || q ? (
          <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span>Showing</span>
            {tag ? (
              <Link href="/home" className="inline-flex items-center gap-1 rounded-full border bg-card px-2.5 py-0.5 font-medium text-primary hover:bg-accent">
                #{tag} <X className="size-3" aria-label="Clear hashtag filter" />
              </Link>
            ) : null}
            {q ? (
              <button type="button" className="inline-flex items-center gap-1 rounded-full border bg-card px-2.5 py-0.5 font-medium hover:bg-accent" onClick={() => { setQ(undefined); setDraftQuery(""); setPage(0); }}>
                “{q}” <X className="size-3" aria-label="Clear search" />
              </button>
            ) : null}
          </div>
        ) : null}
      </div>

      {isPending ? (
        <div className="space-y-4" aria-busy="true" aria-label="Loading posts">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="space-y-3 rounded-xl border bg-card p-4">
              <div className="flex items-center gap-3">
                <Skeleton className="size-10 rounded-full" />
                <div className="flex-1 space-y-1.5"><Skeleton className="h-3.5 w-40" /><Skeleton className="h-3 w-24" /></div>
              </div>
              <Skeleton className="h-3.5 w-full" /><Skeleton className="h-3.5 w-4/5" /><Skeleton className="h-48 w-full" />
            </div>
          ))}
        </div>
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
        "shrink-0 rounded-full border px-3 py-1.5 text-xs font-medium whitespace-nowrap transition-colors",
        active ? "border-primary bg-primary text-primary-foreground" : "bg-card hover:bg-accent",
      )}
    >
      {children}
    </button>
  );
}
