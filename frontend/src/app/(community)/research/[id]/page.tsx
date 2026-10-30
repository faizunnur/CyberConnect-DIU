import type { Metadata } from "next";
import { ResearchDetail } from "@/features/research/components/research-detail";

export const metadata: Metadata = { title: "Research project" };

export default async function ResearchProjectPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ResearchDetail id={id} />;
}
