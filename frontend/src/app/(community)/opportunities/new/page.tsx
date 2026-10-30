import type { Metadata } from "next";
import { OpportunityEditor } from "@/features/opportunities/components/opportunity-form";

export const metadata: Metadata = { title: "Post an opportunity" };

export default function NewOpportunityPage() {
  return <OpportunityEditor />;
}
