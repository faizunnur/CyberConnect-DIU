import type { Metadata } from "next";
import { MemberDetail } from "@/features/admin/components/members";

export const metadata: Metadata = { title: "Member" };

export default async function MemberPage({ params }: { params: Promise<{ userId: string }> }) {
  const { userId } = await params;
  return <MemberDetail userId={userId} />;
}
