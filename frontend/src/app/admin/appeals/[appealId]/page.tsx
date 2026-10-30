import type { Metadata } from "next";
import { AppealReview } from "@/features/moderation/components/appeals";

export const metadata: Metadata = { title: "Review appeal" };

export default async function AppealReviewPage({ params }: { params: Promise<{ appealId: string }> }) {
  const { appealId } = await params;
  return <AppealReview appealId={appealId} />;
}
