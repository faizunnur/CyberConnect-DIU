import type { Metadata } from "next";
import { SecuritySettings } from "@/features/auth/components/security-settings";

export const metadata: Metadata = { title: "Security settings" };

export default function SecuritySettingsPage() {
  return <SecuritySettings />;
}
