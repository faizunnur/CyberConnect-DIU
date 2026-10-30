import type { Metadata } from "next";
import { PrivacySettingsForm } from "@/features/profile/components/privacy-settings";

export const metadata: Metadata = { title: "Privacy settings" };

export default function PrivacySettingsPage() {
  return <PrivacySettingsForm />;
}
