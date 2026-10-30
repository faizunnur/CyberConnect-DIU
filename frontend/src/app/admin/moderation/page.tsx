import type { Metadata } from "next";
import { VerificationQueue } from "@/features/moderation/components/verification-queue";

export const metadata: Metadata = { title: "Verification queue" };

export default function ModerationQueuePage() {
  return <VerificationQueue />;
}
