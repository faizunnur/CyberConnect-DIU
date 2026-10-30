import type { Metadata } from "next";
import { SettingsEditor } from "@/features/admin/components/settings-audit";

export const metadata: Metadata = { title: "Platform settings" };

export default function SettingsPage() {
  return <SettingsEditor />;
}
