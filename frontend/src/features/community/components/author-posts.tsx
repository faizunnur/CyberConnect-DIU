"use client";

import { useState } from "react";
import { FormError } from "@/components/forms/form-alert";
import { Pager } from "@/components/shared/pager";
import { Skeleton } from "@/components/ui/skeleton";
import { useFeed } from "../hooks";
import { PostCard } from "./post-card";

/** Posts by one member that the viewer may see (same visibility rules as the feed). */
export function AuthorPosts({ userId }: { userId: string }) {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error } = useFeed({ author: userId, page, size: 10 });
  return (
    <section aria-labelledby="author-posts-heading" className="space-y-4">
      <h2 id="author-posts-heading" className="text-lg font-semibold">Posts</h2>
      {isPending ? <Skeleton className="h-32 w-full" /> : isError ? <FormError error={error} /> : data.content.length === 0 ? (
        <p className="text-sm text-muted-foreground">No posts to show.</p>
      ) : (
        <div className="space-y-4">
          {data.content.map((post) => <PostCard key={post.id} post={post} />)}
          <Pager page={data.page} onChange={setPage} />
        </div>
      )}
    </section>
  );
}
