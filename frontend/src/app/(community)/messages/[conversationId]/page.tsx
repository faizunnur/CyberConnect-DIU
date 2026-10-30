import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { Messages } from "@/features/messaging/components/messages";

export const metadata: Metadata = { title: "Conversation" };

export default async function ConversationPage({ params }: { params: Promise<{ conversationId: string }> }) {
  const { conversationId } = await params;
  return (
    <Suspense fallback={<Skeleton className="h-96 w-full" />}>
      <Messages conversationId={conversationId} />
    </Suspense>
  );
}
