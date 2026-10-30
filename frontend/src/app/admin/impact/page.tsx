import type { Metadata } from "next";
import { ImpactDisputes } from "@/features/moderation/components/impact-disputes";

export const metadata: Metadata = { title: "Impact disputes" };

export default function ImpactDisputesPage() {
  return <ImpactDisputes />;
}
