import type { Metadata } from "next";
import { ResearchEditor } from "@/features/research/components/research-form";

export const metadata: Metadata = { title: "Propose a project" };

export default function NewResearchPage() {
  return <ResearchEditor />;
}
