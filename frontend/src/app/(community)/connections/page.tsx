import type { Metadata } from "next";
import { Connections } from "@/features/networking/components/connections";

export const metadata: Metadata = { title: "Connections" };

export default function ConnectionsPage() {
  return <Connections />;
}
