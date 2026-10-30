import type { Metadata } from "next";
import { ImpactPage } from "@/features/impact/components/impact-page";

export const metadata: Metadata = { title: "Impact" };

export default function Impact() {
  return <ImpactPage />;
}
