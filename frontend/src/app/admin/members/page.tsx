import type { Metadata } from "next";
import { MemberList } from "@/features/admin/components/members";

export const metadata: Metadata = { title: "Members" };

export default function MembersPage() {
  return <MemberList />;
}
