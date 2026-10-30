import type { Metadata } from "next";
import { ReportDetail } from "@/features/moderation/components/reports";

export const metadata: Metadata = { title: "Report" };

export default async function ReportPage({ params }: { params: Promise<{ reportId: string }> }) {
  const { reportId } = await params;
  return <ReportDetail reportId={reportId} />;
}
