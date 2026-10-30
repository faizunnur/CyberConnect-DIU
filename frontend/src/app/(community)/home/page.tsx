import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { Feed } from "@/features/community/components/feed";

export const metadata: Metadata = { title: "Home" };

export default function HomePage() {
  return (
    <Suspense fallback={<Skeleton className="h-96 w-full" />}>
      <Feed />
    </Suspense>
  );
}
