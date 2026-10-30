"use client";

import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { EmptyState, Pager } from "@/components/shared/pager";
import { Skeleton } from "@/components/ui/skeleton";
import { useBookmarks } from "../hooks";
import { PostCard } from "./post-card";

export function SavedPosts() {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useBookmarks(page);
  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Saved posts</h1>
        <p className="mt-1 text-muted-foreground">Bookmarks are private to you.</p>
      </div>
      {isPending ? <Skeleton className="h-40 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <EmptyState title="No saved posts" text="Use the bookmark icon on a post to keep it here." />
      ) : (
        <div className="space-y-4">
          {data.content.map((post) => <PostCard key={post.id} post={post} />)}
          <Pager page={data.page} onChange={setPage} />
        </div>
      )}
    </div>
  );
}
