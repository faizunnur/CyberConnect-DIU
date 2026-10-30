import type { Metadata } from "next";
import { ResearchEditor } from "@/features/research/components/research-form";

export const metadata: Metadata = { title: "Edit project" };

export default async function EditResearchPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ResearchEditor id={id} />;
}
