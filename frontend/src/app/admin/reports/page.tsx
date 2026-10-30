import type { Metadata } from "next";
import { ReportQueue } from "@/features/moderation/components/reports";

export const metadata: Metadata = { title: "Reports" };

export default function ReportsPage() {
  return <ReportQueue />;
}
