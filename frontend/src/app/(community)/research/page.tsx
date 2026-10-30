import type { Metadata } from "next";
import { ResearchList } from "@/features/research/components/research-list";

export const metadata: Metadata = { title: "Research hub" };

export default function ResearchPage() {
  return <ResearchList />;
}
