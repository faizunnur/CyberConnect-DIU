import type { Metadata } from "next";
import { OpportunityDetail } from "@/features/opportunities/components/opportunity-detail";

export const metadata: Metadata = { title: "Opportunity" };

export default async function OpportunityPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <OpportunityDetail id={id} />;
}
