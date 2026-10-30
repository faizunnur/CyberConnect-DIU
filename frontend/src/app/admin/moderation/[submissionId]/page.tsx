import type { Metadata } from "next";
import { SubmissionReview } from "@/features/moderation/components/submission-review";

export const metadata: Metadata = { title: "Review submission" };

export default async function SubmissionReviewPage({ params }: { params: Promise<{ submissionId: string }> }) {
  const { submissionId } = await params;
  return <SubmissionReview submissionId={submissionId} />;
}
