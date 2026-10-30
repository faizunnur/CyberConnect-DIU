import type { Metadata } from "next";
import { AppealQueue } from "@/features/moderation/components/appeals";

export const metadata: Metadata = { title: "Appeals" };

export default function AppealsPage() {
  return <AppealQueue />;
}
