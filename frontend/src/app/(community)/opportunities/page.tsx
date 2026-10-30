import type { Metadata } from "next";
import { OpportunityList } from "@/features/opportunities/components/opportunity-list";

export const metadata: Metadata = { title: "Opportunities" };

export default function OpportunitiesPage() {
  return <OpportunityList />;
}
