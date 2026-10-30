import type { Metadata } from "next";
import { AuditLog } from "@/features/admin/components/settings-audit";

export const metadata: Metadata = { title: "Audit history" };

export default function AuditPage() {
  return <AuditLog />;
}
