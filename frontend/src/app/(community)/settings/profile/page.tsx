import type { Metadata } from "next";
import { ProfileEditor } from "@/features/profile/components/profile-editor";

export const metadata: Metadata = { title: "Edit profile" };

export default function ProfileSettingsPage() {
  return <ProfileEditor />;
}
