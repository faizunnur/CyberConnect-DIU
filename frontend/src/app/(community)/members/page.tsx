import type { Metadata } from "next";
import { Directory } from "@/features/profile/components/directory";

export const metadata: Metadata = { title: "Members" };

export default function MembersPage() {
  return <Directory />;
}
