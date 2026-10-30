import type { Metadata } from "next";
import { OpportunityEditor } from "@/features/opportunities/components/opportunity-form";

export const metadata: Metadata = { title: "Edit opportunity" };

export default async function EditOpportunityPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <OpportunityEditor id={id} />;
}
