import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { Messages } from "@/features/messaging/components/messages";

export const metadata: Metadata = { title: "Messages" };

export default function MessagesPage() {
  return (
    <Suspense fallback={<Skeleton className="h-96 w-full" />}>
      <Messages />
    </Suspense>
  );
}
